package com.xiaoqi.companion.core.local

import com.xiaoqi.companion.core.companion.KoogAgentEvent
import com.xiaoqi.companion.core.companion.KoogAgentWrapper
import com.xiaoqi.companion.core.prompt.BuiltPrompt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.KSerializer

class LocalQwenAgentWrapper(
    private val engine: LocalQwenEngine,
    private val modelName: String = "",
) : KoogAgentWrapper {

    override suspend fun run(prompt: BuiltPrompt): String =
        engine.stream(prompt.toLocalRequest()).toList().joinToString("")

    override suspend fun <T> runStructured(
        prompt: BuiltPrompt,
        serializer: KSerializer<T>,
        examples: List<T>,
    ): T {
        throw UnsupportedOperationException("Local Qwen text MVP does not support structured output yet.")
    }

    override fun runStreaming(prompt: BuiltPrompt): Flow<String> =
        engine.stream(prompt.toLocalRequest())

    override fun runEvents(prompt: BuiltPrompt): Flow<KoogAgentEvent> = flow {
        engine.stream(prompt.toLocalRequest()).collect { emit(KoogAgentEvent.TextDelta(it)) }
    }

    private fun BuiltPrompt.toLocalRequest(): LocalQwenRequest {
        if (hasImage) {
            throw UnsupportedOperationException("Vision prompts are not supported by the local Qwen text MVP yet.")
        }
        return LocalQwenRequest(
            systemPrompt = systemPrompt,
            userMessage = userMessage,
            modelName = modelName,
            allowTools = false,
        )
    }
}
