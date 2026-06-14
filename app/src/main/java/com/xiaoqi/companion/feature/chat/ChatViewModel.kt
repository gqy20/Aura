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
import com.xiaoqi.companion.core.presence.PresenceController
import com.xiaoqi.companion.core.presence.PresenceEvent
import com.xiaoqi.companion.core.presence.PresenceInputs
import com.xiaoqi.companion.core.presence.PresenceReaction
import com.xiaoqi.companion.core.presence.PresenceReactionPolicy
import com.xiaoqi.companion.core.tools.ToolDisplayRegistry
import com.xiaoqi.companion.core.local.LocalQwenModelDownloadState
import com.xiaoqi.companion.core.local.LocalQwenModelDownloader
import com.xiaoqi.companion.data.db.dao.AgentStateDao
import com.xiaoqi.companion.data.db.converter.MessageRole
import com.xiaoqi.companion.data.db.converter.LlmProvider
import com.xiaoqi.companion.data.db.dao.MemoryDao
import com.xiaoqi.companion.data.db.entity.AgentStateEntity
import com.xiaoqi.companion.data.db.entity.MemoryEntity
import com.xiaoqi.companion.data.db.entity.MessageEntity
import com.xiaoqi.companion.data.db.entity.ReminderEntity
import com.xiaoqi.companion.data.datastore.AppPreferences
import com.xiaoqi.companion.data.repository.ConfigRepository
import com.xiaoqi.companion.data.repository.DefaultLlmValues
import com.xiaoqi.companion.data.repository.LlmConfigStatus
import com.xiaoqi.companion.data.repository.MessageRepository
import com.xiaoqi.companion.data.repository.ReminderRepository
import com.xiaoqi.companion.data.repository.ToolCallRepository
import com.xiaoqi.companion.data.repository.ToolCallSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val runtime: CompanionRuntime,
    private val toolDisplayRegistry: ToolDisplayRegistry,
    private val toolCallRepository: ToolCallRepository,
    private val configRepository: ConfigRepository,
    private val imageProcessor: ChatImageProcessor,
    private val messageRepository: MessageRepository,
    private val memoryDao: MemoryDao,
    private val agentStateDao: AgentStateDao,
    private val presenceController: PresenceController,
    private val presenceReactionPolicy: PresenceReactionPolicy,
    private val appPreferences: AppPreferences,
    private val reminderRepository: ReminderRepository,
    private val localQwenModelDownloader: LocalQwenModelDownloader,
) : ViewModel() {

    companion object {
        private const val DEFAULT_SESSION_ID = "default"
        private const val RECENT_TOOL_CALL_LIMIT = 3
        private const val PRESENCE_REACTION_DURATION_MS = 1_300L
        private const val STREAMING_IDLE_TIMEOUT_MS = 30_000L
        private const val STREAMING_RENDER_BATCH_MS = 90L
        private const val STREAMING_RENDER_BATCH_CHARS = 48
        private const val IMAGE_ONLY_PROMPT = "我想给你看这张图片。先说说你看到了什么，再自然地回应我。"
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    private var presenceReactionJob: Job? = null
    private val lastPresenceReactionAtMillis = mutableMapOf<PresenceReaction, Long>()
    private var localQwenDownloadJob: Job? = null
    private var localQwenStatusJob: Job? = null

    val uiState: StateFlow<ChatUiState> = _uiState
        .map { it.withPresence() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = _uiState.value.withPresence(),
        )

    init {
        viewModelScope.launch {
            configRepository.observeLlmConfigStatus().collect { status ->
                _uiState.update { state ->
                    state.copy(configStatus = status.toChatConfigStatus()).withPresence()
                }
                if (status.provider == LlmProvider.LOCAL_QWEN) {
                    refreshLocalQwenModelStatus(status.modelName)
                } else {
                    localQwenStatusJob?.cancel()
                    localQwenStatusJob = null
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
                        ).withPresence()
                    }
                }
            }
        }

        viewModelScope.launch {
            combine(
                appPreferences.deviceStatusContextEnabled,
                appPreferences.locationContextEnabled,
                appPreferences.weatherContextEnabled,
                appPreferences.reminderToolEnabled,
                appPreferences.notificationEnabled,
            ) { deviceStatus, location, weather, reminder, notification ->
                ChatToolCapabilitySettings(
                    deviceStatusEnabled = deviceStatus,
                    locationContextEnabled = location,
                    weatherContextEnabled = weather,
                    reminderToolEnabled = reminder,
                    notificationEnabled = notification,
                )
            }.collect { settings ->
                _uiState.update { state ->
                    state.copy(
                        toolCapabilitySettings = settings.copy(
                            mcpHttpUrl = state.toolCapabilitySettings.mcpHttpUrl,
                        )
                    )
                }
            }
        }

        viewModelScope.launch {
            combine(
                appPreferences.mcpServerName,
                appPreferences.mcpHttpUrl,
            ) { mcpServerName, mcpHttpUrl -> mcpServerName to mcpHttpUrl }
                .collect { (mcpServerName, mcpHttpUrl) ->
                _uiState.update { state ->
                    state.copy(
                        toolCapabilitySettings = state.toolCapabilitySettings.copy(
                            mcpServerName = mcpServerName,
                            mcpHttpUrl = mcpHttpUrl,
                        )
                    )
                }
            }
        }

        viewModelScope.launch {
            messageRepository.getMessagesBySession(DEFAULT_SESSION_ID).collect { messages ->
                _uiState.update { state ->
                    if (state.isLoading || state.messages.any { it.isStreaming }) {
                        state
                    } else {
                        state.copy(messages = messages.map { it.toChatMessage() }).withPresence()
                    }
                }
            }
        }

        viewModelScope.launch {
            memoryDao.observeAll().collect { memories ->
                _uiState.update { state ->
                    state.copy(memories = memories.take(24).map { it.toChatMemory() }).withPresence()
                }
            }
        }

        viewModelScope.launch {
            reminderRepository.observeReminders().collect { reminders ->
                _uiState.update { state ->
                    state.copy(reminders = reminders.map { it.toChatReminder() })
                }
            }
        }

        viewModelScope.launch {
            toolCallRepository.observeBySession(DEFAULT_SESSION_ID).collect { calls ->
                var shouldClearReaction = false
                _uiState.update { state ->
                    val visibleCalls = calls.take(RECENT_TOOL_CALL_LIMIT).map { it.toChatToolCall() }
                    val previousLatest = state.toolCalls.firstOrNull()
                    val nextLatest = visibleCalls.firstOrNull()
                    val reaction = nextLatest
                        ?.takeIf { previousLatest?.id != it.id || previousLatest.toolStatus != it.toolStatus }
                        ?.let {
                            presenceController.reactionFor(
                                PresenceEvent.ToolChanged(
                                    name = it.toolName,
                                    status = it.toolStatus,
                                )
                            )
                        }
                    val acceptedReaction = reaction?.takeIf { candidate ->
                        shouldShowPresenceReaction(candidate, state)
                    }
                    shouldClearReaction = acceptedReaction != null
                    state.copy(
                        toolCalls = visibleCalls,
                        presenceReaction = acceptedReaction ?: state.presenceReaction,
                    ).withPresence()
                }
                if (shouldClearReaction) {
                    clearPresenceReactionLater()
                }
            }
        }
    }

    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text).withPresence() }
    }

    fun attachImage(uriString: String?) {
        if (uriString.isNullOrBlank()) return
        _uiState.update {
            it.copy(
                isPreparingImage = true,
                error = null,
            ).withPresence()
        }
        viewModelScope.launch {
            try {
                val prepared = imageProcessor.prepare(uriString)
                _uiState.update {
                    it.copy(
                        pendingImage = ChatImageAttachment(
                            uriString = prepared.uriString,
                            imageBase64 = prepared.imageBase64,
                            mediaType = prepared.mediaType,
                        ),
                        isPreparingImage = false,
                    ).withPresence()
                }
            } catch (e: Exception) {
                AppLogger.warn(
                    LogTags.Chat,
                    "image_prepare_failed",
                    "message" to (e.message ?: e::class.simpleName.orEmpty()),
                )
                _uiState.update {
                    it.copy(
                        pendingImage = null,
                        isPreparingImage = false,
                        error = "图片处理失败，请换一张试试。",
                    )
                }
            }
        }
    }

    fun removePendingImage() {
        _uiState.update { it.copy(pendingImage = null, isPreparingImage = false).withPresence() }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        val attachment = _uiState.value.pendingImage
        if (trimmed.isEmpty() && attachment == null) return
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
            "hasImage" to (attachment != null),
        )

        val userPrompt = trimmed.ifBlank { IMAGE_ONLY_PROMPT }
        val userDisplayContent = trimmed.ifBlank { "Shared a picture" }
        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = "USER",
            content = userDisplayContent,
            imageUri = attachment?.uriString,
        )
        _uiState.update {
            it.copy(
                messages = it.messages + userMsg,
                inputText = "",
                pendingImage = null,
                isLoading = true,
                error = null,
            )
        }

        viewModelScope.launch {
            val assistantId = UUID.randomUUID().toString()
            var assistantContent = ""
            val streamingChunker = StreamingMarkdownChunker()
            val pendingStreamingContent = StringBuilder()
            var streamingRenderJob: Job? = null
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

            fun flushStreamingContent() {
                if (pendingStreamingContent.isEmpty()) return
                val renderState = streamingChunker.append(pendingStreamingContent.toString())
                pendingStreamingContent.clear()
                updateAssistantMessage(assistantId) {
                    it.copy(
                        content = renderState.rawText,
                        renderBlocks = renderState.committedBlocks,
                        renderDraft = renderState.draftText,
                        isRenderDraftCode = renderState.isDraftCode,
                    )
                }
            }

            fun scheduleStreamingRender() {
                if (assistantContent == pendingStreamingContent.toString() ||
                    pendingStreamingContent.length >= STREAMING_RENDER_BATCH_CHARS
                ) {
                    streamingRenderJob?.cancel()
                    streamingRenderJob = null
                    flushStreamingContent()
                    return
                }
                if (streamingRenderJob?.isActive == true) return
                streamingRenderJob = launch {
                    delay(STREAMING_RENDER_BATCH_MS)
                    flushStreamingContent()
                    streamingRenderJob = null
                }
            }

            fun finishWithError(message: String) {
                idleTimeoutJob?.cancel()
                streamingRenderJob?.cancel()
                flushStreamingContent()
                _uiState.update { state ->
                    val hasPartialAssistantReply = assistantContent.isNotBlank() ||
                        state.messages.any { it.id == assistantId && it.content.isNotBlank() }
                    val messages = if (hasPartialAssistantReply) {
                        state.messages.map { msg ->
                            if (msg.id == assistantId) {
                                msg.copy(
                                    isStreaming = false,
                                    renderBlocks = emptyList(),
                                    renderDraft = "",
                                    isRenderDraftCode = false,
                                    toolStatus = "回复未完整完成",
                                )
                            } else {
                                msg
                            }
                        }
                    } else {
                        state.messages.filter { it.id != assistantId }
                    }
                    state.copy(
                        messages = messages,
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

                val userInput = if (attachment != null) {
                    UserInput.Vision(
                        text = userPrompt,
                        imageBase64 = attachment.imageBase64,
                        mediaType = attachment.mediaType,
                        displayText = userDisplayContent,
                    )
                } else {
                    UserInput.Text(userPrompt)
                }

                runtime.send(userInput).collect { event ->
                    when (event) {
                        is AgentEvent.Streaming -> {
                            resetIdleTimer()
                            assistantContent += event.delta
                            pendingStreamingContent.append(event.delta)
                            scheduleStreamingRender()
                        }
                        is AgentEvent.ToolCallUpdated -> {
                            maybeShowPermissionPrompt(event.call)
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
                        is AgentEvent.MemorySaved -> {
                            updateAssistantToolStatus(
                                assistantId,
                                if (event.count == 1) "已记住 1 条" else "已记住 ${event.count} 条",
                            )
                            triggerPresenceReaction(
                                presenceController.reactionFor(PresenceEvent.MemorySaved(event.count))
                            )
                        }
                        is AgentEvent.Complete -> {
                            idleTimeoutJob?.cancel()
                            streamingRenderJob?.cancel()
                            flushStreamingContent()
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
                                    renderBlocks = emptyList(),
                                    renderDraft = "",
                                    isRenderDraftCode = false,
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

    fun dismissPermissionPrompt() {
        _uiState.update { it.copy(permissionPrompt = null) }
    }

    fun onPresenceTapped() {
        triggerPresenceReaction(presenceController.reactionFor(PresenceEvent.UserTapped))
    }

    fun cancelReminder(reminderId: String) {
        viewModelScope.launch {
            try {
                AppLogger.info(LogTags.Reminder, "ui_cancel_reminder_started", "reminderId" to reminderId)
                reminderRepository.cancelReminder(reminderId)
                AppLogger.info(LogTags.Reminder, "ui_cancel_reminder_completed", "reminderId" to reminderId)
            } catch (e: Exception) {
                AppLogger.error(LogTags.Reminder, e, "ui_cancel_reminder_failed", "reminderId" to reminderId)
                _uiState.update { it.copy(error = "Cancel reminder failed. Please try again.") }
            }
        }
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            try {
                AppLogger.info(LogTags.Repo, "ui_delete_memory_started", "memoryId" to memoryId)
                memoryDao.deleteById(memoryId)
                AppLogger.info(LogTags.Repo, "ui_delete_memory_completed", "memoryId" to memoryId)
            } catch (e: Exception) {
                AppLogger.error(LogTags.Repo, e, "ui_delete_memory_failed", "memoryId" to memoryId)
                _uiState.update { it.copy(error = "Delete memory failed. Please try again.") }
            }
        }
    }

    fun prepareSettings() {
        val state = _uiState.value
        val modelName = state.configStatus.modelName
        _uiState.update {
            it.copy(
                settingsProvider = state.configStatus.provider,
                settingsModelName = modelName,
                settingsBaseUrl = state.configStatus.baseUrl,
                settingsMessage = null,
            )
        }
        refreshLocalQwenModelStatus(modelName)
    }

    fun updateSettingsApiKey(value: String) {
        _uiState.update { it.copy(settingsApiKey = value, settingsMessage = null) }
    }

    fun updateSettingsProvider(value: LlmProvider) {
        val defaultModel = DefaultLlmValues.defaultModel(value)
        _uiState.update {
            it.copy(
                settingsProvider = value,
                settingsModelName = defaultModel,
                settingsBaseUrl = defaultBaseUrl(value),
                settingsMessage = null,
            )
        }
        if (value == LlmProvider.LOCAL_QWEN) {
            refreshLocalQwenModelStatus(defaultModel)
        }
    }

    fun updateSettingsModelName(value: String) {
        _uiState.update { it.copy(settingsModelName = value, settingsMessage = null) }
        if (_uiState.value.settingsProvider == LlmProvider.LOCAL_QWEN) {
            refreshLocalQwenModelStatus(value)
        }
    }

    fun updateSettingsBaseUrl(value: String) {
        _uiState.update { it.copy(settingsBaseUrl = value, settingsMessage = null) }
    }

    fun setDeviceStatusContextEnabled(value: Boolean) {
        updateBooleanPreference("device_status_context", value) { appPreferences.setDeviceStatusContextEnabled(value) }
    }

    fun setLocationContextEnabled(value: Boolean) {
        updateBooleanPreference("location_context", value) { appPreferences.setLocationContextEnabled(value) }
    }

    fun setWeatherContextEnabled(value: Boolean) {
        updateBooleanPreference("weather_context", value) { appPreferences.setWeatherContextEnabled(value) }
    }

    fun setReminderToolEnabled(value: Boolean) {
        updateBooleanPreference("reminder_tool", value) { appPreferences.setReminderToolEnabled(value) }
    }

    fun setNotificationEnabled(value: Boolean) {
        updateBooleanPreference("notification", value) { appPreferences.setNotificationEnabled(value) }
    }

    fun saveSettings() {
        val state = _uiState.value
        val provider = state.settingsProvider
        val model = state.settingsModelName.trim()
            .takeIf { it in DefaultLlmValues.modelOptions(provider) }
            ?: DefaultLlmValues.defaultModel(provider)
        val baseUrl = DefaultLlmValues.defaultBaseUrl(provider)
        if (model.isBlank()) {
            _uiState.update { it.copy(settingsMessage = "模型名称不能为空") }
            return
        }
        if (provider != LlmProvider.LOCAL_QWEN && baseUrl.isBlank()) {
            _uiState.update { it.copy(settingsMessage = "Base URL 不能为空") }
            return
        }
        viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            try {
                AppLogger.info(LogTags.Config, "settings_save_started", "provider" to provider, "model" to model)
                configRepository.setLlmProvider(provider)
                configRepository.setModelName(model)
                configRepository.setBaseUrl(baseUrl)
                state.settingsApiKey.trim().takeIf { it.isNotEmpty() }?.let { apiKey ->
                    configRepository.setApiKey(apiKey)
                }
                _uiState.update {
                    it.copy(
                        settingsApiKey = "",
                        settingsModelName = model,
                        settingsBaseUrl = baseUrl,
                        settingsMessage = null,
                    )
                }
                AppLogger.info(
                    LogTags.Config,
                    "settings_save_completed",
                    "provider" to provider,
                    "model" to model,
                    "durationMs" to (System.currentTimeMillis() - startedAt),
                )
            } catch (e: Exception) {
                AppLogger.error(
                    LogTags.Config,
                    e,
                    "settings_save_failed",
                    "provider" to provider,
                    "model" to model,
                    "durationMs" to (System.currentTimeMillis() - startedAt),
                )
                _uiState.update { it.copy(settingsMessage = "Save settings failed. Please try again.") }
            }
        }
    }

    fun downloadSelectedLocalQwenModel() {
        val state = _uiState.value
        if (state.settingsProvider != LlmProvider.LOCAL_QWEN) return
        val modelName = state.settingsModelName
            .takeIf { it in DefaultLlmValues.modelOptions(LlmProvider.LOCAL_QWEN) }
            ?: DefaultLlmValues.LOCAL_QWEN_MODEL
        localQwenDownloadJob?.cancel()
        localQwenDownloadJob = viewModelScope.launch {
            try {
                localQwenModelDownloader.download(modelName).collect { downloadState ->
                    _uiState.update {
                        it.copy(
                            configStatus = it.configStatus.withLocalQwenDownloadState(downloadState),
                            localQwenDownload = downloadState.toUiState(),
                        )
                    }
                }
            } catch (e: Exception) {
                AppLogger.error(
                    LogTags.Config,
                    e,
                    "local_qwen_download_failed",
                    "model" to modelName,
                )
                _uiState.update {
                    it.copy(
                        localQwenDownload = it.localQwenDownload.copy(
                            modelName = modelName,
                            isDownloading = false,
                            error = e.message ?: "Download failed",
                        ),
                        settingsMessage = "Local Qwen download failed: ${e.message ?: "unknown error"}",
                    )
                }
            }
        }
    }

    private fun refreshLocalQwenModelStatus(modelName: String) {
        if (modelName !in DefaultLlmValues.modelOptions(LlmProvider.LOCAL_QWEN)) return
        if (localQwenDownloadJob?.isActive == true) return
        localQwenStatusJob?.cancel()
        localQwenStatusJob = viewModelScope.launch {
            localQwenModelDownloader.observeStatus(modelName).collect { downloadState ->
                _uiState.update {
                    it.copy(
                        configStatus = it.configStatus.withLocalQwenDownloadState(downloadState),
                        localQwenDownload = downloadState.toUiState(),
                    )
                }
            }
        }
    }

    fun prepareMcpSettings() {
        val currentName = _uiState.value.toolCapabilitySettings.mcpServerName
        val currentUrl = _uiState.value.toolCapabilitySettings.mcpHttpUrl
        _uiState.update {
            it.copy(
                mcpSettingsName = currentName,
                mcpSettingsUrl = currentUrl,
                mcpSettingsMessage = null,
            )
        }
    }

    fun updateMcpSettingsUrl(value: String) {
        _uiState.update { it.copy(mcpSettingsUrl = value, mcpSettingsMessage = null) }
    }

    fun updateMcpSettingsName(value: String) {
        _uiState.update { it.copy(mcpSettingsName = value, mcpSettingsMessage = null) }
    }

    fun saveMcpSettings() {
        val name = _uiState.value.mcpSettingsName.trim()
        val url = _uiState.value.mcpSettingsUrl.trim()
        if (url.isNotBlank() && name.isBlank()) {
            _uiState.update { it.copy(mcpSettingsMessage = "MCP name is required when a URL is set") }
            return
        }
        if (url.isNotBlank() && !url.startsWith("http://") && !url.startsWith("https://")) {
            _uiState.update { it.copy(mcpSettingsMessage = "MCP URL must start with http:// or https://") }
            return
        }
        viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            try {
                AppLogger.info(LogTags.Config, "mcp_settings_save_started", "serverName" to name, "hasUrl" to url.isNotBlank())
                appPreferences.setMcpServerName(name)
                appPreferences.setMcpHttpUrl(url)
                _uiState.update {
                    it.copy(
                        mcpSettingsName = name,
                        mcpSettingsUrl = url,
                        mcpSettingsMessage = null,
                    )
                }
                AppLogger.info(
                    LogTags.Config,
                    "mcp_settings_save_completed",
                    "serverName" to name,
                    "hasUrl" to url.isNotBlank(),
                    "durationMs" to (System.currentTimeMillis() - startedAt),
                )
            } catch (e: Exception) {
                AppLogger.error(
                    LogTags.Config,
                    e,
                    "mcp_settings_save_failed",
                    "serverName" to name,
                    "hasUrl" to url.isNotBlank(),
                    "durationMs" to (System.currentTimeMillis() - startedAt),
                )
                _uiState.update { it.copy(mcpSettingsMessage = "Save MCP settings failed. Please try again.") }
            }
        }
    }

    private fun updateBooleanPreference(
        name: String,
        value: Boolean,
        update: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            try {
                AppLogger.info(LogTags.Config, "preference_update_started", "name" to name, "value" to value)
                update()
                AppLogger.info(LogTags.Config, "preference_update_completed", "name" to name, "value" to value)
            } catch (e: Exception) {
                AppLogger.error(LogTags.Config, e, "preference_update_failed", "name" to name, "value" to value)
                _uiState.update { it.copy(error = "Update setting failed. Please try again.") }
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

    private fun maybeShowPermissionPrompt(call: com.xiaoqi.companion.core.companion.model.AgentToolCall) {
        if (call.name != "create_local_reminder" || call.status != ToolCallStatus.SUCCEEDED) return
        val result = call.resultJson.orEmpty()
        if (!result.contains("exact_alarm_permission_missing")) return

        _uiState.update { state ->
            state.copy(
                permissionPrompt = ChatPermissionPrompt(
                    type = ChatPermissionType.EXACT_ALARM,
                    title = "Enable precise reminders",
                    message = "Aura needs the Android alarm permission to deliver exact reminders on time.",
                    primaryActionLabel = "Open settings",
                )
            )
        }
    }

    private fun ToolCallSnapshot.toChatToolCall(): ChatToolCall =
        ChatToolCall(
            id = id,
            toolName = toolName,
            toolStatus = status,
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
            imageUri = imageBase64?.let { "data:image/jpeg;base64,$it" },
        )

    private fun MemoryEntity.toChatMemory(): ChatMemory =
        ChatMemory(
            id = id,
            content = content,
            type = type.name,
            importance = importance,
            source = source,
            timestamp = timestamp,
        )

    private fun ReminderEntity.toChatReminder(): ChatReminder =
        ChatReminder(
            id = id,
            title = title,
            message = message,
            triggerAtMillis = triggerAtMillis,
            exact = exact,
            status = status,
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
        if (provider == LlmProvider.LOCAL_QWEN && isReady) {
            ChatConfigStatus(
                label = "${provider.name} · $modelName",
                isReady = false,
                detail = "正在检查本地模型",
                provider = provider,
                modelName = modelName,
                baseUrl = baseUrl,
            )
        } else if (isReady) {
            ChatConfigStatus(
                label = "${provider.name} · $modelName",
                isReady = true,
                detail = "模型已就绪",
                provider = provider,
                modelName = modelName,
                baseUrl = baseUrl,
            )
        } else {
            ChatConfigStatus(
                label = "${provider.name} · ${modelName.ifBlank { "未选择模型" }}",
                isReady = false,
                detail = missingReason ?: "模型配置未完成",
                provider = provider,
                modelName = modelName,
                baseUrl = baseUrl,
            )
        }

    private fun ChatConfigStatus.withLocalQwenDownloadState(
        downloadState: LocalQwenModelDownloadState,
    ): ChatConfigStatus {
        if (provider != LlmProvider.LOCAL_QWEN || modelName != downloadState.modelName) {
            return this
        }
        return when {
            downloadState.isInstalled -> copy(
                isReady = true,
                detail = "本地模型已安装",
            )
            downloadState.isDownloading -> copy(
                isReady = false,
                detail = "本地模型下载中",
            )
            else -> copy(
                isReady = false,
                detail = downloadState.error ?: "请先下载本地模型",
            )
        }
    }

    private fun LocalQwenModelDownloadState.toUiState(): LocalQwenDownloadUiState =
        LocalQwenDownloadUiState(
            modelName = modelName,
            isInstalled = isInstalled,
            isDownloading = isDownloading,
            progress = progress,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            message = message,
            error = error,
        )

    private fun defaultBaseUrl(provider: LlmProvider): String =
        DefaultLlmValues.defaultBaseUrl(provider)

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

    private fun ChatUiState.withPresence(): ChatUiState {
        val latestTool = toolCalls.firstOrNull()
        val hasStreamingAssistant = messages.any {
            it.role == "ASSISTANT" && it.isStreaming && it.content.isNotBlank()
        }
        return copy(
            presence = presenceController.derive(
                PresenceInputs(
                    mood = status.mood,
                    intensity = status.intensity,
                    relationshipLevel = status.relationshipLevel,
                    isLoading = isLoading || isPreparingImage,
                    isStreaming = hasStreamingAssistant,
                    latestToolName = latestTool?.toolName,
                    latestToolStatus = latestTool?.toolStatus,
                    reaction = presenceReaction,
                    hasError = error != null,
                    hasInputText = inputText.isNotBlank(),
                    hasPendingImage = pendingImage != null,
                    isConfigReady = configStatus.isReady,
                    configDetail = configStatus.detail,
                    recentMemoryCount = memories.size,
                )
            )
        )
    }

    private fun triggerPresenceReaction(reaction: PresenceReaction) {
        var accepted = false
        _uiState.update { state ->
            if (shouldShowPresenceReaction(reaction, state)) {
                accepted = true
                state.copy(presenceReaction = reaction).withPresence()
            } else {
                state
            }
        }
        if (accepted) {
            clearPresenceReactionLater(reaction)
        }
    }

    private fun shouldShowPresenceReaction(
        reaction: PresenceReaction,
        state: ChatUiState,
    ): Boolean {
        val now = System.currentTimeMillis()
        val canShow = presenceReactionPolicy.shouldShow(
            candidate = reaction,
            currentState = state.withPresence().presence,
            nowMillis = now,
            lastShownAtMillis = lastPresenceReactionAtMillis[reaction],
        )
        if (canShow) {
            lastPresenceReactionAtMillis[reaction] = now
        }
        return canShow
    }

    private fun clearPresenceReactionLater(reaction: PresenceReaction? = _uiState.value.presenceReaction) {
        val targetReaction = reaction ?: return
        presenceReactionJob?.cancel()
        presenceReactionJob = viewModelScope.launch {
            delay(presenceReactionPolicy.displayDurationMillis(targetReaction))
            _uiState.update { it.copy(presenceReaction = null).withPresence() }
        }
    }
}
