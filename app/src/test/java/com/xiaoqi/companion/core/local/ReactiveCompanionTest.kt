package com.xiaoqi.companion.core.local

import app.cash.turbine.test
import com.xiaoqi.companion.core.companion.KoogAgentEvent
import com.xiaoqi.companion.core.prompt.BuiltPrompt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
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
    fun runStreaming_routesThroughRunEvents() = runTest {
        val engine = FakeLocalQwenEngine(listOf("a", "b", "c"))
        val wrapper = ReactiveCompanion(engine)

        val collected = mutableListOf<String>()
        wrapper.runStreaming(BuiltPrompt(systemPrompt = "s", userMessage = "u")).collect {
            collected += it
        }

        assertEquals(listOf("a", "b", "c"), collected)
        assertEquals("s", engine.lastRequest?.systemPrompt)
        assertEquals("u", engine.lastRequest?.userMessage)
    }

    @Test
    fun runEvents_forwardsImageFieldsToLocalRequest() = runTest {
        val engine = FakeLocalQwenEngine(listOf("ok"))
        val wrapper = ReactiveCompanion(engine)

        wrapper.runEvents(
            BuiltPrompt(
                systemPrompt = "system",
                userMessage = "describe",
                hasImage = true,
                imageBase64 = "AAAA",
                imageMediaType = "image/png",
            )
        ).test {
            assertEquals(KoogAgentEvent.TextDelta("ok"), awaitItem())
            awaitComplete()
        }

        val req = engine.lastRequest
        assertEquals("AAAA", req?.imageBase64)
        assertEquals("image/png", req?.imageMediaType)
    }

    @Test
    fun runStructured_parsesJsonFromTextCompletion() = runTest {
        val engine = FakeLocalQwenEngine(
            listOf("""{"value":"parsed"}"""),
        )
        val wrapper = ReactiveCompanion(engine)

        val result = wrapper.runStructured(
            prompt = BuiltPrompt(systemPrompt = "s", userMessage = "u"),
            serializer = Dummy.serializer(),
            examples = emptyList(),
        )

        assertEquals("parsed", result.value)
    }

    @Test
    fun runStructured_stripsCodeFenceBeforeParsing() = runTest {
        val engine = FakeLocalQwenEngine(
            listOf("""```json
                {"value":"fenced"}
            ```"""),
        )
        val wrapper = ReactiveCompanion(engine)

        val result = wrapper.runStructured(
            prompt = BuiltPrompt(systemPrompt = "s", userMessage = "u"),
            serializer = Dummy.serializer(),
            examples = emptyList(),
        )

        assertEquals("fenced", result.value)
    }

    @Test
    fun runStructured_fallsBackToFirstExampleWhenJsonMissing() = runTest {
        val engine = FakeLocalQwenEngine(listOf("no json at all here"))
        val fallback = Dummy("fallback")
        val wrapper = ReactiveCompanion(engine)

        val result = wrapper.runStructured(
            prompt = BuiltPrompt(systemPrompt = "s", userMessage = "u"),
            serializer = Dummy.serializer(),
            examples = listOf(fallback),
        )

        assertEquals(fallback, result)
    }

    @Test(expected = IllegalStateException::class)
    fun runStructured_throwsWhenNoJsonAndNoFallback() = runTest {
        val engine = FakeLocalQwenEngine(listOf("still no json"))
        val wrapper = ReactiveCompanion(engine)

        wrapper.runStructured(
            prompt = BuiltPrompt(systemPrompt = "s", userMessage = "u"),
            serializer = Dummy.serializer(),
            examples = emptyList(),
        )
    }

    @Serializable
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
