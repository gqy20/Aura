package com.xiaoqi.companion.core.companion

import com.xiaoqi.companion.core.companion.model.AgentToolCall
import com.xiaoqi.companion.core.companion.model.ToolCallStatus
import com.xiaoqi.companion.core.prompt.BuiltPrompt
import com.xiaoqi.companion.data.repository.LlmConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer

interface KoogAgentWrapper {
    suspend fun run(prompt: BuiltPrompt): String
    suspend fun <T> runStructured(
        prompt: BuiltPrompt,
        serializer: KSerializer<T>,
        examples: List<T> = emptyList(),
    ): T
    fun runStreaming(prompt: BuiltPrompt): Flow<String>
    fun runEvents(prompt: BuiltPrompt): Flow<KoogAgentEvent> =
        runStreaming(prompt).map { KoogAgentEvent.TextDelta(it) }
}

interface KoogAgentFactory {
    /**
     * @param allowLocalTools 仅 LOCAL_QWEN provider 生效。true = 注入 AgentToolRegistry,走软协议工具循环;
     * false(默认) = ToolRegistry.EMPTY,纯文本陪伴对话。云端 provider 忽略此参数。
     */
    fun create(config: LlmConfig, sessionId: String = "default", allowLocalTools: Boolean = false): KoogAgentWrapper
}

sealed class KoogAgentEvent {
    data class TextDelta(val text: String) : KoogAgentEvent()
    data class ToolCallUpdated(val call: AgentToolCall) : KoogAgentEvent()
    data class ToolStarted(val name: String) : KoogAgentEvent()
    data class ToolFinished(val name: String) : KoogAgentEvent()

    companion object {
        fun toolStarted(name: String, callId: String? = null): ToolCallUpdated =
            ToolCallUpdated(AgentToolCall(name = name, status = ToolCallStatus.STARTED, callId = callId))

        fun toolSucceeded(name: String, callId: String? = null): ToolCallUpdated =
            ToolCallUpdated(AgentToolCall(name = name, status = ToolCallStatus.SUCCEEDED, callId = callId))

        fun toolFailed(name: String, callId: String? = null, errorMessage: String? = null): ToolCallUpdated =
            ToolCallUpdated(
                AgentToolCall(
                    name = name,
                    status = ToolCallStatus.FAILED,
                    callId = callId,
                    errorMessage = errorMessage,
                )
            )
    }
}
