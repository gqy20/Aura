package com.xiaoqi.companion.core.presence

import javax.inject.Inject

class PresenceReactionPolicy @Inject constructor() {

    fun shouldShow(
        candidate: PresenceReaction,
        currentState: PresenceUiState,
        nowMillis: Long,
        lastShownAtMillis: Long?,
    ): Boolean {
        if (lastShownAtMillis != null && nowMillis - lastShownAtMillis < candidate.cooldownMillis()) {
            return false
        }
        if (currentState.reaction != null && currentState.reaction.priority() > candidate.priority()) {
            return false
        }
        if (candidate.isAmbient() && currentState.mode.isTaskFocused()) {
            return false
        }
        return true
    }

    fun displayDurationMillis(reaction: PresenceReaction): Long =
        when (reaction) {
            PresenceReaction.ERROR_RECOVER -> 2_600L
            PresenceReaction.MEMORY_SPARK -> 2_300L
            PresenceReaction.SEARCH_SWEEP -> 1_900L
            PresenceReaction.RETURN_BLINK -> 1_600L
            PresenceReaction.TOUCH_NUZZLE -> 1_200L
        }

    private fun PresenceReaction.cooldownMillis(): Long =
        when (this) {
            PresenceReaction.ERROR_RECOVER -> 0L
            PresenceReaction.MEMORY_SPARK -> 1_500L
            PresenceReaction.SEARCH_SWEEP -> 1_500L
            PresenceReaction.RETURN_BLINK -> 10 * 60 * 1_000L
            PresenceReaction.TOUCH_NUZZLE -> 900L
        }

    private fun PresenceReaction.priority(): Int =
        when (this) {
            PresenceReaction.ERROR_RECOVER -> 50
            PresenceReaction.MEMORY_SPARK -> 40
            PresenceReaction.SEARCH_SWEEP -> 30
            PresenceReaction.RETURN_BLINK -> 20
            PresenceReaction.TOUCH_NUZZLE -> 10
        }

    private fun PresenceReaction.isAmbient(): Boolean =
        this == PresenceReaction.RETURN_BLINK || this == PresenceReaction.TOUCH_NUZZLE

    private fun PresenceMode.isTaskFocused(): Boolean =
        this == PresenceMode.THINKING ||
            this == PresenceMode.SPEAKING ||
            this == PresenceMode.SEARCHING ||
            this == PresenceMode.REMEMBERING ||
            this == PresenceMode.ERROR
}
