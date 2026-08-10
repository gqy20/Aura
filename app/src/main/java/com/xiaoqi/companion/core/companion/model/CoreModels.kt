package com.xiaoqi.companion.core.companion.model

// --- User Input ---

sealed class UserInput {
    abstract val content: String

    data class Text(override val content: String) : UserInput()
    data class Vision(
        val text: String,
        val imageBase64: String,
        val mediaType: String = "image/jpeg",
        val displayText: String = text,
    ) : UserInput() {
        override val content get() = displayText.ifBlank { "分享了一张图片" }
    }
    data class Speech(val transcript: String) : UserInput() {
        override val content get() = transcript
    }
}

// --- Agent Events ---

sealed class AgentEvent {
    data class Streaming(val delta: String) : AgentEvent()
    data object StreamingReset : AgentEvent()
    data class Progress(val stage: String, val message: String = "") : AgentEvent()
    data class ToolCallUpdated(val call: AgentToolCall) : AgentEvent()
    data class ToolStarted(val name: String) : AgentEvent()
    data class ToolFinished(val name: String) : AgentEvent()
    data class RemoteStatus(val runId: String, val status: String) : AgentEvent()
    data class MemorySaved(val count: Int) : AgentEvent()
    data class Complete(
        val textReply: String = "",
        val persistedMessageId: String? = null,
    ) : AgentEvent()
    data class Error(val error: AgentError) : AgentEvent()
}

data class AgentToolCall(
    val name: String,
    val status: ToolCallStatus,
    val callId: String? = null,
    val argumentsJson: String? = null,
    val resultJson: String? = null,
    val errorMessage: String? = null,
)

enum class ToolCallStatus {
    STARTED,
    SUCCEEDED,
    FAILED,
}

sealed class AgentError {
    object NetworkTimeout : AgentError()
    object RateLimited : AgentError()
    data class ApiError(val message: String, val code: Int? = null) : AgentError()
    data class ParseError(val reason: String) : AgentError()
}
