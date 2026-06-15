package com.xiaoqi.companion.core.tools

import com.xiaoqi.companion.core.companion.model.ToolCallStatus
import com.xiaoqi.companion.core.tools.parser.ToolCallResultParser
import com.xiaoqi.companion.core.tools.parser.ToolDisplayFormatter
import com.xiaoqi.companion.core.tools.parser.ToolResultSummary
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tool chip / pill 上展示的文案。
 *
 * **两条路径**:
 * 1. **静态回退**(`label(toolName, status)`):没有 resultJson 时返回固定文案。
 * 2. **动态解析**(`resolveLabel(toolName, status, resultJson)`):用 [ToolCallResultParser]
 *    把 resultJson 解析成 [ToolResultSummary],再用 [ToolDisplayFormatter] 拼出
 *    "已搜索 3 条记忆" 这类带数据的文案;解析失败回退到静态路径。
 */
@Singleton
class ToolDisplayRegistry @Inject constructor(
    private val parser: ToolCallResultParser,
) {

    /** 静态回退 —— 与原版同名同行为,旧调用方不破。 */
    fun label(toolName: String, status: ToolCallStatus): String =
        staticLabel(toolName, status)

    /**
     * 优先用 [resultJson] 解析出带数据的文案;解析失败或为空时回退到静态文案。
     *
     * - STARTED 不解析(还没结果)
     * - FAILED 总会拿到 reason(因为 envelope 错误也是合法 resultJson)
     */
    fun resolveLabel(
        toolName: String,
        status: ToolCallStatus,
        resultJson: String?,
        errorMessage: String? = null,
    ): String {
        // 失败但没 resultJson(早期异常):用 errorMessage 当 hint
        val effectiveJson = resultJson ?: errorMessage?.let {
            """{"status":"error","reason":"${escapeJsonString(it)}","hint":""}"""
        }
        val summary = parser.parse(toolName, effectiveJson)
        return ToolDisplayFormatter.format(summary, status) ?: staticLabel(toolName, status)
    }

    private fun staticLabel(toolName: String, status: ToolCallStatus): String =
        when (status) {
            ToolCallStatus.STARTED -> labels(toolName).started
            ToolCallStatus.SUCCEEDED -> labels(toolName).succeeded
            ToolCallStatus.FAILED -> labels(toolName).failed
        }

    private fun labels(toolName: String): ToolDisplayLabels =
        when (toolName) {
            "save_memory" -> ToolDisplayLabels("保存中", "已保存", "保存失败")
            "search_memory" -> ToolDisplayLabels("搜索中", "已搜索", "搜索失败")
            "get_current_time" -> ToolDisplayLabels("读取时间中", "已读取", "读取失败")
            "get_recent_interaction_context" -> ToolDisplayLabels(
                "读取最近互动中",
                "已读取",
                "读取失败",
            )
            "get_user_context_settings" -> ToolDisplayLabels("检查设置中", "已检查", "检查失败")
            "get_device_status" -> ToolDisplayLabels("读取设备状态中", "已读取", "读取失败")
            "get_weather" -> ToolDisplayLabels("查询天气中", "已查询", "查询失败")
            "create_local_reminder" -> ToolDisplayLabels("创建提醒中", "已创建", "创建失败")
            "update_mood" -> ToolDisplayLabels("更新情绪中", "已更新", "更新失败")
            "update_relationship" -> ToolDisplayLabels(
                "更新关系中",
                "已更新",
                "更新失败",
            )
            else -> if (toolName.startsWith("mcp__")) {
                ToolDisplayLabels("调用远端 MCP", "MCP 已完成", "MCP 失败")
            } else {
                ToolDisplayLabels("使用工具中", "工具已完成", "工具失败")
            }
        }

    private fun escapeJsonString(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    private data class ToolDisplayLabels(
        val started: String,
        val succeeded: String,
        val failed: String,
    )
}
