package com.xiaoqi.companion.core.local

import app.cash.turbine.test
import com.xiaoqi.companion.core.companion.KoogAgentEvent
import com.xiaoqi.companion.core.prompt.BuiltPrompt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactiveCompanionTest {

    @Test
    fun runEvents_streamsLocalEngineTokensAsKoogTextDeltas() = runTest {
        val engine = FakeLocalQwenEngine(listOf("hello", " ", "local"))
        val wrapper = ReactiveCompanion(engine)

        wrapper.runEvents(
            BuiltPrompt(
                systemPrompt = "system persona",
                userMessage = "hi",
                allowTools = true,
            )
        ).test {
            assertEquals(KoogAgentEvent.TextDelta("hello"), awaitItem())
            assertEquals(KoogAgentEvent.TextDelta(" "), awaitItem())
            assertEquals(KoogAgentEvent.TextDelta("local"), awaitItem())
            awaitComplete()
        }

        assertEquals("system persona", engine.lastRequest?.systemPrompt)
        assertEquals("hi", engine.lastRequest?.userMessage)
        assertFalse(engine.lastRequest?.allowTools ?: true)
    }

    @Test
    fun run_collectsLocalEngineTokensIntoCompleteText() = runTest {
        val engine = FakeLocalQwenEngine(listOf("hello", " ", "local"))
        val wrapper = ReactiveCompanion(engine)

        val text = wrapper.run(BuiltPrompt(systemPrompt = "system", userMessage = "hi"))

        assertEquals("hello local", text)
    }

    @Test
    fun runEvents_rejectsVisionPromptsForFirstTextOnlyMvp() = runTest {
        val engine = FakeLocalQwenEngine(listOf("unused"))
        val wrapper = ReactiveCompanion(engine)

        wrapper.runEvents(
            BuiltPrompt(
                systemPrompt = "system",
                userMessage = "describe",
                hasImage = true,
                imageBase64 = "base64",
            )
        ).test {
            val error = awaitError()
            assertTrue(error is UnsupportedOperationException)
            assertTrue(error.message.orEmpty().contains("Vision"))
        }
    }

    @Test(expected = UnsupportedOperationException::class)
    fun runStructured_isDisabledForLocalTextMvp() = runTest {
        val wrapper = ReactiveCompanion(FakeLocalQwenEngine(listOf("unused")))

        wrapper.runStructured(
            prompt = BuiltPrompt(systemPrompt = "system", userMessage = "json"),
            serializer = Dummy.serializer(),
            examples = emptyList(),
        )
    }

    @kotlinx.serialization.Serializable
    private data class Dummy(val value: String = "")

    private class FakeLocalQwenEngine(
        private val chunks: List<String>,
    ) : LocalQwenEngine {
        var lastRequest: LocalQwenRequest? = null

        override fun stream(request: LocalQwenRequest): Flow<String> = flow {
            lastRequest = request
            chunks.forEach { emit(it) }
        }
    }
}
