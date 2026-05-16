package com.xiaoqi.companion.core.tools

import com.xiaoqi.companion.core.companion.model.ToolCallStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolDisplayRegistryTest {

    private val registry = ToolDisplayRegistry()

    @Test
    fun label_returnsSpecificLabelsForRegisteredTools() {
        assertEquals("Saving memory", registry.label("save_memory", ToolCallStatus.STARTED))
        assertEquals("Memory saved", registry.label("save_memory", ToolCallStatus.SUCCEEDED))
        assertEquals("Searching memory", registry.label("search_memory", ToolCallStatus.STARTED))
        assertEquals("Time checked", registry.label("get_current_time", ToolCallStatus.SUCCEEDED))
        assertEquals(
            "Recent activity check failed",
            registry.label("get_recent_interaction_context", ToolCallStatus.FAILED),
        )
        assertEquals("Settings checked", registry.label("get_user_context_settings", ToolCallStatus.SUCCEEDED))
        assertEquals("Device checked", registry.label("get_device_status", ToolCallStatus.SUCCEEDED))
        assertEquals("Weather checked", registry.label("get_weather", ToolCallStatus.SUCCEEDED))
        assertEquals("Reminder created", registry.label("create_local_reminder", ToolCallStatus.SUCCEEDED))
        assertEquals("Mood updated", registry.label("update_mood", ToolCallStatus.SUCCEEDED))
        assertEquals("Relationship update failed", registry.label("update_relationship", ToolCallStatus.FAILED))
    }

    @Test
    fun label_returnsGenericLabelsForUnknownTool() {
        assertEquals("Using tool", registry.label("unknown", ToolCallStatus.STARTED))
        assertEquals("Tool finished", registry.label("unknown", ToolCallStatus.SUCCEEDED))
        assertEquals("Tool failed", registry.label("unknown", ToolCallStatus.FAILED))
    }
}
