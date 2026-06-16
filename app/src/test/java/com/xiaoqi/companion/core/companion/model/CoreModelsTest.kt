package com.xiaoqi.companion.core.companion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun agentEvent_complete_hasTextReply() {
        val event = AgentEvent.Complete(textReply = "回复内容")
        assertEquals("回复内容", event.textReply)
    }

    @Test
    fun agentEvent_error_hasMessage() {
        val event = AgentError.NetworkTimeout
        assertTrue(event is AgentError)
    }
}
