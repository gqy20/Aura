package com.xiaoqi.companion.core.presence

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresenceReactionPolicyTest {

    private val policy = PresenceReactionPolicy()

    @Test
    fun shouldShow_whenAmbientDuringThinking_returnsFalse() {
        val result = policy.shouldShow(
            candidate = PresenceReaction.RETURN_BLINK,
            currentState = PresenceUiState(mode = PresenceMode.THINKING),
            nowMillis = 20_000L,
            lastShownAtMillis = null,
        )

        assertFalse(result)
    }

    @Test
    fun shouldShow_whenReturnBlinkInsideCooldown_returnsFalse() {
        val result = policy.shouldShow(
            candidate = PresenceReaction.RETURN_BLINK,
            currentState = PresenceUiState(mode = PresenceMode.IDLE),
            nowMillis = 20_000L,
            lastShownAtMillis = 19_000L,
        )

        assertFalse(result)
    }

    @Test
    fun shouldShow_whenMemorySparkDuringThinking_returnsTrue() {
        val result = policy.shouldShow(
            candidate = PresenceReaction.MEMORY_SPARK,
            currentState = PresenceUiState(mode = PresenceMode.THINKING),
            nowMillis = 20_000L,
            lastShownAtMillis = null,
        )

        assertTrue(result)
    }

    @Test
    fun shouldShow_whenCurrentReactionHasHigherPriority_returnsFalse() {
        val result = policy.shouldShow(
            candidate = PresenceReaction.TOUCH_NUZZLE,
            currentState = PresenceUiState(
                mode = PresenceMode.IDLE,
                reaction = PresenceReaction.MEMORY_SPARK,
            ),
            nowMillis = 20_000L,
            lastShownAtMillis = null,
        )

        assertFalse(result)
    }
}
