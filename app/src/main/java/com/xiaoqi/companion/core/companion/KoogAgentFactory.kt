package com.xiaoqi.companion.core.companion

import com.xiaoqi.companion.core.prompt.BuiltPrompt
import com.xiaoqi.companion.data.repository.LlmConfig
import kotlinx.coroutines.flow.Flow

interface KoogAgentWrapper {
    suspend fun run(prompt: BuiltPrompt): String
    fun runStreaming(prompt: BuiltPrompt): Flow<String>
}

interface KoogAgentFactory {
    fun create(config: LlmConfig): KoogAgentWrapper
}
