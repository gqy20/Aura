package com.xiaoqi.companion.core.mcp

import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpToolAdapterTest {

    @Test
    fun remoteTool_exposesPrefixedDescriptorAndCallsOriginalToolName() = runTest {
        val client = RecordingMcpClient()
        val tool = McpRemoteTool(
            serverUrl = "https://mcp.example.com/mcp",
            serverName = "",
            spec = McpToolSpec(
                name = "web_search",
                description = "Search the web",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put(
                                "query",
                                buildJsonObject {
                                    put("type", "string")
                                    put("description", "Query text")
                                },
                            )
                        },
                    )
                    put("required", kotlinx.serialization.json.buildJsonArray { add(JsonPrimitive("query")) })
                },
            ),
            client = client,
        )

        val result = tool.execute(buildJsonObject { put("query", "android") })

        assertEquals("ok", result)
        assertTrue(tool.descriptor.name.startsWith("mcp__mcp_example_com__web_search"))
        assertEquals("web_search", client.calledToolName)
        assertEquals("android", client.arguments["query"]?.let { (it as JsonPrimitive).content })
        assertEquals(listOf("query"), tool.descriptor.requiredParameters.map { it.name })
    }

    @Test
    fun remoteTool_quotesLenientBareStringValuesBeforeSending() = runTest {
        val client = RecordingMcpClient()
        val tool = McpRemoteTool(
            serverUrl = "https://mcp.example.com/mcp",
            serverName = "map",
            spec = McpToolSpec(
                name = "maps_geo",
                description = "Geocode an address",
                inputSchema = buildJsonObject { put("type", "object") },
            ),
            client = client,
        )
        val lenientArgs = Json { isLenient = true }
            .parseToJsonElement("""{"address":西湖,"city":杭州}""")
            .jsonObject

        tool.execute(lenientArgs)

        assertEquals("西湖", client.arguments.getValue("address").let { (it as JsonPrimitive).content })
        assertEquals("杭州", client.arguments.getValue("city").let { (it as JsonPrimitive).content })
        assertEquals("""{"address":"西湖","city":"杭州"}""", client.arguments.toString())
    }

    @Test
    fun readOnlyRemoteTool_retriesTransientFailureInsideSingleExecution() = runTest {
        val client = FlakyMcpClient(
            failures = ArrayDeque(
                listOf(
                    IOException("timeout"),
                    McpHttpException(statusCode = 503),
                )
            )
        )
        val tool = remoteTool(name = "web_search", client = client)

        val retryAttempts = mutableListOf<Int>()
        val result = withContext(
            McpRetryProgressContext { retryAttempts += it.nextAttempt }
        ) {
            tool.execute(buildJsonObject {})
        }

        assertEquals("ok", result)
        assertEquals(3, client.callCount)
        assertEquals(listOf(2, 3), retryAttempts)
    }

    @Test
    fun readOnlyRemoteTool_doesNotRetryPermanentClientFailure() = runTest {
        val client = FlakyMcpClient(
            failures = ArrayDeque(listOf(McpHttpException(statusCode = 400)))
        )
        val tool = remoteTool(name = "web_search", client = client)

        val error = runCatching { tool.execute(buildJsonObject {}) }.exceptionOrNull()

        assertTrue(error is McpHttpException)
        assertEquals(1, client.callCount)
    }

    @Test
    fun writeRemoteTool_doesNotRetryWithoutIdempotencyGuarantee() = runTest {
        val client = FlakyMcpClient(failures = ArrayDeque(listOf(IOException("timeout"))))
        val tool = remoteTool(name = "send_message", client = client)

        val error = runCatching { tool.execute(buildJsonObject {}) }.exceptionOrNull()

        assertTrue(error is IOException)
        assertEquals(1, client.callCount)
    }

    private fun remoteTool(name: String, client: RemoteMcpClient) = McpRemoteTool(
        serverUrl = "https://mcp.example.com/mcp",
        serverName = "test",
        spec = McpToolSpec(
            name = name,
            description = name,
            inputSchema = buildJsonObject { put("type", "object") },
        ),
        client = client,
    )

    private class RecordingMcpClient : RemoteMcpClient {
        lateinit var calledToolName: String
        lateinit var arguments: JsonObject

        override suspend fun listTools(serverUrl: String, headers: Map<String, String>): List<McpToolSpec> = emptyList()

        override suspend fun callTool(serverUrl: String, toolName: String, arguments: JsonObject, headers: Map<String, String>): String {
            calledToolName = toolName
            this.arguments = arguments
            return "ok"
        }

        override suspend fun probe(serverUrl: String, headers: Map<String, String>): List<McpToolSpec> = emptyList()
    }

    private class FlakyMcpClient(
        private val failures: ArrayDeque<Exception>,
    ) : RemoteMcpClient {
        var callCount = 0

        override suspend fun listTools(serverUrl: String, headers: Map<String, String>): List<McpToolSpec> = emptyList()

        override suspend fun callTool(
            serverUrl: String,
            toolName: String,
            arguments: JsonObject,
            headers: Map<String, String>,
        ): String {
            callCount += 1
            failures.removeFirstOrNull()?.let { throw it }
            return "ok"
        }

        override suspend fun probe(serverUrl: String, headers: Map<String, String>): List<McpToolSpec> = emptyList()
    }
}
