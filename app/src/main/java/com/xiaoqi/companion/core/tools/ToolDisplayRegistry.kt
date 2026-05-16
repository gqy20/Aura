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
            "save_memory" -> ToolDisplayLabels("Saving memory", "Memory saved", "Memory save failed")
            "search_memory" -> ToolDisplayLabels("Searching memory", "Memory searched", "Memory search failed")
            "get_current_time" -> ToolDisplayLabels("Checking time", "Time checked", "Time check failed")
            "get_recent_interaction_context" -> ToolDisplayLabels(
                "Checking recent activity",
                "Recent activity checked",
                "Recent activity check failed",
            )
            "update_mood" -> ToolDisplayLabels("Updating mood", "Mood updated", "Mood update failed")
            "update_relationship" -> ToolDisplayLabels(
                "Updating relationship",
                "Relationship updated",
                "Relationship update failed",
            )
            else -> ToolDisplayLabels("Using tool", "Tool finished", "Tool failed")
        }

    private data class ToolDisplayLabels(
        val started: String,
        val succeeded: String,
        val failed: String,
    )
}
