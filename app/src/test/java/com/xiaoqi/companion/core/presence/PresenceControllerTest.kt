package com.xiaoqi.companion.core.presence

import com.xiaoqi.companion.core.companion.model.ToolCallStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class PresenceControllerTest {

    private val controller = PresenceController()

    @Test
    fun derive_whenInputHasText_returnsListening() {
        val state = controller.derive(inputs(hasInputText = true))

        assertEquals(PresenceMode.LISTENING, state.mode)
    }

    @Test
    fun derive_whenStreaming_returnsSpeaking() {
        val state = controller.derive(inputs(isStreaming = true))

        assertEquals(PresenceMode.SPEAKING, state.mode)
    }

    @Test
    fun derive_whenLoading_returnsThinking() {
        val state = controller.derive(inputs(isLoading = true))

        assertEquals(PresenceMode.THINKING, state.mode)
    }

    @Test
    fun derive_whenSearchingMemory_returnsSearching() {
        val state = controller.derive(
            inputs(
                latestToolName = "search_memory",
                latestToolStatus = ToolCallStatus.STARTED,
            )
        )

        assertEquals(PresenceMode.SEARCHING, state.mode)
    }

    @Test
    fun derive_whenFormerMemoryWriteToolStarts_returnsThinking() {
        val state = controller.derive(
            inputs(
                latestToolName = "save_memory",
                latestToolStatus = ToolCallStatus.STARTED,
            )
        )

        assertEquals(PresenceMode.THINKING, state.mode)
    }

    @Test
    fun derive_whenHasPendingImage_returnsListening() {
        val state = controller.derive(inputs(hasPendingImage = true))

        assertEquals(PresenceMode.LISTENING, state.mode)
    }

    @Test
    fun derive_whenToolFails_returnsError() {
        val state = controller.derive(
            inputs(
                latestToolName = "save_memory",
                latestToolStatus = ToolCallStatus.FAILED,
            )
        )

        assertEquals(PresenceMode.ERROR, state.mode)
    }

    @Test
    fun derive_whenMoodIsSad_returnsSad() {
        val state = controller.derive(inputs(mood = "sad"))

        assertEquals(PresenceMode.SAD, state.mode)
    }

    @Test
    fun derive_whenReactionProvided_keepsReaction() {
        val state = controller.derive(inputs(reaction = PresenceReaction.TOUCH_NUZZLE))

        assertEquals(PresenceReaction.TOUCH_NUZZLE, state.reaction)
    }

    @Test
    fun reactionFor_whenUserTapped_returnsTouchNuzzle() {
        assertEquals(PresenceReaction.TOUCH_NUZZLE, controller.reactionFor(PresenceEvent.UserTapped))
    }

    @Test
    fun reactionFor_whenMemorySaved_returnsMemorySpark() {
        val reaction = controller.reactionFor(PresenceEvent.MemorySaved(1))

        assertEquals(PresenceReaction.MEMORY_SPARK, reaction)
    }

    private fun inputs(
        mood: String = "neutral",
        isLoading: Boolean = false,
        isStreaming: Boolean = false,
        latestToolName: String? = null,
        latestToolStatus: ToolCallStatus? = null,
        reaction: PresenceReaction? = null,
        hasError: Boolean = false,
        hasInputText: Boolean = false,
        hasPendingImage: Boolean = false,
    ) = PresenceInputs(
        mood = mood,
        intensity = 0.5f,
        relationshipLevel = 0.3f,
        isLoading = isLoading,
        isStreaming = isStreaming,
        latestToolName = latestToolName,
        latestToolStatus = latestToolStatus,
        reaction = reaction,
        hasError = hasError,
        hasInputText = hasInputText,
        hasPendingImage = hasPendingImage,
    )
}
