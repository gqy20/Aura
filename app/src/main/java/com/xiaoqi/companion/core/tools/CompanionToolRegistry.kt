package com.xiaoqi.companion.core.tools

import ai.koog.agents.core.tools.ToolRegistry
import javax.inject.Inject

interface AgentToolRegistry {
    fun create(): ToolRegistry
}

class CompanionToolRegistry @Inject constructor(
    private val saveMemoryTool: SaveMemoryTool,
) : AgentToolRegistry {
    override fun create(): ToolRegistry =
        ToolRegistry.builder()
            .tool(saveMemoryTool)
            .build()
}
