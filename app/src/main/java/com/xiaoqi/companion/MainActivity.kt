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
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.streaming.StreamFrame
import com.xiaoqi.companion.feature.chat.ChatScreenContent
import com.xiaoqi.companion.feature.chat.ChatMessage
import com.xiaoqi.companion.feature.chat.ChatUiState
import com.xiaoqi.companion.ui.theme.CompanionTheme
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "Companion-LLM"
    }

    private val uiState = MutableStateFlow(ChatUiState())
    private val scope = CoroutineScope(Dispatchers.Main)

    private val glmModel = LLModel(
        id = BuildConfig.ANTHROPIC_MODEL,
        provider = LLMProvider.Anthropic,
    )

    private val client by lazy {
        AnthropicLLMClient(
            apiKey = BuildConfig.ANTHROPIC_API_KEY,
            settings = AnthropicClientSettings(baseUrl = BuildConfig.ANTHROPIC_BASE_URL),
        )
    }

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
                var assistantContent = ""
                Timber.tag(TAG).d("Starting API call...")
                val chatPrompt = prompt("chat") {
                    system("你是 Aura，一个友好温暖的 AI 伙伴。用中文简洁回答。")
                    user(trimmed)
                }

                client.executeStreaming(chatPrompt, glmModel).collect { frame ->
                    when (frame) {
                        is StreamFrame.TextDelta -> {
                            assistantContent += frame.text
                            val captured = assistantContent
                            uiState.update { state ->
                                val updated = state.messages.map { msg ->
                                    if (msg.id == assistantId) msg.copy(content = captured) else msg
                                }
                                state.copy(messages = updated)
                            }
                        }
                        is StreamFrame.End -> {
                            Timber.tag(TAG).d("Stream ended, content length=%d", assistantContent.length)
                            uiState.update { state ->
                                val updated = state.messages.map { msg ->
                                    if (msg.id == assistantId) msg.copy(isStreaming = false) else msg
                                }
                                state.copy(messages = updated, isLoading = false)
                            }
                        }
                        else -> { /* ignore other frame types */ }
                    }
                }
                Timber.tag(TAG).d("Collection completed normally")
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
}
