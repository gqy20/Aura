package com.xiaoqi.companion.core.tools

import ai.koog.agents.core.environment.ReceivedToolResult

internal data class CompositeTaskStep(
    val label: String,
    val acceptedToolNames: Set<String>,
    val instruction: String,
    val requiredEvidenceCount: Int = 1,
)

internal class CompositeTaskExecution(
    val goal: String,
    private val steps: List<CompositeTaskStep>,
) {
    private var nextStepIndex = 0
    private val currentStepEvidence = linkedSetOf<String>()

    val isComplete: Boolean
        get() = nextStepIndex >= steps.size

    val completedStepCount: Int
        get() = nextStepIndex

    val totalStepCount: Int
        get() = steps.size

    val requiredToolNameHints: Set<String>
        get() = steps
            .flatMap { it.acceptedToolNames }
            .filterNot { it.endsWith("_") }
            .toSet()

    val minimumToolRounds: Int
        get() = steps.sumOf { it.requiredEvidenceCount }

    fun acceptsNextTool(toolName: String): Boolean {
        if (isComplete) return false
        return steps[nextStepIndex].acceptedToolNames.any { accepted ->
            toolName.contains(accepted, ignoreCase = true)
        }
    }

    fun record(results: List<ReceivedToolResult>) {
        if (isComplete) return
        val current = steps[nextStepIndex]
        results.filter { result ->
            !result.isErrorResult() &&
                result.content.isNotBlank() &&
                current.acceptedToolNames.any { accepted ->
                    result.tool.contains(accepted, ignoreCase = true)
                }
        }.forEach { result ->
            currentStepEvidence += "${result.tool}:${result.content}"
        }
        if (currentStepEvidence.size >= current.requiredEvidenceCount) {
            nextStepIndex += 1
            currentStepEvidence.clear()
        }
    }

    fun initialInstruction(): String = buildString {
        appendLine("## Required compound-task workflow")
        appendLine("Goal: $goal")
        appendLine("Complete every step below in order using successful tool results:")
        steps.forEachIndexed { index, step -> appendLine("${index + 1}. ${step.label}") }
        appendLine("Do not claim a step is complete without a successful matching tool result.")
        appendLine("Do not invent stores, routes, offers, schedules, webpages, or recipes.")
        appendLine("Independent read-only lookups may be requested together in one response.")
        append("Do not give the final answer until all required steps are complete.")
    }

    fun nextStepInstruction(): String {
        if (isComplete) {
            return "All required compound-task steps now have tool evidence. Answer naturally using only those results."
        }
        val step = steps[nextStepIndex]
        val completedToolNames = steps.take(nextStepIndex)
            .flatMap { it.acceptedToolNames }
            .distinct()
        return buildString {
            appendLine("Compound task progress: $completedStepCount/$totalStepCount required steps complete.")
            appendLine("The next required step is: ${step.label}.")
            appendLine(step.instruction)
            if (completedToolNames.isNotEmpty()) {
                appendLine("Do not call completed-step tools again: ${completedToolNames.joinToString()}.")
            }
            appendLine("Call exactly one of these tools next: ${step.acceptedToolNames.joinToString()}.")
            append("Do not provide the final answer yet. Return the required tool call.")
        }
    }

    fun incompleteFallback(): String {
        if (isComplete) return ""
        val missing = steps.drop(nextStepIndex).joinToString("、") { it.label }
        return "我已经完成了部分查询，但以下步骤还没有取得可靠的工具结果：$missing。为了避免编造信息，我先停在这里，你可以重试未完成的步骤。"
    }
}

internal object CompositeTaskPlanner {
    fun create(query: String): CompositeTaskExecution? {
        val normalized = query.lowercase()
        if (normalized.isBlank()) return null

        val asksRoute = normalized.containsAny(
            "route", "direction", "navigation", "walk", "walking", "路线", "导航", "步行", "怎么去",
        )
        val asksNearby = normalized.containsAny(
            "nearby", "around", "coffee", "cafe", "store", "附近", "周边", "咖啡", "门店",
        )
        if (asksRoute && asksNearby) {
            val isMcDonalds = normalized.containsAny("mcdonald", "麦当劳")
            val routeToolNames = when {
                normalized.containsAny("walk", "walking", "步行", "走路") -> setOf("maps_direction_walking")
                normalized.containsAny("drive", "driving", "开车", "驾车", "自驾") -> setOf("maps_direction_driving")
                normalized.containsAny("transit", "bus", "subway", "公交", "地铁") ->
                    setOf("maps_direction_transit_integrated")
                else -> setOf("maps_direction_")
            }
            val nearbyStep = if (isMcDonalds) {
                CompositeTaskStep(
                    label = "查询真实麦当劳门店",
                    acceptedToolNames = setOf("query-nearby-stores"),
                    instruction = "Call query-nearby-stores and use a returned store with a real address and location as the route destination.",
                )
            } else {
                CompositeTaskStep(
                    label = "查询目标区域附近的真实地点",
                    acceptedToolNames = setOf("maps_around_search"),
                    instruction = "Call maps_around_search. Geocode the origin and search area first when coordinates are needed. Select one returned POI as the destination.",
                )
            }
            return CompositeTaskExecution(
                goal = "完成附近地点查询并规划到真实地点的路线",
                steps = buildList {
                    if (normalized.containsAny(" from ", "从") && !isMcDonalds) {
                        add(
                            CompositeTaskStep(
                                label = "获取路线起点和周边搜索区域的坐标",
                                acceptedToolNames = setOf("maps_geo"),
                                instruction = "Call maps_geo for the route origin and the nearby-search area. You may issue both independent geocoding calls in the same response.",
                                requiredEvidenceCount = 2,
                            )
                        )
                    }
                    add(nearbyStep)
                    if (!isMcDonalds) {
                        add(
                            CompositeTaskStep(
                                label = "读取所选地点的真实坐标",
                                acceptedToolNames = setOf("maps_search_detail"),
                                instruction = "Call maps_search_detail with the id of one POI returned by maps_around_search. Keep that POI's coordinates for the route destination.",
                            )
                        )
                    }
                    add(
                        CompositeTaskStep(
                            label = "规划到所选真实地点的路线",
                            acceptedToolNames = routeToolNames,
                            instruction = "Call the requested maps_direction tool only after POI details succeed. The destination must use the selected POI detail coordinates, never the nearby-search center or a generic area.",
                        )
                    )
                },
            )
        }

        val asksWebSearch = normalized.containsAny("bing", "web search", "必应", "网页搜索")
        val asksDetailedSource = normalized.containsAny(
            "latest", "news", "detail", "source", "最新", "新闻", "详情", "来源",
        )
        if (asksWebSearch && asksDetailedSource) {
            return CompositeTaskExecution(
                goal = "搜索最新信息并读取来源页面",
                steps = listOf(
                    CompositeTaskStep(
                        label = "执行网页搜索",
                        acceptedToolNames = setOf("bing_search"),
                        instruction = "Call bing_search with the current year or an explicit current date range, and prefer authoritative sources.",
                    ),
                    CompositeTaskStep(
                        label = "抓取一个权威来源页面",
                        acceptedToolNames = setOf("crawl_webpage"),
                        instruction = "Call crawl_webpage for the best authoritative result returned by bing_search before summarizing it.",
                    ),
                ),
            )
        }

        val asksMeal = normalized.containsAny(
            "what to eat", "meal", "recipe", "吃什么", "菜谱", "做法",
        )
        val asksRecipeDetail = normalized.containsAny("detail", "steps", "详细", "步骤", "做法")
        if (asksMeal && asksRecipeDetail) {
            return CompositeTaskExecution(
                goal = "推荐菜品并取得详细做法",
                steps = listOf(
                    CompositeTaskStep(
                        label = "取得真实菜品推荐",
                        acceptedToolNames = setOf("whattoeat", "recommendmeals"),
                        instruction = "Call the meal recommendation tool and keep a returned recipe id.",
                    ),
                    CompositeTaskStep(
                        label = "读取所选菜品的详细做法",
                        acceptedToolNames = setOf("getrecipebyid"),
                        instruction = "Call getRecipeById using an id returned by the recommendation result.",
                    ),
                ),
            )
        }
        return null
    }

    private fun String.containsAny(vararg candidates: String): Boolean = candidates.any(::contains)
}
