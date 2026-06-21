package com.xiaoqi.companion.core.local

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.serialization.typeToken
import app.cash.turbine.test
import com.xiaoqi.companion.core.companion.KoogAgentEvent
import com.xiaoqi.companion.core.companion.model.ToolCallStatus
import com.xiaoqi.companion.core.mcp.McpRemoteTool
import com.xiaoqi.companion.core.mcp.McpToolSpec
import com.xiaoqi.companion.core.mcp.RemoteMcpClient
import com.xiaoqi.companion.core.prompt.BuiltPrompt
import com.xiaoqi.companion.testing.FakeLocalQwenEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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

    @Test
    fun runEvents_whenSecondRoundStillNeedsTool_continuesUntilNaturalAnswer() = runTest {
        val engine = SequencedLocalQwenEngine(
            responses = listOf(
                listOf("""{"tool_calls":[{"name":"test_note","arguments":{"content":"first"}}]}"""),
                listOf("""{"tool_calls":[{"name":"test_note","arguments":{"content":"second"}}]}"""),
                listOf("done after two tools"),
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
                userMessage = "remember more",
                allowTools = true,
            )
        ).collect { events += it }

        val started = events.mapNotNull { (it as? KoogAgentEvent.ToolCallUpdated)?.call }
            .count { it.status == ToolCallStatus.STARTED && it.name == "test_note" }
        val succeeded = events.mapNotNull { (it as? KoogAgentEvent.ToolCallUpdated)?.call }
            .count { it.status == ToolCallStatus.SUCCEEDED && it.name == "test_note" }
        assertEquals(2, started)
        assertEquals(2, succeeded)
        assertTrue(events.contains(KoogAgentEvent.TextDelta("done after two tools")))
        assertEquals(3, engine.requests.size)
        assertTrue(engine.requests[1].userMessage.contains("tool_results"))
    }

    @Test
    fun runEvents_whenToolRoundsReachLimit_emitsFallbackText() = runTest {
        val engine = SequencedLocalQwenEngine(
            responses = List(4) {
                listOf("""{"tool_calls":[{"name":"test_note","arguments":{"content":"loop-$it"}}]}""")
            }
        )
        val wrapper = ReactiveCompanion(
            engine = engine,
            toolRegistry = ToolRegistry.builder().tool(TestNoteTool()).build(),
        )

        val events = mutableListOf<KoogAgentEvent>()
        wrapper.runEvents(
            BuiltPrompt(
                systemPrompt = "system",
                userMessage = "loop forever",
                allowTools = true,
            )
        ).collect { events += it }

        assertTrue(
            events.last() == KoogAgentEvent.TextDelta(
                "我已经拿到部分工具结果，但本地工具调用轮次已到上限。你可以换个问法，或减少一次请求里的任务数。"
            )
        )
        assertEquals(4, engine.requests.size)
    }

    @Test
    fun localToolExecutor_executesRemoteMcpToolThroughAdapter() = runTest {
        val client = RecordingMcpClient()
        val tool = McpRemoteTool(
            serverUrl = "https://mcp.example.com/mcp",
            serverName = "example",
            spec = McpToolSpec(
                name = "search",
                description = "Search docs",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put(
                                "query",
                                buildJsonObject {
                                    put("type", "string")
                                },
                            )
                        },
                    )
                    put("required", kotlinx.serialization.json.buildJsonArray {
                        add(kotlinx.serialization.json.JsonPrimitive("query"))
                    })
                },
            ),
            client = client,
        )
        val registry = ToolRegistry.builder().tool(tool).build()
        val executor = LocalToolExecutor(registry, recorder = null, sessionId = "default")

        val result = executor.execute(
            listOf(
                LocalToolCall(
                    name = tool.descriptor.name,
                    arguments = buildJsonObject { put("query", "android") },
                )
            )
        )

        assertEquals("search", client.calledToolName)
        assertEquals("android", client.arguments?.get("query")?.toString()?.trim('"'))
        assertTrue(result.events.any { (it as? KoogAgentEvent.ToolCallUpdated)?.call?.status == ToolCallStatus.SUCCEEDED })
        assertTrue(result.transcripts.first().result.contains("mcp-result"))
    }

    @Test
    fun localToolExecutor_marksMissingToolAsFailedTranscript() = runTest {
        val executor = LocalToolExecutor(ToolRegistry.EMPTY, recorder = null, sessionId = "default")

        val result = executor.execute(
            listOf(
                LocalToolCall(
                    name = "missing_tool",
                    arguments = buildJsonObject { put("value", "x") },
                )
            )
        )

        assertTrue(result.events.any {
            val call = (it as? KoogAgentEvent.ToolCallUpdated)?.call
            call?.name == "missing_tool" && call.status == ToolCallStatus.FAILED
        })
        assertTrue(result.transcripts.first().isError)
    }

    private class RecordingMcpClient : RemoteMcpClient {
        var calledToolName: String? = null
        var arguments: kotlinx.serialization.json.JsonObject? = null

        override suspend fun listTools(serverUrl: String, headers: Map<String, String>): List<McpToolSpec> = emptyList()

        override suspend fun callTool(serverUrl: String, toolName: String, arguments: kotlinx.serialization.json.JsonObject, headers: Map<String, String>): String {
            calledToolName = toolName
            this.arguments = arguments
            return """{"content":[{"type":"text","text":"mcp-result"}]}"""
        }

        override suspend fun probe(serverUrl: String, headers: Map<String, String>): List<McpToolSpec> = emptyList()
    }

    @Serializable
    private data class Dummy(val value: String = "")

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
