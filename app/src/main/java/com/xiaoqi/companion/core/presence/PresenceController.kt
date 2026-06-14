package com.xiaoqi.companion.core.presence

import com.xiaoqi.companion.core.companion.model.ToolCallStatus
import javax.inject.Inject

class PresenceController @Inject constructor() {

    fun derive(inputs: PresenceInputs): PresenceUiState {
        val normalizedMood = inputs.mood.ifBlank { "neutral" }.lowercase()
        val mode = when {
            !inputs.isConfigReady -> PresenceMode.ERROR
            inputs.hasError -> PresenceMode.ERROR
            inputs.latestToolStatus == ToolCallStatus.FAILED -> PresenceMode.ERROR
            inputs.reaction == PresenceReaction.MEMORY_SPARK -> PresenceMode.REMEMBERING
            inputs.latestToolStatus == ToolCallStatus.STARTED && inputs.latestToolName.isMemorySearch() -> PresenceMode.SEARCHING
            inputs.latestToolStatus == ToolCallStatus.SUCCEEDED && inputs.latestToolName.isMemorySearch() -> PresenceMode.REMEMBERING
            inputs.latestToolStatus == ToolCallStatus.STARTED -> PresenceMode.THINKING
            inputs.isStreaming -> PresenceMode.SPEAKING
            inputs.isLoading -> PresenceMode.THINKING
            inputs.hasInputText || inputs.hasPendingImage -> PresenceMode.LISTENING
            normalizedMood.isHappyMood() -> PresenceMode.HAPPY
            normalizedMood.isSadMood() -> PresenceMode.SAD
            normalizedMood.isTiredMood() -> PresenceMode.TIRED
            else -> PresenceMode.IDLE
        }

        return PresenceUiState(
            mode = mode,
            reaction = inputs.reaction,
            mood = normalizedMood,
            intensity = inputs.intensity.coerceIn(0f, 1f),
            relationshipLevel = inputs.relationshipLevel.coerceIn(0f, 1f),
            label = mode.labelFor(normalizedMood, inputs),
            detail = mode.detailFor(inputs),
            accent = mode.accent(),
        )
    }

    fun reactionFor(event: PresenceEvent): PresenceReaction =
        when (event) {
            PresenceEvent.UserTapped -> PresenceReaction.TOUCH_NUZZLE
            PresenceEvent.AppReturned -> PresenceReaction.RETURN_BLINK
            PresenceEvent.ErrorShown -> PresenceReaction.ERROR_RECOVER
            is PresenceEvent.MemorySaved -> PresenceReaction.MEMORY_SPARK
            is PresenceEvent.ToolChanged -> when {
                event.status == ToolCallStatus.STARTED && event.name.isMemorySearch() -> PresenceReaction.SEARCH_SWEEP
                event.status == ToolCallStatus.FAILED -> PresenceReaction.ERROR_RECOVER
                else -> PresenceReaction.RETURN_BLINK
            }
        }

    private fun String.isHappyMood(): Boolean =
        this in setOf("happy", "joy", "excited", "warm", "calm")

    private fun String.isSadMood(): Boolean =
        this in setOf("sad", "lonely", "anxious", "worried", "upset")

    private fun String.isTiredMood(): Boolean =
        this in setOf("tired", "sleepy", "exhausted", "low")

    private fun String?.isMemorySearch(): Boolean =
        this == "search_memory" || this == "search_records" || this == "search_summaries"

    private fun PresenceMode.labelFor(mood: String, inputs: PresenceInputs): String =
        when (this) {
            PresenceMode.ERROR -> if (!inputs.isConfigReady) "Setup" else "Recovering"
            PresenceMode.IDLE -> "Here"
            PresenceMode.LISTENING -> "Listening"
            PresenceMode.THINKING -> "Thinking"
            PresenceMode.SPEAKING -> "Replying"
            PresenceMode.SEARCHING -> "Searching"
            PresenceMode.REMEMBERING -> ""
            PresenceMode.HAPPY -> "Bright"
            PresenceMode.SAD -> "Soft"
            PresenceMode.TIRED -> "Resting"
            PresenceMode.SLEEPING -> "Resting"
        }

    private fun PresenceMode.detailFor(inputs: PresenceInputs): String =
        when (this) {
            PresenceMode.ERROR -> if (!inputs.isConfigReady) {
                inputs.configDetail.ifBlank { "Model needed" }
            } else {
                "Try again"
            }
            PresenceMode.IDLE -> if (inputs.recentMemoryCount > 0) {
                "${inputs.recentMemoryCount} memories"
            } else {
                ""
            }
            PresenceMode.LISTENING -> if (inputs.hasPendingImage) {
                "Image ready"
            } else {
                ""
            }
            PresenceMode.THINKING -> ""
            PresenceMode.SPEAKING -> ""
            PresenceMode.SEARCHING -> "Memory"
            PresenceMode.REMEMBERING -> "Memory"
            PresenceMode.HAPPY -> ""
            PresenceMode.SAD -> ""
            PresenceMode.TIRED -> ""
            PresenceMode.SLEEPING -> ""
        }

    private fun PresenceMode.accent(): String =
        when (this) {
            PresenceMode.ERROR -> "needs_setup"
            PresenceMode.LISTENING -> "listening"
            PresenceMode.THINKING -> "thinking"
            PresenceMode.SPEAKING -> "speaking"
            PresenceMode.SEARCHING -> "searching"
            PresenceMode.REMEMBERING -> "remembering"
            PresenceMode.HAPPY -> "warm"
            PresenceMode.SAD -> "soft"
            PresenceMode.TIRED, PresenceMode.SLEEPING -> "resting"
            PresenceMode.IDLE -> "present"
        }
}
