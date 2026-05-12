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
) : AgentToolRegistry {
    override fun create(): ToolRegistry =
        ToolRegistry.builder()
            .tool(saveMemoryTool)
            .tool(searchMemoryTool)
            .tool(updateMoodTool)
            .tool(updateRelationshipTool)
            .build()
}
