package com.xiaoqi.companion.core.companion

import com.xiaoqi.companion.core.prompt.BuiltPrompt
import com.xiaoqi.companion.data.repository.LlmConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface KoogAgentWrapper {
    suspend fun run(prompt: BuiltPrompt): String
    fun runStreaming(prompt: BuiltPrompt): Flow<String>
    fun runEvents(prompt: BuiltPrompt): Flow<KoogAgentEvent> =
        runStreaming(prompt).map { KoogAgentEvent.TextDelta(it) }
}

interface KoogAgentFactory {
    fun create(config: LlmConfig): KoogAgentWrapper
}

sealed class KoogAgentEvent {
    data class TextDelta(val text: String) : KoogAgentEvent()
    data class ToolStarted(val name: String) : KoogAgentEvent()
    data class ToolFinished(val name: String) : KoogAgentEvent()
}
