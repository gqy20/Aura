package com.xiaoqi.companion.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoqi.companion.core.companion.CompanionRuntime
import com.xiaoqi.companion.core.companion.model.AgentError
import com.xiaoqi.companion.core.companion.model.AgentEvent
import com.xiaoqi.companion.core.companion.model.ToolCallStatus
import com.xiaoqi.companion.core.companion.model.UserInput
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogFieldSanitizer
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.core.tools.ToolDisplayRegistry
import com.xiaoqi.companion.data.db.dao.AgentStateDao
import com.xiaoqi.companion.data.db.converter.MessageRole
import com.xiaoqi.companion.data.db.converter.LlmProvider
import com.xiaoqi.companion.data.db.dao.MemoryDao
import com.xiaoqi.companion.data.db.entity.AgentStateEntity
import com.xiaoqi.companion.data.db.entity.MemoryEntity
import com.xiaoqi.companion.data.db.entity.MessageEntity
import com.xiaoqi.companion.data.repository.ConfigRepository
import com.xiaoqi.companion.data.repository.LlmConfigStatus
import com.xiaoqi.companion.data.repository.MessageRepository
import com.xiaoqi.companion.data.repository.ToolCallRepository
import com.xiaoqi.companion.data.repository.ToolCallSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val runtime: CompanionRuntime,
    private val toolDisplayRegistry: ToolDisplayRegistry,
    private val toolCallRepository: ToolCallRepository,
    private val configRepository: ConfigRepository,
    private val messageRepository: MessageRepository,
    private val memoryDao: MemoryDao,
    private val agentStateDao: AgentStateDao,
) : ViewModel() {

    companion object {
        private const val DEFAULT_SESSION_ID = "default"
        private const val RECENT_TOOL_CALL_LIMIT = 3
        private const val STREAMING_IDLE_TIMEOUT_MS = 30_000L
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    init {
        viewModelScope.launch {
            configRepository.observeLlmConfigStatus().collect { status ->
                _uiState.update { state ->
                    state.copy(configStatus = status.toChatConfigStatus())
                }
            }
        }

        viewModelScope.launch {
            agentStateDao.observeByCompanionId(DEFAULT_SESSION_ID).collect { savedState ->
                if (savedState != null) {
                    _uiState.update { state ->
                        state.copy(
                            status = state.status.copy(
                                mood = savedState.mood.ifBlank { "neutral" },
                                intensity = savedState.emotionVector.extractIntensity(),
                                relationshipLevel = savedState.relationshipLevel,
                            )
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            messageRepository.getMessagesBySession(DEFAULT_SESSION_ID).collect { messages ->
                _uiState.update { state ->
                    if (state.isLoading || state.messages.any { it.isStreaming }) {
                        state
                    } else {
                        state.copy(messages = messages.map { it.toChatMessage() })
                    }
                }
            }
        }

        viewModelScope.launch {
            memoryDao.observeAll().collect { memories ->
                _uiState.update { state ->
                    state.copy(memories = memories.take(5).map { it.toChatMemory() })
                }
            }
        }

        viewModelScope.launch {
            toolCallRepository.observeBySession(DEFAULT_SESSION_ID).collect { calls ->
                _uiState.update { state ->
                    state.copy(toolCalls = calls.take(RECENT_TOOL_CALL_LIMIT).map { it.toChatToolCall() })
                }
            }
        }
    }

    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val configStatus = _uiState.value.configStatus
        if (!configStatus.isReady) {
            _uiState.update {
                it.copy(error = configStatus.detail.ifBlank { "模型配置未完成" })
            }
            return
        }

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
            val assistantId = UUID.randomUUID().toString()
            var assistantContent = ""
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

            fun finishWithError(message: String) {
                idleTimeoutJob?.cancel()
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.filter { it.id != assistantId },
                        isLoading = false,
                        error = message,
                    )
                }
            }

            try {
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages + ChatMessage(
                            id = assistantId,
                            role = "ASSISTANT",
                            content = "",
                            isStreaming = true,
                        )
                    )
                }

                runtime.send(UserInput.Text(trimmed)).collect { event ->
                    when (event) {
                        is AgentEvent.Streaming -> {
                            resetIdleTimer()
                            assistantContent += event.delta
                            updateAssistantMessage(assistantId) {
                                it.copy(content = assistantContent)
                            }
                        }
                        is AgentEvent.ToolCallUpdated -> {
                            updateAssistantToolStatus(
                                assistantId,
                                toolDisplayRegistry.label(event.call.name, event.call.status),
                            )
                        }
                        is AgentEvent.ToolStarted -> {
                            updateAssistantToolStatus(
                                assistantId,
                                toolDisplayRegistry.label(event.name, ToolCallStatus.STARTED),
                            )
                        }
                        is AgentEvent.ToolFinished -> {
                            updateAssistantToolStatus(
                                assistantId,
                                toolDisplayRegistry.label(event.name, ToolCallStatus.SUCCEEDED),
                            )
                        }
                        is AgentEvent.Complete -> {
                            idleTimeoutJob?.cancel()
                            AppLogger.info(
                                LogTags.Chat,
                                "message_send_completed",
                                "requestHash" to LogFieldSanitizer.hash(requestId),
                                "replyLength" to event.parsed.textReply.length,
                            )
                            updateAssistantMessage(assistantId) {
                                it.copy(
                                    content = event.parsed.textReply,
                                    isStreaming = false,
                                )
                            }
                            _uiState.update {
                                val nextStatus = it.status.after(
                                    mood = event.parsed.emotionSignal.mood,
                                    intensity = event.parsed.emotionSignal.intensity,
                                    affinityDelta = event.parsed.interactionSignal.affinityDelta,
                                )
                                persistStatus(nextStatus)
                                it.copy(
                                    isLoading = false,
                                    status = nextStatus,
                                )
                            }
                        }
                        is AgentEvent.Error -> {
                            AppLogger.warn(
                                LogTags.Chat,
                                "agent_error_received",
                                "requestHash" to LogFieldSanitizer.hash(requestId),
                                "errorType" to event.error::class.simpleName,
                            )
                            finishWithError(formatError(event.error))
                        }
                    }
                }

                if (timedOut) {
                    finishWithError("Response timed out. Please try again.")
                }
            } catch (e: Exception) {
                AppLogger.error(
                    LogTags.Chat,
                    e,
                    "message_send_failed",
                    "requestHash" to LogFieldSanitizer.hash(requestId),
                    "textLength" to trimmed.length,
                )
                finishWithError("Send failed. Please try again.")
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun openMemoryRoom() {
        _uiState.update { it.copy(isMemoryRoomOpen = true) }
    }

    fun closeMemoryRoom() {
        _uiState.update { it.copy(isMemoryRoomOpen = false) }
    }

    fun openSettings() {
        val state = _uiState.value
        _uiState.update {
            it.copy(
                isSettingsOpen = true,
                settingsProvider = state.configStatus.provider,
                settingsModelName = state.configStatus.modelName,
                settingsMessage = null,
            )
        }
    }

    fun closeSettings() {
        _uiState.update { it.copy(isSettingsOpen = false, settingsMessage = null) }
    }

    fun updateSettingsApiKey(value: String) {
        _uiState.update { it.copy(settingsApiKey = value, settingsMessage = null) }
    }

    fun updateSettingsProvider(value: LlmProvider) {
        val defaultModel = when (value) {
            LlmProvider.GLM -> "glm-5v-turbo"
            LlmProvider.KIMI -> "kimi-latest"
        }
        _uiState.update {
            it.copy(
                settingsProvider = value,
                settingsModelName = defaultModel,
                settingsMessage = null,
            )
        }
    }

    fun updateSettingsModelName(value: String) {
        _uiState.update { it.copy(settingsModelName = value, settingsMessage = null) }
    }

    fun saveSettings() {
        val state = _uiState.value
        val model = state.settingsModelName.trim()
        if (model.isBlank()) {
            _uiState.update { it.copy(settingsMessage = "模型名称不能为空") }
            return
        }
        viewModelScope.launch {
            configRepository.setLlmProvider(state.settingsProvider)
            configRepository.setModelName(model)
            state.settingsApiKey.trim().takeIf { it.isNotEmpty() }?.let { apiKey ->
                configRepository.setApiKey(apiKey)
            }
            _uiState.update {
                it.copy(
                    isSettingsOpen = false,
                    settingsApiKey = "",
                    settingsModelName = model,
                    settingsMessage = null,
                )
            }
        }
    }

    private fun updateAssistantMessage(
        assistantId: String,
        transform: (ChatMessage) -> ChatMessage,
    ) {
        _uiState.update { state ->
            val updated = state.messages.map { msg ->
                if (msg.id == assistantId) transform(msg) else msg
            }
            state.copy(messages = updated)
        }
    }

    private fun updateAssistantToolStatus(assistantId: String, status: String) {
        updateAssistantMessage(assistantId) { it.copy(toolStatus = status) }
    }

    private fun ToolCallSnapshot.toChatToolCall(): ChatToolCall =
        ChatToolCall(
            id = id,
            label = toolDisplayRegistry.label(toolName, status),
            status = when (status) {
                ToolCallStatus.STARTED -> "Running"
                ToolCallStatus.SUCCEEDED -> "Done"
                ToolCallStatus.FAILED -> "Failed"
            },
            durationMs = durationMs,
            errorMessage = errorMessage,
        )

    private fun formatError(error: AgentError): String =
        when (error) {
            is AgentError.NetworkTimeout -> "Network timed out. Check your connection."
            is AgentError.RateLimited -> "Too many requests. Try again later."
            is AgentError.ApiError -> error.message
            is AgentError.ParseError -> error.reason
        }

    private fun MessageEntity.toChatMessage(): ChatMessage =
        ChatMessage(
            id = id,
            role = when (role) {
                MessageRole.USER -> "USER"
                MessageRole.ASSISTANT -> "ASSISTANT"
                MessageRole.SYSTEM -> "SYSTEM"
            },
            content = content,
            timestamp = timestamp,
        )

    private fun MemoryEntity.toChatMemory(): ChatMemory =
        ChatMemory(
            id = id,
            content = content,
            type = type.name,
            importance = importance,
        )

    private fun CompanionStatus.after(
        mood: String,
        intensity: Float,
        affinityDelta: Float,
    ): CompanionStatus =
        copy(
            mood = mood.ifBlank { this.mood },
            intensity = intensity.coerceIn(0f, 1f),
            relationshipLevel = (relationshipLevel + affinityDelta).coerceIn(0f, 1f),
        )

    private fun LlmConfigStatus.toChatConfigStatus(): ChatConfigStatus =
        if (isReady) {
            ChatConfigStatus(
                label = "${provider.name} · $modelName",
                isReady = true,
                detail = "模型已就绪",
                provider = provider,
                modelName = modelName,
            )
        } else {
            ChatConfigStatus(
                label = "${provider.name} · ${modelName.ifBlank { "未选择模型" }}",
                isReady = false,
                detail = missingReason ?: "模型配置未完成",
                provider = provider,
                modelName = modelName,
            )
        }

    private fun persistStatus(status: CompanionStatus) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val existing = agentStateDao.getByCompanionId(DEFAULT_SESSION_ID)
            agentStateDao.insert(
                AgentStateEntity(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    companionId = DEFAULT_SESSION_ID,
                    mood = status.mood,
                    emotionVector = """{"intensity":${status.intensity}}""",
                    relationshipLevel = status.relationshipLevel,
                    lastInteractionAt = now,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                )
            )
        }
    }

    private fun String.extractIntensity(): Float {
        val value = Regex("\"intensity\"\\s*:\\s*([\\d.]+)")
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.toFloatOrNull()
        return value?.coerceIn(0f, 1f) ?: 0.5f
    }
}
