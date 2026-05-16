package com.xiaoqi.companion.core.presence

import com.xiaoqi.companion.core.companion.model.ToolCallStatus

enum class PresenceMode {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    SEARCHING,
    REMEMBERING,
    HAPPY,
    SAD,
    TIRED,
    SLEEPING,
    ERROR,
}

enum class PresenceReaction {
    TOUCH_NUZZLE,
    MEMORY_SPARK,
    SEARCH_SWEEP,
    RETURN_BLINK,
    ERROR_RECOVER,
}

sealed class PresenceEvent {
    data object UserTapped : PresenceEvent()
    data object AppReturned : PresenceEvent()
    data class ToolChanged(
        val name: String,
        val status: ToolCallStatus,
    ) : PresenceEvent()
    data object ErrorShown : PresenceEvent()
}

data class PresenceUiState(
    val mode: PresenceMode = PresenceMode.IDLE,
    val reaction: PresenceReaction? = null,
    val mood: String = "neutral",
    val intensity: Float = 0.5f,
    val relationshipLevel: Float = 0f,
    val label: String = "Aura is here",
)

data class PresenceInputs(
    val mood: String,
    val intensity: Float,
    val relationshipLevel: Float,
    val isLoading: Boolean,
    val isStreaming: Boolean,
    val latestToolName: String? = null,
    val latestToolStatus: ToolCallStatus? = null,
    val reaction: PresenceReaction? = null,
    val hasError: Boolean = false,
    val hasInputText: Boolean = false,
    val hasPendingImage: Boolean = false,
)
