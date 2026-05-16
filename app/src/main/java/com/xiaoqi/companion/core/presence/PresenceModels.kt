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

data class PresenceUiState(
    val mode: PresenceMode = PresenceMode.IDLE,
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
    val hasError: Boolean = false,
    val hasInputText: Boolean = false,
    val hasPendingImage: Boolean = false,
)
