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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val uiState = MutableStateFlow(ChatUiState())
    private val scope = CoroutineScope(Dispatchers.Main)

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

        val userMsg = ChatMessage(id = UUID.randomUUID().toString(), role = "USER", content = trimmed)
        val assistantId = UUID.randomUUID().toString()

        uiState.update {
            it.copy(messages = it.messages + userMsg, inputText = "", isLoading = true, error = null)
        }
        uiState.update {
            it.copy(messages = it.messages + ChatMessage(assistantId, "ASSISTANT", "", isStreaming = true))
        }

        scope.launch {
            kotlinx.coroutines.delay(600)
            uiState.update { state ->
                val updated = state.messages.map { msg ->
                    if (msg.id == assistantId) msg.copy(content = "Echo: $trimmed", isStreaming = false) else msg
                }
                state.copy(messages = updated, isLoading = false)
            }
        }
    }
}
