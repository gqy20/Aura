package com.xiaoqi.companion.core.companion.model

// --- User Input ---

sealed class UserInput {
    abstract val content: String

    data class Text(override val content: String) : UserInput()
    data class Vision(
        val text: String,
        val imageBase64: String,
        val mediaType: String = "image/jpeg",
    ) : UserInput() {
        override val content get() = text
    }
    data class Speech(val transcript: String) : UserInput() {
        override val content get() = transcript
    }
}

// --- Agent Events ---

sealed class AgentEvent {
    data class Streaming(val delta: String) : AgentEvent()
    data class ToolCallUpdated(val call: AgentToolCall) : AgentEvent()
    data class ToolStarted(val name: String) : AgentEvent()
    data class ToolFinished(val name: String) : AgentEvent()
    data class Complete(val parsed: ParsedOutput) : AgentEvent()
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

// --- Parsed Output ---

data class ParsedOutput(
    val textReply: String = "",
    val emotionSignal: EmotionSignal = EmotionSignal(),
    val interactionSignal: InteractionSignal = InteractionSignal(),
    val actions: List<AgentAction> = emptyList(),
)

data class EmotionSignal(
    val mood: String = "neutral",
    val intensity: Float = 0.5f,
    val trigger: String = "",
    val emotionVector: Map<String, Float> = emptyMap(),
)

data class InteractionSignal(
    val affinityDelta: Float = 0f,
    val topicTags: List<String> = emptyList(),
    val isDeepConversation: Boolean = false,
)

data class AgentAction(
    val type: String,
    val params: Map<String, String> = emptyMap(),
)
