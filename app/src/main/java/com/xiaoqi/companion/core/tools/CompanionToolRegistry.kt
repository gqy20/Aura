package com.xiaoqi.companion.core.tools

import ai.koog.agents.core.tools.ToolRegistry
import javax.inject.Inject

interface AgentToolRegistry {
    fun create(): ToolRegistry
}

class CompanionToolRegistry @Inject constructor(
    private val saveMemoryTool: SaveMemoryTool,
    private val searchMemoryTool: SearchMemoryTool,
    private val updateMoodTool: UpdateMoodTool,
    private val updateRelationshipTool: UpdateRelationshipTool,
    private val getCurrentTimeTool: GetCurrentTimeTool,
    private val getRecentInteractionContextTool: GetRecentInteractionContextTool,
    private val getUserContextSettingsTool: GetUserContextSettingsTool,
    private val getDeviceStatusTool: GetDeviceStatusTool,
    private val getWeatherTool: GetWeatherTool,
    private val createLocalReminderTool: CreateLocalReminderTool,
) : AgentToolRegistry {
    override fun create(): ToolRegistry =
        ToolRegistry.builder()
            .tool(saveMemoryTool)
            .tool(searchMemoryTool)
            .tool(updateMoodTool)
            .tool(updateRelationshipTool)
            .tool(getCurrentTimeTool)
            .tool(getRecentInteractionContextTool)
            .tool(getUserContextSettingsTool)
            .tool(getDeviceStatusTool)
            .tool(getWeatherTool)
            .tool(createLocalReminderTool)
            .build()
}
