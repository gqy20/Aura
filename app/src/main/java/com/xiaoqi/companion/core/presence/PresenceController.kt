package com.xiaoqi.companion.core.presence

import com.xiaoqi.companion.core.companion.model.ToolCallStatus
import javax.inject.Inject

class PresenceController @Inject constructor() {

    fun derive(inputs: PresenceInputs): PresenceUiState {
        val normalizedMood = inputs.mood.ifBlank { "neutral" }.lowercase()
        val mode = when {
            inputs.hasError -> PresenceMode.ERROR
            inputs.latestToolStatus == ToolCallStatus.FAILED -> PresenceMode.ERROR
            inputs.latestToolStatus == ToolCallStatus.STARTED && inputs.latestToolName.isMemorySearch() -> PresenceMode.SEARCHING
            inputs.latestToolStatus == ToolCallStatus.STARTED && inputs.latestToolName.isMemoryWrite() -> PresenceMode.REMEMBERING
            inputs.latestToolStatus == ToolCallStatus.STARTED -> PresenceMode.THINKING
            inputs.latestToolStatus == ToolCallStatus.SUCCEEDED && inputs.latestToolName.isMemoryWrite() -> PresenceMode.REMEMBERING
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
            label = mode.labelFor(normalizedMood),
        )
    }

    fun reactionFor(event: PresenceEvent): PresenceReaction =
        when (event) {
            PresenceEvent.UserTapped -> PresenceReaction.TOUCH_NUZZLE
            PresenceEvent.AppReturned -> PresenceReaction.RETURN_BLINK
            PresenceEvent.ErrorShown -> PresenceReaction.ERROR_RECOVER
            is PresenceEvent.ToolChanged -> when {
                event.status == ToolCallStatus.SUCCEEDED && event.name.isMemoryWrite() -> PresenceReaction.MEMORY_SPARK
                event.status == ToolCallStatus.STARTED && event.name.isMemorySearch() -> PresenceReaction.SEARCH_SWEEP
                event.status == ToolCallStatus.FAILED -> PresenceReaction.ERROR_RECOVER
                else -> PresenceReaction.RETURN_BLINK
            }
        }

    private fun String?.isMemorySearch(): Boolean =
        this == "search_memory"

    private fun String?.isMemoryWrite(): Boolean =
        this == "save_memory"

    private fun String.isHappyMood(): Boolean =
        this in setOf("happy", "joy", "excited", "warm", "calm")

    private fun String.isSadMood(): Boolean =
        this in setOf("sad", "lonely", "anxious", "worried", "upset")

    private fun String.isTiredMood(): Boolean =
        this in setOf("tired", "sleepy", "exhausted", "low")

    private fun PresenceMode.labelFor(mood: String): String =
        when (this) {
            PresenceMode.IDLE -> "Aura is with you"
            PresenceMode.LISTENING -> "Listening closely"
            PresenceMode.THINKING -> "Thinking"
            PresenceMode.SPEAKING -> "Speaking"
            PresenceMode.SEARCHING -> "Looking through memories"
            PresenceMode.REMEMBERING -> "Saving this gently"
            PresenceMode.HAPPY -> "Feeling $mood"
            PresenceMode.SAD -> "Feeling $mood"
            PresenceMode.TIRED -> "A little tired"
            PresenceMode.SLEEPING -> "Resting"
            PresenceMode.ERROR -> "Needs a moment"
        }
}
