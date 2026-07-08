package com.xiaoqi.companion.core.remote

import com.xiaoqi.companion.core.companion.model.AgentEvent
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val REMOTE_AGENT_JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

interface RemoteAgentTransport {
    suspend fun postJson(path: String, bodyJson: String): String
}

class OkHttpRemoteAgentTransport(
    private val baseUrl: String,
    private val apiKey: String,
    private val client: OkHttpClient = OkHttpClient(),
) : RemoteAgentTransport {
    override suspend fun postJson(path: String, bodyJson: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .header("Content-Type", "application/json")
            .apply {
                if (apiKey.isNotBlank()) header("Authorization", "Bearer $apiKey")
            }
            .post(bodyJson.toRequestBody(REMOTE_AGENT_JSON_MEDIA_TYPE))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Remote agent HTTP ${response.code}: $body")
            body
        }
    }
}

class RemoteAgentService @Inject constructor(
    private val transport: RemoteAgentTransport,
) {
    suspend fun runTurn(request: RemoteAgentTurnRequest): Flow<AgentEvent> {
        val body = json.encodeToString(request)
        val response = transport.postJson(AGENT_TURN_PATH, body)
        return json.decodeFromString<List<RemoteAgentEventDto>>(response)
            .map { it.toAgentEvent() }
            .asFlow()
    }

    suspend fun callReadOnlyTool(request: RemoteReadOnlyToolRequest): RemoteReadOnlyToolResult {
        require(request.toolName.isNotBlank()) { "toolName is required" }
        val body = json.encodeToString(request)
        val response = transport.postJson(READ_ONLY_TOOL_PATH, body)
        return json.decodeFromString(response)
    }

    private companion object {
        const val AGENT_TURN_PATH = "/v1/agent/turn"
        const val READ_ONLY_TOOL_PATH = "/v1/tools/read"
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
