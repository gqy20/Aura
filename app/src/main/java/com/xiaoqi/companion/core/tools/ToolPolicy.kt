package com.xiaoqi.companion.core.tools

enum class ToolCategory {
    READ_CONTEXT,
    LOCAL_WRITE,
    REMOTE_READ,
    REMOTE_WRITE,
    HIGH_RISK_ACTION,
}

enum class ToolRiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    BLOCKED,
}

data class ToolMetadata(
    val name: String,
    val category: ToolCategory,
    val riskLevel: ToolRiskLevel,
)

data class ToolPolicy(
    val allowedCategories: Set<ToolCategory>,
    val maxRiskLevel: ToolRiskLevel,
    val maxToolRoundsPerTurn: Int = DEFAULT_MAX_TOOL_ROUNDS_PER_TURN,
    val maxToolCallsPerTurn: Int = DEFAULT_MAX_TOOL_CALLS_PER_TURN,
) {
    val allowTools: Boolean = allowedCategories.isNotEmpty() && maxRiskLevel != ToolRiskLevel.BLOCKED

    fun allows(metadata: ToolMetadata): Boolean =
        metadata.category in allowedCategories &&
            metadata.riskLevel.weight <= maxRiskLevel.weight &&
            metadata.riskLevel != ToolRiskLevel.BLOCKED

    companion object {
        const val DEFAULT_MAX_TOOL_ROUNDS_PER_TURN = 3
        const val DEFAULT_MAX_TOOL_CALLS_PER_TURN = 6

        val none = ToolPolicy(
            allowedCategories = emptySet(),
            maxRiskLevel = ToolRiskLevel.BLOCKED,
        )

        val chatDefault = ToolPolicy(
            allowedCategories = setOf(
                ToolCategory.READ_CONTEXT,
                ToolCategory.LOCAL_WRITE,
                ToolCategory.REMOTE_READ,
            ),
            maxRiskLevel = ToolRiskLevel.MEDIUM,
        )

        val systemOnly = ToolPolicy(
            allowedCategories = setOf(
                ToolCategory.READ_CONTEXT,
                ToolCategory.LOCAL_WRITE,
            ),
            maxRiskLevel = ToolRiskLevel.MEDIUM,
        )

        val readOnly = ToolPolicy(
            allowedCategories = setOf(
                ToolCategory.READ_CONTEXT,
                ToolCategory.REMOTE_READ,
            ),
            maxRiskLevel = ToolRiskLevel.LOW,
        )
    }
}

private val ToolRiskLevel.weight: Int
    get() = when (this) {
        ToolRiskLevel.LOW -> 0
        ToolRiskLevel.MEDIUM -> 1
        ToolRiskLevel.HIGH -> 2
        ToolRiskLevel.BLOCKED -> 3
    }

object ToolMetadataRegistry {
    val searchMemory = ToolMetadata("search_memory", ToolCategory.READ_CONTEXT, ToolRiskLevel.LOW)
    val searchRecords = ToolMetadata("search_records", ToolCategory.READ_CONTEXT, ToolRiskLevel.LOW)
    val searchSummaries = ToolMetadata("search_summaries", ToolCategory.READ_CONTEXT, ToolRiskLevel.LOW)
    val getCurrentTime = ToolMetadata("get_current_time", ToolCategory.READ_CONTEXT, ToolRiskLevel.LOW)
    val getRecentInteractionContext = ToolMetadata("get_recent_interaction_context", ToolCategory.READ_CONTEXT, ToolRiskLevel.LOW)
    val getUserContextSettings = ToolMetadata("get_user_context_settings", ToolCategory.READ_CONTEXT, ToolRiskLevel.LOW)
    val getDeviceStatus = ToolMetadata("get_device_status", ToolCategory.READ_CONTEXT, ToolRiskLevel.LOW)
    val getWeather = ToolMetadata("get_weather", ToolCategory.READ_CONTEXT, ToolRiskLevel.LOW)
    val queryHealthData = ToolMetadata("query_health_data", ToolCategory.READ_CONTEXT, ToolRiskLevel.LOW)
    val updateState = ToolMetadata("update_state", ToolCategory.LOCAL_WRITE, ToolRiskLevel.MEDIUM)
    val createLocalReminder = ToolMetadata("create_local_reminder", ToolCategory.LOCAL_WRITE, ToolRiskLevel.MEDIUM)

    fun remoteMcp(name: String): ToolMetadata =
        ToolMetadata(name, ToolCategory.REMOTE_READ, ToolRiskLevel.LOW)
}
