package com.xiaoqi.companion.core.llm

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import kotlin.time.Clock
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AnthropicMessagesLLMClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun execute_serializesToolCallAndResultWithAnthropicProtocol() = runTest {
        server.enqueue(successResponse("done"))
        val client = client()
        val now = Clock.System.now()
        val requestPrompt = prompt("tool-followup") {
            system("system")
            user("What time is it?")
            message(
                Message.Tool.Call(
                    id = "call-1",
                    tool = "get_current_time",
                    content = "{\"timezone\":\"Asia/Shanghai\"}",
                    metaInfo = ResponseMetaInfo(now),
                )
            )
            message(
                Message.Tool.Result(
                    id = "call-1",
                    tool = "get_current_time",
                    content = "{\"time\":\"10:00\"}",
                    metaInfo = RequestMetaInfo(now),
                    isError = true,
                )
            )
            user("Answer using the tool result.")
        }

        client.execute(requestPrompt, model, emptyList())

        val recorded = server.takeRequest()
        val body = json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        val messages = body.getValue("messages").jsonArray
        val toolCall = messages[1].jsonObject
        val toolCallBlock = toolCall.getValue("content").jsonArray.single().jsonObject
        val toolResult = messages[2].jsonObject
        val toolResultBlock = toolResult.getValue("content").jsonArray.single().jsonObject

        assertEquals("/v1/messages", recorded.path)
        assertEquals("assistant", toolCall.getValue("role").jsonPrimitive.content)
        assertEquals("tool_use", toolCallBlock.getValue("type").jsonPrimitive.content)
        assertEquals("call-1", toolCallBlock.getValue("id").jsonPrimitive.content)
        assertEquals(
            "Asia/Shanghai",
            toolCallBlock.getValue("input").jsonObject.getValue("timezone").jsonPrimitive.content,
        )
        assertEquals("user", toolResult.getValue("role").jsonPrimitive.content)
        assertEquals("tool_result", toolResultBlock.getValue("type").jsonPrimitive.content)
        assertEquals("call-1", toolResultBlock.getValue("tool_use_id").jsonPrimitive.content)
        assertTrue(toolResultBlock.getValue("is_error").jsonPrimitive.boolean)
    }

    @Test
    fun execute_retriesTransientHttpFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("temporary"))
        server.enqueue(successResponse("recovered"))

        val responses = client().execute(simplePrompt(), model, emptyList())

        assertEquals("recovered", responses.single().content)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun execute_doesNotRetryNonTransientHttpFailure() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("""{"error":"bad request shape","api_key":"secret-value"}""")
        )

        val failure = runCatching {
            client().execute(simplePrompt(), model, emptyList())
        }.exceptionOrNull()

        assertTrue(failure?.message?.contains("HTTP 400") == true)
        assertTrue(failure?.message?.contains("bad request shape") == true)
        assertFalse(failure?.message?.contains("secret-value") == true)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun executeStreaming_retriesBeforeAnyFrameIsEmitted() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("busy"))
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    listOf(
                        "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"OK\"}}",
                        "",
                        "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"}}",
                        "",
                        "data: {\"type\":\"message_stop\"}",
                        "",
                    ).joinToString("\n")
                )
        )

        val frames = client().executeStreaming(simplePrompt(), model, emptyList()).toList()

        assertTrue(frames.any { it is StreamFrame.TextDelta && it.text == "OK" })
        assertTrue(frames.any { it is StreamFrame.End })
        assertEquals(2, server.requestCount)
    }

    @Test
    fun executeStreaming_accumulatesInterleavedToolsByContentBlockIndex() = runTest {
        server.enqueue(
            sseResponse(
                """{"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"map-1","name":"maps_around_search"}}""",
                """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\"query\":"}}""",
                """{"type":"content_block_start","index":2,"content_block":{"type":"tool_use","id":"memory-1","name":"search_records"}}""",
                """{"type":"content_block_delta","index":2,"delta":{"type":"input_json_delta","partial_json":"{\"query\":睡眠,"}}""",
                """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"\"咖啡\"}"}}""",
                """{"type":"content_block_delta","index":2,"delta":{"type":"input_json_delta","partial_json":"\"limit\":10}"}}""",
                """{"type":"content_block_stop","index":2}""",
                """{"type":"content_block_stop","index":1}""",
                """{"type":"message_delta","delta":{"stop_reason":"tool_use"}}""",
                """{"type":"message_stop"}""",
            )
        )

        val frames = client().executeStreaming(simplePrompt(), model, emptyList()).toList()
        val calls = frames.filterIsInstance<StreamFrame.ToolCallComplete>()

        assertEquals(listOf("search_records", "maps_around_search"), calls.map { it.name })
        assertEquals(listOf(2, 1), calls.map { it.index })
        assertEquals("睡眠", json.parseToJsonElement(calls[0].content).jsonObject.getValue("query").jsonPrimitive.content)
        assertEquals(10, json.parseToJsonElement(calls[0].content).jsonObject.getValue("limit").jsonPrimitive.content.toInt())
        assertEquals("咖啡", json.parseToJsonElement(calls[1].content).jsonObject.getValue("query").jsonPrimitive.content)
    }

    @Test
    fun executeStreaming_doesNotCompleteToolWhenAnotherContentBlockStops() = runTest {
        server.enqueue(
            sseResponse(
                """{"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"map-1","name":"maps_around_search"}}""",
                """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\"query\":\"咖啡\"}"}}""",
                """{"type":"content_block_stop","index":0}""",
                """{"type":"message_delta","delta":{"stop_reason":"tool_use"}}""",
                """{"type":"message_stop"}""",
            )
        )

        val failure = runCatching {
            client().executeStreaming(simplePrompt(), model, emptyList()).toList()
        }.exceptionOrNull()

        assertTrue(failure?.message?.contains("incomplete tool calls") == true)
    }

    @Test
    fun executeStreaming_supportsSingleToolProviderWithoutIndexes() = runTest {
        server.enqueue(
            sseResponse(
                """{"type":"content_block_start","content_block":{"type":"tool_use","id":"map-1","name":"maps_around_search"}}""",
                """{"type":"content_block_delta","delta":{"type":"input_json_delta","partial_json":"{\"query\":\"咖啡\"}"}}""",
                """{"type":"content_block_stop"}""",
                """{"type":"message_delta","delta":{"stop_reason":"tool_use"}}""",
                """{"type":"message_stop"}""",
            )
        )

        val calls = client().executeStreaming(simplePrompt(), model, emptyList())
            .toList()
            .filterIsInstance<StreamFrame.ToolCallComplete>()

        assertEquals(1, calls.size)
        assertEquals("maps_around_search", calls.single().name)
        assertEquals(-1, calls.single().index)
    }

    @Test
    fun executeStreaming_repairsBareStringValuesWithoutRegexLookahead() = runTest {
        server.enqueue(
            sseResponse(
                """{"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"state-1","name":"update_state"}}""",
                """{"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"{\"mood\":calm,\"reason\":needs rest}"}}""",
                """{"type":"content_block_stop","index":0}""",
                """{"type":"message_delta","delta":{"stop_reason":"tool_use"}}""",
                """{"type":"message_stop"}""",
            )
        )

        val call = client().executeStreaming(simplePrompt(), model, emptyList())
            .toList()
            .filterIsInstance<StreamFrame.ToolCallComplete>()
            .single()
        val input = json.parseToJsonElement(call.content).jsonObject

        assertTrue(call.content.contains("\"mood\":\"calm\""))
        assertTrue(call.content.contains("\"reason\":\"needs rest\""))
        assertEquals("calm", input.getValue("mood").jsonPrimitive.content)
        assertEquals("needs rest", input.getValue("reason").jsonPrimitive.content)
    }

    private fun client() = AnthropicMessagesLLMClient(
        apiKey = "test-key",
        baseUrl = server.url("/").toString().trimEnd('/'),
        httpClient = serverClient,
        retryPolicy = LlmRetryPolicy(
            maxAttempts = 3,
            initialDelayMs = 0,
            maxDelayMs = 0,
        ),
    )

    private fun simplePrompt() = prompt("simple") {
        system("system")
        user("hello")
    }

    private fun successResponse(text: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(
            """{"type":"message","role":"assistant","content":[{"type":"text","text":"$text"}],"stop_reason":"end_turn","usage":{"input_tokens":1,"output_tokens":1}}"""
        )

    private fun sseResponse(vararg events: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "text/event-stream")
        .setBody(events.joinToString("\n\n", postfix = "\n\n") { "data: $it" })

    private val serverClient = okhttp3.OkHttpClient.Builder().build()

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val model = LLModel(
            provider = LLMProvider.Anthropic,
            id = "test-model",
            capabilities = listOf(LLMCapability.Completion),
        )
    }
}
