package com.xiaoqi.companion.core.tools

import com.xiaoqi.companion.core.companion.model.ToolCallStatus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolDisplayRegistry @Inject constructor() {

    fun label(toolName: String, status: ToolCallStatus): String =
        when (status) {
            ToolCallStatus.STARTED -> labels(toolName).started
            ToolCallStatus.SUCCEEDED -> labels(toolName).succeeded
            ToolCallStatus.FAILED -> labels(toolName).failed
        }

    private fun labels(toolName: String): ToolDisplayLabels =
        when (toolName) {
            "save_memory" -> ToolDisplayLabels("保存记忆中", "已保存记忆", "记忆保存失败")
            "search_memory" -> ToolDisplayLabels("搜索记忆中", "已搜索记忆", "记忆搜索失败")
            "get_current_time" -> ToolDisplayLabels("读取时间中", "已读取时间", "时间读取失败")
            "get_recent_interaction_context" -> ToolDisplayLabels(
                "读取最近互动中",
                "已读取最近互动",
                "最近互动读取失败",
            )
            "get_user_context_settings" -> ToolDisplayLabels("检查设置中", "已检查设置", "设置检查失败")
            "get_device_status" -> ToolDisplayLabels("读取设备状态中", "已读取设备状态", "设备状态读取失败")
            "get_weather" -> ToolDisplayLabels("查询天气中", "已查询天气", "天气查询失败")
            "create_local_reminder" -> ToolDisplayLabels("创建提醒中", "已创建提醒", "提醒创建失败")
            "update_mood" -> ToolDisplayLabels("更新情绪中", "已更新情绪", "情绪更新失败")
            "update_relationship" -> ToolDisplayLabels(
                "更新关系中",
                "已更新关系",
                "关系更新失败",
            )
            else -> ToolDisplayLabels("使用工具中", "工具已完成", "工具失败")
        }

    private data class ToolDisplayLabels(
        val started: String,
        val succeeded: String,
        val failed: String,
    )
}
