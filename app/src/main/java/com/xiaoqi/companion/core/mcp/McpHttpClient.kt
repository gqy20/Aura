package com.xiaoqi.companion.core.mcp

import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

data class McpToolSpec(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
)

interface RemoteMcpClient {
    suspend fun listTools(serverUrl: String): List<McpToolSpec>
    suspend fun callTool(serverUrl: String, toolName: String, arguments: JsonObject): String
}

@Singleton
class McpHttpClient @Inject constructor() : RemoteMcpClient {

    private val httpClient: OkHttpClient =
        OkHttpClient.Builder()
            .callTimeout(45, TimeUnit.SECONDS)
            .build()
    private val sessions = mutableMapOf<String, String>()
    private val toolCache = mutableMapOf<String, List<McpToolSpec>>()
    private val ids = AtomicLong(1)

    override suspend fun listTools(serverUrl: String): List<McpToolSpec> =
        withContext(Dispatchers.IO) {
            if (serverUrl.isBlank()) return@withContext emptyList()
            toolCache[serverUrl]?.let { return@withContext it }
            ensureInitialized(serverUrl)
            val response = rpc(
                serverUrl = serverUrl,
                method = "tools/list",
                params = buildJsonObject {},
            )
            response.resultObject()["tools"]?.jsonArray
                ?.mapNotNull { element -> element.jsonObject.toToolSpecOrNull() }
                .orEmpty()
                .also { toolCache[serverUrl] = it }
        }

    override suspend fun callTool(serverUrl: String, toolName: String, arguments: JsonObject): String =
        withContext(Dispatchers.IO) {
            ensureInitialized(serverUrl)
            val response = rpc(
                serverUrl = serverUrl,
                method = "tools/call",
                params = buildJsonObject {
                    put("name", toolName)
                    put("arguments", arguments)
                },
            )
            response.resultObject().toToolResultString()
        }

    private fun ensureInitialized(serverUrl: String) {
        if (sessions.containsKey(serverUrl)) return

        val response = postJson(
            serverUrl = serverUrl,
            payload = buildRequest(
                method = "initialize",
                params = buildJsonObject {
                    put("protocolVersion", MCP_PROTOCOL_VERSION)
                    put("capabilities", buildJsonObject {})
                    put(
                        "clientInfo",
                        buildJsonObject {
                            put("name", "Aura Android")
                            put("version", "0.1.1")
                        },
                    )
                },
            ),
            includeSession = false,
        )
        response.sessionId?.let { sessions[serverUrl] = it }
        response.json.throwIfJsonRpcError()

        runCatching {
            postJson(
                serverUrl = serverUrl,
                payload = buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("method", "notifications/initialized")
                },
                includeSession = true,
            )
        }.onFailure {
            AppLogger.debug(
                LogTags.Llm,
                "mcp_initialized_notification_failed",
                "message" to (it.message ?: it::class.simpleName.orEmpty()),
            )
        }
    }

    private fun rpc(serverUrl: String, method: String, params: JsonObject): JsonObject {
        val response = postJson(
            serverUrl = serverUrl,
            payload = buildRequest(method = method, params = params),
            includeSession = true,
        )
        response.sessionId?.let { sessions[serverUrl] = it }
        response.json.throwIfJsonRpcError()
        return response.json
    }

    private fun postJson(serverUrl: String, payload: JsonObject, includeSession: Boolean): McpHttpResponse {
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val requestBuilder = Request.Builder()
            .url(serverUrl)
            .header("content-type", "application/json")
            .header("accept", "application/json, text/event-stream")
            .header("MCP-Protocol-Version", MCP_PROTOCOL_VERSION)
            .post(body)
        if (includeSession) {
            sessions[serverUrl]?.let { requestBuilder.header("Mcp-Session-Id", it) }
        }

        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            val rawBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw RuntimeException("MCP HTTP ${response.code}: ${rawBody.take(300)}")
            }
            return McpHttpResponse(
                json = parseHttpBody(rawBody),
                sessionId = response.header("Mcp-Session-Id"),
            )
        }
    }

    private fun buildRequest(method: String, params: JsonObject): JsonObject =
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", ids.getAndIncrement())
            put("method", method)
            put("params", params)
        }

    private fun parseHttpBody(body: String): JsonObject {
        val trimmed = body.trim()
        val jsonText = if (trimmed.startsWith("data:")) {
            trimmed.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.startsWith("data:") }
                ?.removePrefix("data:")
                ?.trim()
                .orEmpty()
        } else {
            trimmed
        }
        return json.parseToJsonElement(jsonText).jsonObject
    }

    private fun JsonObject.resultObject(): JsonObject =
        (this["result"] as? JsonObject) ?: buildJsonObject {}

    private fun JsonObject.throwIfJsonRpcError() {
        val error = this["error"] as? JsonObject ?: return
        val message = error["message"]?.jsonPrimitive?.contentOrNull ?: error.toString()
        throw RuntimeException("MCP error: $message")
    }

    private fun JsonObject.toToolSpecOrNull(): McpToolSpec? {
        val name = this["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        val description = this["description"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val schema = this["inputSchema"] as? JsonObject ?: buildJsonObject { put("type", "object") }
        return McpToolSpec(name = name, description = description, inputSchema = schema)
    }

    private fun JsonObject.toToolResultString(): String {
        val content = this["content"] as? JsonArray
        val text = content
            ?.mapNotNull { block ->
                val obj = block as? JsonObject ?: return@mapNotNull null
                if (obj["type"]?.jsonPrimitive?.contentOrNull == "text") {
                    obj["text"]?.jsonPrimitive?.contentOrNull
                } else {
                    obj.toString()
                }
            }
            ?.joinToString("\n")
            ?.takeIf { it.isNotBlank() }
        if (text != null) return text

        val structured = this["structuredContent"]?.takeUnless { it is JsonNull }
        if (structured != null) return structured.toString()
        return this.toString()
    }

    private data class McpHttpResponse(
        val json: JsonObject,
        val sessionId: String?,
    )

    private companion object {
        const val MCP_PROTOCOL_VERSION = "2025-06-18"
        val json = Json { ignoreUnknownKeys = true }
    }
}
