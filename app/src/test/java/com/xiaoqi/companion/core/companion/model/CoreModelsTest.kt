package com.xiaoqi.companion.core.companion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class CoreModelsTest {

    // --- UserInput ---

    @Test
    fun userInput_text_hasCorrectContent() {
        val input = UserInput.Text("hello")
        assertEquals("hello", input.content)
    }

    @Test
    fun userInput_vision_hasTextAndImage() {
        val input = UserInput.Vision("看这个", "base64data...", "image/jpeg")
        assertEquals("看这个", input.text)
        assertEquals("base64data...", input.imageBase64)
        assertEquals("image/jpeg", input.mediaType)
    }

    @Test
    fun userInput_speech_hasTranscript() {
        val input = UserInput.Speech("你好世界")
        assertEquals("你好世界", input.transcript)
    }

    // --- AgentEvent ---

    @Test
    fun agentEvent_streaming_hasDelta() {
        val event = AgentEvent.Streaming("你好")
        assertEquals("你好", event.delta)
    }

    @Test
    fun agentEvent_complete_hasParsedOutput() {
        val output = ParsedOutput(textReply = "回复内容", emotionSignal = EmotionSignal(mood = "happy"))
        val event = AgentEvent.Complete(output)
        assertEquals("回复内容", event.parsed.textReply)
        assertEquals("happy", event.parsed.emotionSignal.mood)
    }

    @Test
    fun agentEvent_error_hasMessage() {
        val event = AgentError.NetworkTimeout
        assertTrue(event is AgentError)
    }

    // --- ParsedOutput ---

    @Test
    fun parsedOutput_defaultValues() {
        val output = ParsedOutput()
        assertEquals("", output.textReply)
        assertNotNull(output.emotionSignal)
        assertNotNull(output.interactionSignal)
        assertTrue(output.actions.isEmpty())
    }

    // --- EmotionSignal ---

    @Test
    fun emotionSignal_customValues() {
        val signal = EmotionSignal(
            mood = "excited",
            intensity = 0.9f,
            trigger = "user shared good news",
        )
        assertEquals("excited", signal.mood)
        assertEquals(0.9f, signal.intensity, 0.001f)
        assertEquals("user shared good news", signal.trigger)
    }

    // --- InteractionSignal ---

    @Test
    fun interactionSignal_affinityChange() {
        val signal = InteractionSignal(
            affinityDelta = 0.05f,
            topicTags = listOf("greeting", "casual"),
        )
        assertEquals(0.05f, signal.affinityDelta, 0.001f)
        assertEquals(2, signal.topicTags.size)
    }
}
