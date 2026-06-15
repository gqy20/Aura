package com.xiaoqi.companion.core.tools

import com.xiaoqi.companion.core.companion.model.ToolCallStatus
import com.xiaoqi.companion.core.tools.parser.ToolCallResultParser
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolDisplayRegistryTest {

    private val registry = ToolDisplayRegistry(ToolCallResultParser())

    @Test
    fun label_returnsSpecificLabelsForRegisteredTools() {
        assertEquals("保存中", registry.label("save_memory", ToolCallStatus.STARTED))
        assertEquals("已保存", registry.label("save_memory", ToolCallStatus.SUCCEEDED))
        assertEquals("搜索中", registry.label("search_memory", ToolCallStatus.STARTED))
        assertEquals("已读取", registry.label("get_current_time", ToolCallStatus.SUCCEEDED))
        assertEquals(
            "读取失败",
            registry.label("get_recent_interaction_context", ToolCallStatus.FAILED),
        )
        assertEquals("已检查", registry.label("get_user_context_settings", ToolCallStatus.SUCCEEDED))
        assertEquals("已读取", registry.label("get_device_status", ToolCallStatus.SUCCEEDED))
        assertEquals("已查询", registry.label("get_weather", ToolCallStatus.SUCCEEDED))
        assertEquals("已创建", registry.label("create_local_reminder", ToolCallStatus.SUCCEEDED))
        assertEquals("已更新", registry.label("update_mood", ToolCallStatus.SUCCEEDED))
        assertEquals("更新失败", registry.label("update_relationship", ToolCallStatus.FAILED))
    }

    @Test
    fun label_returnsGenericLabelsForUnknownTool() {
        assertEquals("使用工具中", registry.label("unknown", ToolCallStatus.STARTED))
        assertEquals("工具已完成", registry.label("unknown", ToolCallStatus.SUCCEEDED))
        assertEquals("工具失败", registry.label("unknown", ToolCallStatus.FAILED))
    }
}
