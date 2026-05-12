package com.xiaoqi.companion.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoqi.companion.core.companion.CompanionRuntime
import com.xiaoqi.companion.core.companion.model.AgentError
import com.xiaoqi.companion.core.companion.model.AgentEvent
import com.xiaoqi.companion.core.companion.model.UserInput
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogFieldSanitizer
import com.xiaoqi.companion.core.logging.LogTags
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val runtime: CompanionRuntime,
) : ViewModel() {

    companion object {
        private const val STREAMING_IDLE_TIMEOUT_MS = 30_000L
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val requestId = UUID.randomUUID().toString()
        AppLogger.info(
            LogTags.Chat,
            "message_send_started",
            "requestHash" to LogFieldSanitizer.hash(requestId),
            "textLength" to trimmed.length,
        )

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

                _uiState.update { state ->
                    state.copy(messages = state.messages + ChatMessage(assistantId, "ASSISTANT", "", isStreaming = true))
                }

                var idleTimeoutJob: Job? = null
                var timedOut = false
                fun resetIdleTimer() {
                    timedOut = false
                    idleTimeoutJob?.cancel()
                    idleTimeoutJob = launch {
                        delay(STREAMING_IDLE_TIMEOUT_MS)
                        timedOut = true
                        AppLogger.warn(
                            LogTags.Chat,
                            "streaming_idle_timeout",
                            "requestHash" to LogFieldSanitizer.hash(requestId),
                            "timeoutMs" to STREAMING_IDLE_TIMEOUT_MS,
                        )
                    }
                }

                runtime.send(UserInput.Text(trimmed)).collect { event ->
                    when (event) {
                        is AgentEvent.Streaming -> {
                            resetIdleTimer()
                            assistantContent += event.delta
                            _uiState.update { state ->
                                val updated = state.messages.map { msg ->
                                    if (msg.id == assistantId) msg.copy(content = assistantContent) else msg
                                }
                                state.copy(messages = updated)
                            }
                        }
                        is AgentEvent.ToolStarted,
                        is AgentEvent.ToolFinished -> Unit
                        is AgentEvent.Complete -> {
                            idleTimeoutJob?.cancel()
                            AppLogger.info(
                                LogTags.Chat,
                                "message_send_completed",
                                "requestHash" to LogFieldSanitizer.hash(requestId),
                                "replyLength" to event.parsed.textReply.length,
                            )
                            _uiState.update { state ->
                                val updated = state.messages.map { msg ->
                                    if (msg.id == assistantId) {
                                        msg.copy(
                                            content = event.parsed.textReply,
                                            isStreaming = false,
                                        )
                                    } else {
                                        msg
                                    }
                                }
                                state.copy(messages = updated, isLoading = false)
                            }
                        }
                        is AgentEvent.Error -> {
                            idleTimeoutJob?.cancel()
                            AppLogger.warn(
                                LogTags.Chat,
                                "agent_error_received",
                                "requestHash" to LogFieldSanitizer.hash(requestId),
                                "errorType" to event.error::class.simpleName,
                            )
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

                if (timedOut) {
                    _uiState.update { state ->
                        val filtered = state.messages.filter { it.id != assistantId }
                        state.copy(
                            messages = filtered,
                            isLoading = false,
                            error = "响应超时，请重试",
                        )
                    }
                }
            } catch (e: Exception) {
                AppLogger.error(
                    LogTags.Chat,
                    e,
                    "message_send_failed",
                    "requestHash" to LogFieldSanitizer.hash(requestId),
                    "textLength" to trimmed.length,
                )
                _uiState.update { it.copy(isLoading = false, error = "发送失败，请重试") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun formatError(error: AgentError): String =
        when (error) {
            is AgentError.NetworkTimeout -> "网络超时，请检查连接"
            is AgentError.RateLimited -> "请求过于频繁，请稍后再试"
            is AgentError.ApiError -> error.message ?: "未知错误"
            else -> "出错了，请重试"
        }
}
