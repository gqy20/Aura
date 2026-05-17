package com.xiaoqi.companion.core.mcp

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class McpHttpClientTest {

    private lateinit var server: HttpServer
    private lateinit var serverUrl: String
    private val requests = CopyOnWriteArrayList<String>()
    private val sessionHeaders = CopyOnWriteArrayList<String?>()

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/mcp") { exchange ->
            val body = exchange.requestBody.bufferedReader().readText()
            requests += body
            sessionHeaders += exchange.requestHeaders.getFirst("Mcp-Session-Id")

            val response = when {
                body.contains("\"method\":\"initialize\"") -> {
                    exchange.responseHeaders.add("Mcp-Session-Id", "session-1")
                    """{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-06-18","capabilities":{},"serverInfo":{"name":"test","version":"1"}}}"""
                }
                body.contains("\"method\":\"notifications/initialized\"") ->
                    """{"jsonrpc":"2.0","result":{}}"""
                body.contains("\"method\":\"tools/list\"") ->
                    """{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"echo","description":"Echo remote text","inputSchema":{"type":"object","properties":{"text":{"type":"string","description":"Text to echo"}},"required":["text"]}}]}}"""
                body.contains("\"method\":\"tools/call\"") ->
                    """{"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"remote: hello"}]}}"""
                else ->
                    """{"jsonrpc":"2.0","error":{"code":-32601,"message":"unknown method"}}"""
            }
            exchange.respondJson(response)
        }
        server.start()
        serverUrl = "http://127.0.0.1:${server.address.port}/mcp"
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun listTools_initializesAndReadsToolSchemas() = runTest {
        val client = McpHttpClient()

        val tools = client.listTools(serverUrl)

        assertEquals(1, tools.size)
        assertEquals("echo", tools.single().name)
        assertEquals("Echo remote text", tools.single().description)
        assertTrue(requests.any { it.contains("\"method\":\"initialize\"") })
        assertTrue(requests.any { it.contains("\"method\":\"tools/list\"") })
        assertTrue(sessionHeaders.any { it == "session-1" })
    }

    @Test
    fun callTool_returnsTextContent() = runTest {
        val client = McpHttpClient()

        val result = client.callTool(
            serverUrl = serverUrl,
            toolName = "echo",
            arguments = kotlinx.serialization.json.buildJsonObject {
                put("text", kotlinx.serialization.json.JsonPrimitive("hello"))
            },
        )

        assertEquals("remote: hello", result)
        assertTrue(requests.any { it.contains("\"method\":\"tools/call\"") })
        assertTrue(requests.any { it.contains("\"name\":\"echo\"") })
    }

    private fun HttpExchange.respondJson(response: String) {
        val bytes = response.toByteArray()
        responseHeaders.add("content-type", "application/json")
        sendResponseHeaders(200, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }
}
