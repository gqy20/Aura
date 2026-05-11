package com.xiaoqi.companion.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoqi.companion.core.companion.CompanionRuntime
import com.xiaoqi.companion.core.companion.model.AgentEvent
import com.xiaoqi.companion.core.companion.model.UserInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(
    private val runtime: CompanionRuntime,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        // Append user message immediately
        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = "USER",
            content = trimmed,
        )
        _uiState.update {
            it.copy(
                messages = it.messages + userMsg,
                inputText = "",
                isLoading = true,
                error = null,
            )
        }

        viewModelScope.launch {
            try {
                var assistantContent = ""
                val assistantId = UUID.randomUUID().toString()

                // Add placeholder for streaming
                _uiState.update { state ->
                    state.copy(messages = state.messages + ChatMessage(assistantId, "ASSISTANT", "", isStreaming = true))
                }

                runtime.send(UserInput.Text(trimmed)).collect { event ->
                    when (event) {
                        is AgentEvent.Streaming -> {
                            assistantContent += event.delta
                            _uiState.update { state ->
                                val updated = state.messages.map { msg ->
                                    if (msg.id == assistantId) msg.copy(content = assistantContent) else msg
                                }
                                state.copy(messages = updated)
                            }
                        }
                        is AgentEvent.Complete -> {
                            _uiState.update { state ->
                                val updated = state.messages.map { msg ->
                                    if (msg.id == assistantId) msg.copy(
                                        content = event.parsed.textReply,
                                        isStreaming = false,
                                    ) else msg
                                }
                                state.copy(messages = updated, isLoading = false)
                            }
                        }
                        is AgentEvent.Error -> {
                            // Remove streaming placeholder and set error
                            _uiState.update { state ->
                                val filtered = state.messages.filter { it.id != assistantId }
                                state.copy(
                                    messages = filtered,
                                    isLoading = false,
                                    error = formatError(event.error),
                                )
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "发送失败，请重试") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun formatError(error: com.xiaoqi.companion.core.companion.model.AgentError): String =
        when (error) {
            is com.xiaoqi.companion.core.companion.model.AgentError.NetworkTimeout -> "网络超时，请检查连接"
            is com.xiaoqi.companion.core.companion.model.AgentError.RateLimited -> "请求过于频繁，请稍后再试"
            is com.xiaoqi.companion.core.companion.model.AgentError.ApiError -> error.message ?: "未知错误"
            else -> "出错了，请重试"
        }
}
