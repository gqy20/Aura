package com.xiaoqi.companion.core.tools

import ai.koog.agents.core.tools.ToolRegistry
import javax.inject.Inject

class CompanionToolRegistry @Inject constructor(
    private val saveMemoryTool: SaveMemoryTool,
    private val searchMemoryTool: SearchMemoryTool,
    private val updateMoodTool: UpdateMoodTool,
    private val updateRelationshipTool: UpdateRelationshipTool,
) {
    fun create(): ToolRegistry =
        ToolRegistry.builder()
            .tool(saveMemoryTool)
            .tool(searchMemoryTool)
            .tool(updateMoodTool)
            .tool(updateRelationshipTool)
            .build()
}
