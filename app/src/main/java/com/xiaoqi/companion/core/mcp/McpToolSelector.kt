package com.xiaoqi.companion.core.mcp

/** 在已命中的 MCP server 内按本轮意图选择少量工具，避免把整套 schema 注入模型上下文。 */
internal object McpToolSelector {
    const val DEFAULT_LIMIT = 5

    fun select(
        query: String,
        tools: List<McpToolSpec>,
        limit: Int = DEFAULT_LIMIT,
        requiredToolNames: Set<String> = emptySet(),
    ): List<McpToolSpec> {
        if (tools.size <= limit || query.isBlank()) return tools
        val normalizedQuery = query.lowercase()
        if (normalizedQuery.containsAny("mcp__", "所有mcp", "全部工具", "all mcp", "all tools")) {
            return tools
        }

        val queryTerms = normalizedQuery.searchTerms()
        val ranked = tools.mapIndexed { index, tool ->
            val haystack = "${tool.name} ${tool.description}".lowercase()
            val lexicalScore = queryTerms.sumOf { term ->
                when {
                    term.length < 2 -> 0
                    tool.name.contains(term, ignoreCase = true) -> 6
                    haystack.contains(term) -> 2
                    else -> 0
                }
            }
            val intentScore = intents.sumOf { intent ->
                if (normalizedQuery.containsAny(*intent.queryTerms)) {
                    intent.toolTerms.count(haystack::contains) * intent.weight
                } else {
                    0
                }
            }
            RankedTool(tool, lexicalScore + intentScore - modeMismatchPenalty(normalizedQuery, haystack), index)
        }

        val required = tools.filter { tool ->
            requiredToolNames.any { requiredName ->
                tool.name.contains(requiredName, ignoreCase = true)
            }
        }
        val requiredNames = required.mapTo(mutableSetOf()) { it.name }
        val matched = ranked.filter { it.score > 0 && it.tool.name !in requiredNames }
        if (matched.isEmpty()) return required.ifEmpty { tools }
        val effectiveLimit = limit.coerceAtLeast(required.size).coerceAtLeast(1)
        return (required + matched
            .sortedWith(compareByDescending<RankedTool> { it.score }.thenBy { it.index })
            .map { it.tool })
            .take(effectiveLimit)
    }

    private fun String.searchTerms(): Set<String> {
        val terms = Regex("[a-z0-9_]+|[\\u4e00-\\u9fff]+").findAll(this)
            .flatMap { match ->
                val value = match.value
                if (value.first().isCjk()) {
                    buildList {
                        if (value.length <= 4) add(value)
                        value.windowed(size = 2, step = 1).forEach(::add)
                    }.asSequence()
                } else {
                    sequenceOf(value)
                }
            }
            .filter { it.length >= 2 }
            .toMutableSet()
        terms.removeAll(stopTerms)
        return terms
    }

    private fun Char.isCjk(): Boolean = this in '\u4e00'..'\u9fff'

    private fun String.containsAny(vararg candidates: String): Boolean = candidates.any(::contains)

    private fun modeMismatchPenalty(query: String, tool: String): Int {
        val requestedModes = travelModes.filter { query.containsAny(*it.queryTerms) }
        if (requestedModes.isEmpty()) return 0
        val matchesRequestedMode = requestedModes.any { tool.containsAny(*it.toolTerms) }
        val isDifferentModeTool = travelModes
            .filterNot(requestedModes::contains)
            .any { tool.containsAny(*it.toolTerms) }
        return if (!matchesRequestedMode && isDifferentModeTool) 100 else 0
    }

    private data class RankedTool(
        val tool: McpToolSpec,
        val score: Int,
        val index: Int,
    )

    private data class Intent(
        val queryTerms: Array<String>,
        val toolTerms: Array<String>,
        val weight: Int,
    )

    private data class TravelMode(
        val queryTerms: Array<String>,
        val toolTerms: Array<String>,
    )

    private val intents = listOf(
        Intent(
            queryTerms = arrayOf("附近", "周边", "附近有什么", "nearby", "around", "poi"),
            toolTerms = arrayOf("around_search", "nearby", "周边搜索", "text_search"),
            weight = 14,
        ),
        Intent(
            queryTerms = arrayOf("搜索", "查找", "找一家", "找个", "search", "咖啡", "餐厅", "酒店", "景点"),
            toolTerms = arrayOf("text_search", "around_search", "search_detail", "poi"),
            weight = 8,
        ),
        Intent(
            queryTerms = arrayOf("步行", "走路", "walk", "walking"),
            toolTerms = arrayOf("direction_walking", "walking", "步行"),
            weight = 18,
        ),
        Intent(
            queryTerms = arrayOf("骑行", "骑车", "自行车", "bike", "bicycle", "cycling"),
            toolTerms = arrayOf("direction_bicycling", "bicycling", "cycling", "骑行"),
            weight = 18,
        ),
        Intent(
            queryTerms = arrayOf("开车", "驾车", "自驾", "driving", "drive"),
            toolTerms = arrayOf("direction_driving", "driving", "驾车"),
            weight = 18,
        ),
        Intent(
            queryTerms = arrayOf("公交", "地铁", "公共交通", "transit", "bus", "subway"),
            toolTerms = arrayOf("direction_transit", "transit_integrated", "公交", "地铁"),
            weight = 18,
        ),
        Intent(
            queryTerms = arrayOf("路线", "导航", "怎么去", "到达", "route", "direction", "navigation"),
            toolTerms = arrayOf("direction_", "route", "maps_geo", "geocode", "路线", "地理编码"),
            weight = 8,
        ),
        Intent(
            queryTerms = arrayOf("地址", "地理编码", "地址坐标", "经纬度", "geocode"),
            toolTerms = arrayOf("maps_geo", "geocode", "地理编码"),
            weight = 12,
        ),
        Intent(
            queryTerms = arrayOf("坐标在哪", "坐标位置", "逆地理", "reverse geocode", "regeocode"),
            toolTerms = arrayOf("regeocode", "reverse", "逆地理"),
            weight = 18,
        ),
        Intent(
            queryTerms = arrayOf("距离", "多远", "多少公里", "distance"),
            toolTerms = arrayOf("distance", "距离"),
            weight = 18,
        ),
        Intent(
            queryTerms = arrayOf("天气", "气温", "下雨", "weather", "temperature"),
            toolTerms = arrayOf("weather", "天气"),
            weight = 18,
        ),
        Intent(
            queryTerms = arrayOf("当前位置", "我的位置", "ip定位", "ip location"),
            toolTerms = arrayOf("ip_location", "location_by_ip", "ip定位"),
            weight = 18,
        ),
        Intent(
            queryTerms = arrayOf("详情", "详细信息", "电话", "营业时间", "detail"),
            toolTerms = arrayOf("search_detail", "detail", "详情"),
            weight = 18,
        ),
    )

    private val stopTerms = setOf(
        "帮我", "一下", "一个", "这个", "那个", "怎么", "什么", "可以", "查询", "地图",
    )

    private val travelModes = listOf(
        TravelMode(arrayOf("步行", "走路", "walk", "walking"), arrayOf("walking", "步行")),
        TravelMode(arrayOf("骑行", "骑车", "自行车", "bike", "bicycle", "cycling"), arrayOf("bicycling", "cycling", "骑行")),
        TravelMode(arrayOf("开车", "驾车", "自驾", "driving", "drive"), arrayOf("driving", "驾车")),
        TravelMode(arrayOf("公交", "地铁", "公共交通", "transit", "bus", "subway"), arrayOf("transit", "公交", "地铁")),
    )
}
