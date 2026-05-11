package com.xiaoqi.companion.core.companion

import com.xiaoqi.companion.core.prompt.BuiltPrompt
import com.xiaoqi.companion.data.repository.LlmConfig

interface KoogAgentWrapper {
    suspend fun run(prompt: BuiltPrompt): String
}

interface KoogAgentFactory {
    fun create(config: LlmConfig): KoogAgentWrapper
}
