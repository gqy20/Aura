package com.xiaoqi.companion.core.tools

import com.xiaoqi.companion.core.companion.model.ToolCallStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolDisplayRegistryTest {

    private val registry = ToolDisplayRegistry()

    @Test
    fun label_returnsSpecificLabelsForRegisteredTools() {
        assertEquals("保存记忆中", registry.label("save_memory", ToolCallStatus.STARTED))
        assertEquals("已保存记忆", registry.label("save_memory", ToolCallStatus.SUCCEEDED))
        assertEquals("搜索记忆中", registry.label("search_memory", ToolCallStatus.STARTED))
        assertEquals("已读取时间", registry.label("get_current_time", ToolCallStatus.SUCCEEDED))
        assertEquals(
            "最近互动读取失败",
            registry.label("get_recent_interaction_context", ToolCallStatus.FAILED),
        )
        assertEquals("已检查设置", registry.label("get_user_context_settings", ToolCallStatus.SUCCEEDED))
        assertEquals("已读取设备状态", registry.label("get_device_status", ToolCallStatus.SUCCEEDED))
        assertEquals("已查询天气", registry.label("get_weather", ToolCallStatus.SUCCEEDED))
        assertEquals("已创建提醒", registry.label("create_local_reminder", ToolCallStatus.SUCCEEDED))
        assertEquals("已更新情绪", registry.label("update_mood", ToolCallStatus.SUCCEEDED))
        assertEquals("关系更新失败", registry.label("update_relationship", ToolCallStatus.FAILED))
    }

    @Test
    fun label_returnsGenericLabelsForUnknownTool() {
        assertEquals("使用工具中", registry.label("unknown", ToolCallStatus.STARTED))
        assertEquals("工具已完成", registry.label("unknown", ToolCallStatus.SUCCEEDED))
        assertEquals("工具失败", registry.label("unknown", ToolCallStatus.FAILED))
    }
}
