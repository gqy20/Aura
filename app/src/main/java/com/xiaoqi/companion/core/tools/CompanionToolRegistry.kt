package com.xiaoqi.companion.core.tools

import ai.koog.agents.core.tools.ToolRegistry
import javax.inject.Inject

class CompanionToolRegistry @Inject constructor(
    private val saveMemoryTool: SaveMemoryTool,
) {
    fun create(): ToolRegistry =
        ToolRegistry.builder()
            .tool(saveMemoryTool)
            .build()
}
