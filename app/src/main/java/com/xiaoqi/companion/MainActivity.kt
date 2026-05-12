package com.xiaoqi.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaoqi.companion.feature.chat.ChatScreenContent
import com.xiaoqi.companion.feature.chat.ChatMessage
import com.xiaoqi.companion.feature.chat.ChatUiState
import com.xiaoqi.companion.ui.theme.CompanionTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "Companion-LLM"
    }

    private val uiState = MutableStateFlow(ChatUiState())
    private val scope = CoroutineScope(Dispatchers.Main)

    private val httpClient = OkHttpClient.Builder()
        .callTimeout(60.seconds.inWholeMilliseconds, java.util.concurrent.TimeUnit.MILLISECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val state by uiState.collectAsStateWithLifecycle()
            CompanionTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ChatScreenContent(
                        uiState = state,
                        onSendMessage = { sendMessage(state.inputText) },
                        onInputTextChanged = { text -> uiState.update { it.copy(inputText = text) } },
                        onClearError = { uiState.update { it.copy(error = null) } },
                    )
                }
            }
        }
    }

    private fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        Timber.tag(TAG).d("sendMessage: text='%s', model=%s, url=%s", trimmed, BuildConfig.ANTHROPIC_MODEL, BuildConfig.ANTHROPIC_BASE_URL)

        val userMsg = ChatMessage(id = UUID.randomUUID().toString(), role = "USER", content = trimmed)
        val assistantId = UUID.randomUUID().toString()

        uiState.update {
            it.copy(messages = it.messages + userMsg, inputText = "", isLoading = true, error = null)
        }
        uiState.update {
            it.copy(messages = it.messages + ChatMessage(assistantId, "ASSISTANT", "", isStreaming = true))
        }

        scope.launch {
            try {
                val body = buildRequestBody(trimmed)
                val request = Request.Builder()
                    .url("${BuildConfig.ANTHROPIC_BASE_URL}/v1/messages")
                    .header("x-api-key", BuildConfig.ANTHROPIC_API_KEY)
                    .header("anthropic-version", "2023-06-01")
                    .header("content-type", "application/json")
                    .post(body)
                    .build()

                Timber.tag(TAG).d("Starting streaming request...")
                var assistantContent = ""

                withContext(Dispatchers.IO) {
                    httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errBody = response.body?.string() ?: ""
                        throw RuntimeException("HTTP ${response.code}: $errBody")
                    }

                    response.body?.source()?.let { source ->
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: continue
                            if (!line.startsWith("data: ")) continue

                            val data = line.removePrefix("data: ").trim()
                            if (data == "[DONE]") break

                            parseSseEvent(data)?.let { delta ->
                                assistantContent += delta
                                val captured = assistantContent
                                uiState.update { state ->
                                    val updated = state.messages.map { msg ->
                                        if (msg.id == assistantId) msg.copy(content = captured) else msg
                                    }
                                    state.copy(messages = updated)
                                }
                            }
                        }
                    }
                    }
                }
                uiState.update { state ->
                    val updated = state.messages.map { msg ->
                        if (msg.id == assistantId) msg.copy(isStreaming = false) else msg
                    }
                    state.copy(messages = updated, isLoading = false)
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "API call failed")
                uiState.update { state ->
                    val filtered = state.messages.filter { it.id != assistantId }
                    filtered.let {
                        state.copy(
                            messages = it,
                            isLoading = false,
                            error = e.message ?: "请求失败，请重试",
                        )
                    }
                }
            }
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun buildRequestBody(userMessage: String): okhttp3.RequestBody {
        val body = buildJsonObject {
            put("model", BuildConfig.ANTHROPIC_MODEL)
            put("max_tokens", JsonPrimitive(1024))
            put("stream", JsonPrimitive(true))
            put("system", "你是 Aura，一个友好温暖的 AI 伙伴。用中文简洁回答。")
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", userMessage)
                })
            })
        }
        Timber.tag(TAG).d("Request body: %s", body.toString())
        return body.toString().toRequestBody("application/json".toMediaType())
    }

    private fun parseSseEvent(data: String): String? {
        // Anthropic SSE format: {"type":"content_block_delta","delta":{"type":"text_delta","text":"..."}}
        if (!data.contains("\"content_block_delta\"")) return null
        val regex = """"text_delta".*?"text"\s*:\s*"((?:[^"\\]|\\.)*)"""".toRegex()
        return regex.find(data)?.groupValues?.get(1)?.replace("\\n", "\n")
    }
}
