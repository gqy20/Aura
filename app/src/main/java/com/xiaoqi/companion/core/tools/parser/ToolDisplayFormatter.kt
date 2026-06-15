package com.xiaoqi.companion.core.tools.parser

import com.xiaoqi.companion.core.companion.model.ToolCallStatus

/**
 * 把 [ToolResultSummary] + [ToolCallStatus] → UI chip 上的简短文案。
 *
 * **目的**:替代 `ToolDisplayRegistry` 的固定字符串("已搜索"/"已保存"),让 chip
 * 直接显示数据规模("已搜索 3 条记忆" / "已保存 1 条"),少一次展开。
 *
 * **返回 null 时** 由调用方回退到 `ToolDisplayRegistry.label(...)` 的静态文案。
 *
 * **不依赖 Android Context**:纯字符串拼接,便于单测。
 */
object ToolDisplayFormatter {

    fun format(summary: ToolResultSummary?, status: ToolCallStatus): String? {
        if (status == ToolCallStatus.STARTED) return null
        if (summary == null) return null
        return when (status) {
            ToolCallStatus.STARTED -> null
            ToolCallStatus.SUCCEEDED -> successLabel(summary)
            ToolCallStatus.FAILED -> failedLabel(summary)
        }
    }

    private fun successLabel(summary: ToolResultSummary): String = when (summary) {
        is ToolResultSummary.ListHits -> "已${summary.title} ${summary.count} 条"
        is ToolResultSummary.SavedOne -> "已${summary.title} · ${summary.subject}"
        is ToolResultSummary.Scheduled -> "已创建提醒 · ${summary.subject}"
        is ToolResultSummary.KeyValueReport -> "已${summary.title}"
        is ToolResultSummary.Empty -> "${summary.title} · 无结果"
        is ToolResultSummary.Unknown -> "已完成"
        is ToolResultSummary.Failed -> "${summary.title} 失败" // 不应到这里
    }

    private fun failedLabel(summary: ToolResultSummary): String = when (summary) {
        is ToolResultSummary.Failed -> "${summary.title}失败 · ${summary.reason}"
        else -> "工具失败"
    }
}
