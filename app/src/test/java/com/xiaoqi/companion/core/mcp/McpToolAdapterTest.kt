package com.xiaoqi.companion.core.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.test.runTest
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
}
