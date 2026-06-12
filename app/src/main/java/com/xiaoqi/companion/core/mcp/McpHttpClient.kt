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
import okhttp3.Response
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
            val startedAt = System.currentTimeMillis()
            toolCache[serverUrl]?.let {
                AppLogger.debug(LogTags.Tools, "mcp_list_tools_cache_hit", "toolCount" to it.size)
                return@withContext it
            }
            AppLogger.info(LogTags.Tools, "mcp_list_tools_started", "serverHost" to serverUrl.hostForLog())
            try {
                ensureInitialized(serverUrl)
                val requestId = ids.getAndIncrement()
                val response = rpc(
                    serverUrl = serverUrl,
                    requestId = requestId,
                    method = "tools/list",
                    params = buildJsonObject {},
                )
                response.resultObject()["tools"]?.jsonArray
                    ?.mapNotNull { element -> element.jsonObject.toToolSpecOrNull() }
                    .orEmpty()
                    .also {
                        toolCache[serverUrl] = it
                        AppLogger.info(
                            LogTags.Tools,
                            "mcp_list_tools_completed",
                            "serverHost" to serverUrl.hostForLog(),
                            "toolCount" to it.size,
                            "durationMs" to (System.currentTimeMillis() - startedAt),
                        )
                    }
            } catch (e: Exception) {
                AppLogger.error(
                    LogTags.Tools,
                    e,
                    "mcp_list_tools_failed",
                    "serverHost" to serverUrl.hostForLog(),
                    "durationMs" to (System.currentTimeMillis() - startedAt),
                )
                throw e
            }
        }

    override suspend fun callTool(serverUrl: String, toolName: String, arguments: JsonObject): String =
        withContext(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            AppLogger.info(
                LogTags.Tools,
                "mcp_call_tool_started",
                "serverHost" to serverUrl.hostForLog(),
                "toolName" to toolName,
                "argumentLength" to arguments.toString().length,
            )
            try {
                ensureInitialized(serverUrl)
                val requestId = ids.getAndIncrement()
                val response = rpc(
                    serverUrl = serverUrl,
                    requestId = requestId,
                    method = "tools/call",
                    params = buildJsonObject {
                        put("name", toolName)
                        put("arguments", arguments)
                    },
                )
                response.resultObject().toToolResultString().also {
                    AppLogger.info(
                        LogTags.Tools,
                        "mcp_call_tool_completed",
                        "serverHost" to serverUrl.hostForLog(),
                        "toolName" to toolName,
                        "resultLength" to it.length,
                        "durationMs" to (System.currentTimeMillis() - startedAt),
                    )
                }
            } catch (e: Exception) {
                AppLogger.error(
                    LogTags.Tools,
                    e,
                    "mcp_call_tool_failed",
                    "serverHost" to serverUrl.hostForLog(),
                    "toolName" to toolName,
                    "durationMs" to (System.currentTimeMillis() - startedAt),
                )
                throw e
            }
        }

    private fun ensureInitialized(serverUrl: String) {
        if (sessions.containsKey(serverUrl)) return

        val startedAt = System.currentTimeMillis()
        AppLogger.info(LogTags.Tools, "mcp_initialize_started", "serverHost" to serverUrl.hostForLog())
        val response = postJson(
            serverUrl = serverUrl,
            payload = buildRequest(
                requestId = ids.getAndIncrement(),
                method = "initialize",
                params = buildJsonObject {
                    put("protocolVersion", MCP_PROTOCOL_VERSION)
                    put("capabilities", buildJsonObject {})
                    put(
                        "clientInfo",
                        buildJsonObject {
                            put("name", "Aura Android")
                            put("version", "0.1.3")
                        },
                    )
                },
            ),
            includeSession = false,
            retryOnInvalidSession = false,
        )
        response.sessionId?.let { sessions[serverUrl] = it }
        response.json?.throwIfJsonRpcError()
        AppLogger.info(
            LogTags.Tools,
            "mcp_initialize_completed",
            "serverHost" to serverUrl.hostForLog(),
            "hasSession" to (response.sessionId != null),
            "durationMs" to (System.currentTimeMillis() - startedAt),
        )

        runCatching {
            postJson(
                serverUrl = serverUrl,
                payload = buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("method", "notifications/initialized")
                },
                includeSession = true,
                retryOnInvalidSession = false,
            )
        }.onFailure {
            AppLogger.debug(
                LogTags.Llm,
                "mcp_initialized_notification_failed",
                "message" to (it.message ?: it::class.simpleName.orEmpty()),
            )
        }
    }

    private fun rpc(serverUrl: String, requestId: Long, method: String, params: JsonObject): JsonObject {
        val response = postJson(
            serverUrl = serverUrl,
            payload = buildRequest(requestId = requestId, method = method, params = params),
            includeSession = true,
            retryOnInvalidSession = true,
            expectedResponseId = requestId,
        )
        response.sessionId?.let { sessions[serverUrl] = it }
        val jsonResponse = response.json
            ?: throw RuntimeException("MCP empty response for $method")
        jsonResponse.throwIfJsonRpcError()
        return jsonResponse
    }

    private fun postJson(
        serverUrl: String,
        payload: JsonObject,
        includeSession: Boolean,
        retryOnInvalidSession: Boolean,
        expectedResponseId: Long? = payload["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
    ): McpHttpResponse {
        val startedAt = System.currentTimeMillis()
        val method = payload["method"]?.jsonPrimitive?.contentOrNull.orEmpty()
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
            if (response.code == 404 && includeSession && retryOnInvalidSession) {
                AppLogger.warn(
                    LogTags.Tools,
                    "mcp_session_invalid_reinitializing",
                    "serverHost" to serverUrl.hostForLog(),
                    "method" to method,
                    "durationMs" to (System.currentTimeMillis() - startedAt),
                )
                sessions.remove(serverUrl)
                ensureInitialized(serverUrl)
                return postJson(
                    serverUrl = serverUrl,
                    payload = payload,
                    includeSession = true,
                    retryOnInvalidSession = false,
                    expectedResponseId = expectedResponseId,
                )
            }

            val rawBody = response.body?.string().orEmpty()
            if (response.code == 202 || response.code == 204) {
                return McpHttpResponse(
                    json = null,
                    sessionId = response.header("Mcp-Session-Id"),
                )
            }
            if (!response.isSuccessful) {
                AppLogger.warn(
                    LogTags.Tools,
                    "mcp_http_error",
                    "serverHost" to serverUrl.hostForLog(),
                    "method" to method,
                    "statusCode" to response.code,
                    "bodyLength" to rawBody.length,
                    "durationMs" to (System.currentTimeMillis() - startedAt),
                )
                throw RuntimeException("MCP HTTP ${response.code}: ${rawBody.take(300)}")
            }
            return McpHttpResponse(
                json = parseHttpBody(rawBody, response, expectedResponseId),
                sessionId = response.header("Mcp-Session-Id"),
            )
        }
    }

    private fun buildRequest(requestId: Long, method: String, params: JsonObject): JsonObject =
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", requestId)
            put("method", method)
            put("params", params)
        }

    private fun parseHttpBody(body: String, response: Response, expectedResponseId: Long?): JsonObject {
        val trimmed = body.trim()
        if (trimmed.isBlank()) {
            throw RuntimeException("MCP HTTP ${response.code}: empty body")
        }
        val contentType = response.header("content-type").orEmpty()
        val jsonText = if (contentType.contains("text/event-stream", ignoreCase = true) || trimmed.startsWith("data:")) {
            parseSseDataPayloads(trimmed)
                .firstNotNullOfOrNull { payload ->
                    runCatching { json.parseToJsonElement(payload).jsonObject }
                        .getOrNull()
                        ?.takeIf { expectedResponseId == null || it.matchesId(expectedResponseId) || it.containsKey("error") }
                }
                ?.toString()
                ?: throw RuntimeException("MCP SSE response did not include JSON-RPC response id=$expectedResponseId")
        } else {
            trimmed
        }
        return json.parseToJsonElement(jsonText).jsonObject
    }

    private fun parseSseDataPayloads(body: String): List<String> {
        val payloads = mutableListOf<String>()
        val dataLines = mutableListOf<String>()

        fun flushEvent() {
            if (dataLines.isNotEmpty()) {
                payloads += dataLines.joinToString("\n").trim()
                dataLines.clear()
            }
        }

        body.lineSequence().forEach { line ->
            when {
                line.isBlank() -> flushEvent()
                line.startsWith(":") -> Unit
                line.startsWith("data:") -> dataLines += line.removePrefix("data:").trimStart()
            }
        }
        flushEvent()
        return payloads
    }

    private fun JsonObject.resultObject(): JsonObject =
        (this["result"] as? JsonObject) ?: buildJsonObject {}

    private fun JsonObject.throwIfJsonRpcError() {
        val error = this["error"] as? JsonObject ?: return
        val message = error["message"]?.jsonPrimitive?.contentOrNull ?: error.toString()
        throw RuntimeException("MCP error: $message")
    }

    private fun JsonObject.matchesId(expectedResponseId: Long): Boolean =
        this["id"]?.jsonPrimitive?.contentOrNull == expectedResponseId.toString()

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
        val json: JsonObject?,
        val sessionId: String?,
    )

    private companion object {
        const val MCP_PROTOCOL_VERSION = "2025-11-25"
        val json = Json { ignoreUnknownKeys = true }
    }
}

private fun String.hostForLog(): String =
    substringAfter("://", this)
        .substringBefore("/")
        .substringBefore(":")
        .ifBlank { "unknown" }
