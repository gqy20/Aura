package com.xiaoqi.companion.core.local

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.serialization.typeToken
import app.cash.turbine.test
import com.xiaoqi.companion.core.companion.KoogAgentEvent
import com.xiaoqi.companion.core.companion.model.ToolCallStatus
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
        assertTrue(engine.lastRequest?.allowTools ?: false)
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

    @Test
    fun runEvents_whenLocalModelReturnsToolCall_executesToolAndStreamsSecondPass() = runTest {
        val engine = SequencedLocalQwenEngine(
            responses = listOf(
                listOf("""{"tool_calls":[{"name":"test_note","arguments":{"content":"User likes jasmine tea"}}]}"""),
                listOf("remembered locally"),
            )
        )
        val wrapper = ReactiveCompanion(
            engine = engine,
            toolRegistry = ToolRegistry.builder().tool(TestNoteTool()).build(),
        )

        val events = mutableListOf<KoogAgentEvent>()
        wrapper.runEvents(
            BuiltPrompt(
                systemPrompt = "system",
                userMessage = "remember this",
                allowTools = true,
            )
        ).collect { events += it }

        assertTrue(events.any {
            val call = (it as? KoogAgentEvent.ToolCallUpdated)?.call
            call?.name == "test_note" && call.status == ToolCallStatus.STARTED
        })
        assertTrue(events.any {
            val call = (it as? KoogAgentEvent.ToolCallUpdated)?.call
            call?.name == "test_note" &&
                call.status == ToolCallStatus.SUCCEEDED &&
                call.resultJson?.contains("noted") == true
        })
        assertTrue(events.contains(KoogAgentEvent.TextDelta("remembered locally")))
        assertEquals(2, engine.requests.size)
        assertTrue(engine.requests.first().systemPrompt.contains("You may call tools"))
        assertTrue(engine.requests.last().userMessage.contains("tool_results"))
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

    private class SequencedLocalQwenEngine(
        private val responses: List<List<String>>,
    ) : LocalQwenEngine {
        val requests = mutableListOf<LocalQwenRequest>()

        override fun stream(request: LocalQwenRequest): Flow<String> = flow {
            requests += request
            responses[requests.lastIndex].forEach { emit(it) }
        }
    }

    private class TestNoteTool : SimpleTool<TestNoteTool.Args>(
        typeToken<Args>(),
        name = "test_note",
        description = "Test-only note tool.",
    ) {
        @Serializable
        data class Args(val content: String = "")

        override suspend fun execute(args: Args): String =
            """{"status":"noted","content":"${args.content}"}"""
    }
}
