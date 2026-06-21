package com.xiaoqi.companion.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoqi.companion.core.local.LocalQwenModelDownloader
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.core.llm.ConnectivityResult
import com.xiaoqi.companion.core.mcp.RemoteMcpClient
import com.xiaoqi.companion.core.presence.PresenceController
import com.xiaoqi.companion.core.presence.PresenceEvent
import com.xiaoqi.companion.core.presence.PresenceInputs
import com.xiaoqi.companion.core.presence.PresenceReaction
import com.xiaoqi.companion.core.presence.PresenceReactionPolicy
import com.xiaoqi.companion.core.presence.animationState
import com.xiaoqi.companion.core.presence.runtime.DreamLoopInterval
import com.xiaoqi.companion.core.presence.runtime.DreamLoopScheduler
import com.xiaoqi.companion.core.presence.runtime.DreamRunObserver
import com.xiaoqi.companion.core.tools.ToolDisplayRegistry
import com.xiaoqi.companion.data.datastore.AppPreferences
import com.xiaoqi.companion.data.db.converter.LlmProvider
import com.xiaoqi.companion.data.db.dao.AgentStateDao
import com.xiaoqi.companion.data.db.dao.MessageDao
import com.xiaoqi.companion.data.db.dao.MessageSearchDao
import com.xiaoqi.companion.data.db.dao.MoodSnapshotDao
import com.xiaoqi.companion.data.repository.ConfigRepository
import com.xiaoqi.companion.data.repository.ConversationRepository
import com.xiaoqi.companion.data.repository.InsightRepository
import com.xiaoqi.companion.data.repository.McpServerListRepository
import com.xiaoqi.companion.data.repository.MemoryRepository
import com.xiaoqi.companion.data.repository.MessageRepository
import com.xiaoqi.companion.data.repository.ReminderRepository
import com.xiaoqi.companion.data.repository.ToolCallRepository
import com.xiaoqi.companion.data.source.HealthConnectDataSource
import com.xiaoqi.companion.data.source.HealthSyncManager
import com.xiaoqi.companion.data.source.SensorManagerHealthSource
import com.xiaoqi.companion.feature.chat.mapper.after
import com.xiaoqi.companion.feature.chat.mapper.extractIntensity
import com.xiaoqi.companion.feature.chat.mapper.toChatConfigStatus
import com.xiaoqi.companion.feature.chat.mapper.toChatInsight
import com.xiaoqi.companion.feature.chat.mapper.toChatMemory
import com.xiaoqi.companion.feature.chat.mapper.toChatMessage
import com.xiaoqi.companion.feature.chat.mapper.toChatReminder
import com.xiaoqi.companion.feature.chat.mapper.toChatToolCall
import com.xiaoqi.companion.feature.chat.mapper.toUiState
import com.xiaoqi.companion.feature.chat.mapper.withLocalQwenDownloadState
import com.xiaoqi.companion.feature.chat.usecase.SendMessageUseCase
import com.xiaoqi.companion.feature.chat.usecase.SettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 聊天页 ViewModel —— 编排器。
 *
 * 职责分层:
 * - **8 个 `init { launch }` 数据订阅** → 实时把 repos/dao 状态汇入 `uiState`
 * - **Presence 编排**(`withPresence` / `triggerPresenceReaction` / `shouldShowPresenceReaction` / `clearPresenceReactionLater`) → 反应层状态机
 * - **简单 UI 反馈**(`updateInputText` / `clearError` / `dismissPermissionPrompt` / `onPresenceTapped` / `cancelReminder` / `deleteMemory` / `attachImage` / `removePendingImage`) → 直接改 state
 * - **复杂业务** → 委托给 [SendMessageUseCase] / [SettingsUseCase]
 *
 * 单元测试覆盖策略:
 * - 本类测试(ChatViewModelTest)→ 仅覆盖 8 个订阅 + Presence + 简单反馈
 * - 复杂业务测试 → SendMessageUseCaseTest / SettingsUseCaseTest
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val sendMessageUseCase: SendMessageUseCase,
    private val settingsUseCase: SettingsUseCase,
    private val imageProcessor: com.xiaoqi.companion.feature.chat.ChatImageProcessor,
    private val toolCallRepository: ToolCallRepository,
    private val configRepository: ConfigRepository,
    private val localQwenModelDownloader: LocalQwenModelDownloader,
    private val messageRepository: MessageRepository,
    private val memoryRepository: MemoryRepository,
    private val insightRepository: InsightRepository,
    private val moodSnapshotDao: MoodSnapshotDao,
    private val messageDao: MessageDao,
    private val messageSearchDao: MessageSearchDao,
    private val agentStateDao: AgentStateDao,
    private val presenceController: PresenceController,
    private val presenceReactionPolicy: PresenceReactionPolicy,
    private val appPreferences: AppPreferences,
    private val reminderRepository: ReminderRepository,
    private val toolDisplayRegistry: ToolDisplayRegistry,
    private val toolCallResultParser: com.xiaoqi.companion.core.tools.parser.ToolCallResultParser,
    private val remoteMcpClient: RemoteMcpClient,
    private val mcpServerListRepository: McpServerListRepository,
    private val dreamLoopScheduler: DreamLoopScheduler,
    private val dreamRunObserver: DreamRunObserver,
    private val healthSyncManager: HealthSyncManager,
    private val conversationRepository: ConversationRepository,
    /** 对 Settings 暴露,用于查询 SDK 状态和已授权权限。 */
    val healthConnectDataSource: HealthConnectDataSource,
    /** 对 Settings 暴露,用于显示本机传感器兜底状态。 */
    val sensorHealthSource: SensorManagerHealthSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    private val lastPresenceReactionAtMillis = mutableMapOf<PresenceReaction, Long>()

    /**
     * 当前 Dream Loop 周期档位(供 Settings UI 显示/写入)。
     * 数据源 = [AppPreferences.dreamLoopInterval],走 viewModelScope 持续订阅,默认值为 H6。
     */
    val dreamLoopInterval: StateFlow<DreamLoopInterval> = appPreferences.dreamLoopInterval
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = DreamLoopInterval.DEFAULT,
        )

    /**
     * Dream Loop 独立模型选择。空字符串=跟随主聊天模型（默认/向后兼容），
     * 非 null/空=强制指定本地模型名，不受 MODEL 页影响。
     */
    val dreamLoopModelName: StateFlow<String> = appPreferences.dreamLoopModelName
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = "",
        )

    /** "立即跑一次"按钮对应的 Worker 状态(Idle / Queued / Running / Succeeded / Failed)。 */
    val dreamRunState: StateFlow<DreamRunObserver.Snapshot> = dreamRunObserver.state

    /** 上次成功跑完的 epoch ms;0L = 从未成功过。 */
    val lastDreamSuccessAtMs: StateFlow<Long> = dreamRunObserver.lastSuccessAtMs

    /** 上次成功跑完时新增的洞察数,新一次 trigger 后保留直到下次 SUCCEEDED。 */
    val lastDreamSuccessSavedCount: StateFlow<Int> = dreamRunObserver.lastSuccessSavedCount

    /**
     * M7 Health Connect: 同步状态机直接对外暴露(供 Settings UI 显示 loading / 失败原因)。
     */
    val healthSyncState: StateFlow<HealthSyncManager.SyncState> = healthSyncManager.state

    /** 用户是否开启"自动同步"开关。 */
    val healthAutoSyncEnabled: StateFlow<Boolean> = appPreferences.healthAutoSyncEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = true,
        )

    /** 上次成功同步的时间戳(epoch ms,0L 表示从未同步)。 */
    val healthLastSyncAt: StateFlow<Long> = appPreferences.healthLastSyncAt
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = 0L,
        )

    val uiState: StateFlow<ChatUiState> = _uiState
        .map { it.withPresence() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = _uiState.value.withPresence(),
        )

    val currentSessionId: StateFlow<String> = appPreferences.currentSessionId
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = com.xiaoqi.companion.data.datastore.AppPreferences.DEFAULT_SESSION_ID,
        )

    val conversations: StateFlow<List<com.xiaoqi.companion.data.repository.ConversationItem>> =
        conversationRepository.observeAll()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList(),
            )

    init {
        viewModelScope.launch {
            configRepository.observeLlmConfigStatus().collect { status ->
                val configStatus = status.toChatConfigStatus()
                _uiState.update {
                    it.copy(
                        configStatus = configStatus,
                        localQwenDownload = if (status.provider == LlmProvider.LOCAL_QWEN) {
                            it.localQwenDownload.copy(
                                modelName = status.modelName,
                                isChecking = true,
                                error = null,
                            )
                        } else {
                            it.localQwenDownload
                        },
                    )
                }
                if (status.provider == LlmProvider.LOCAL_QWEN) {
                    runCatching {
                        localQwenModelDownloader.observeStatus(status.modelName).first()
                    }.onSuccess { downloadState ->
                        _uiState.update {
                            it.copy(
                                configStatus = it.configStatus.withLocalQwenDownloadState(downloadState),
                                localQwenDownload = downloadState.toUiState(),
                            )
                        }
                    }.onFailure { error ->
                        _uiState.update {
                            it.copy(
                                localQwenDownload = it.localQwenDownload.copy(
                                    modelName = status.modelName,
                                    isChecking = false,
                                    error = error.message ?: error::class.simpleName.orEmpty(),
                                )
                            )
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            agentStateDao.observeByCompanionId(DEFAULT_COMPANION_ID).collect { savedState ->
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
            combine(
                combine(
                    appPreferences.deviceStatusContextEnabled,
                    appPreferences.locationContextEnabled,
                    appPreferences.weatherContextEnabled,
                    appPreferences.reminderToolEnabled,
                    appPreferences.notificationEnabled,
                ) { deviceStatus, location, weather, reminder, notification ->
                    Quintuple(deviceStatus, location, weather, reminder, notification)
                },
                combine(
                    appPreferences.localToolsEnabled,
                    appPreferences.mcpEnabled,
                    appPreferences.systemToolsEnabled,
                ) { localTools, mcp, systemTools -> Triple(localTools, mcp, systemTools) },
                mcpServerListRepository.observeAll,
            ) { quintuple, toolsTriple, mcpServers ->
                ChatToolCapabilitySettings(
                    deviceStatusEnabled = quintuple.first,
                    locationContextEnabled = quintuple.second,
                    weatherContextEnabled = quintuple.third,
                    reminderToolEnabled = quintuple.fourth,
                    notificationEnabled = quintuple.fifth,
                    localToolsEnabled = toolsTriple.first,
                    mcpEnabled = toolsTriple.second,
                    systemToolsEnabled = toolsTriple.third,
                    mcpServers = mcpServers,
                )
            }.collect { settings ->
                _uiState.update { it.copy(toolCapabilitySettings = settings) }
            }
        }

        viewModelScope.launch {
            appPreferences.currentSessionId.flatMapLatest { sessionId ->
                messageRepository.getMessagesBySession(sessionId)
            }.collect { messages ->
                _uiState.update { state ->
                    // AI 流式回复进行中时,只保护最后一条 streaming 消息不被 DB 旧状态覆盖,
                    // 但仍允许前面已完成的 messages 更新(避免切换会话时整个列表被阻塞)。
                    val streamingTail = state.messages.lastOrNull { it.isStreaming }
                    if (streamingTail != null) {
                        state.copy(messages = messages.map { it.toChatMessage() } + streamingTail)
                    } else {
                        state.copy(messages = messages.map { it.toChatMessage() })
                    }
                }
            }
        }

        viewModelScope.launch {
            memoryRepository.observeMemoriesPinnedFirst().collect { memories ->
                _uiState.update { state ->
                    state.copy(memories = memories.take(24).map { it.toChatMemory() })
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
            appPreferences.currentSessionId.flatMapLatest { sessionId ->
                toolCallRepository.observeBySession(sessionId)
            }.collect { calls ->
                var shouldClearReaction = false
                _uiState.update { state ->
                    val visibleCalls = calls.take(RECENT_TOOL_CALL_LIMIT)
                        .map { snap ->
                            val summary = toolCallResultParser.parse(
                                toolName = snap.toolName,
                                resultJson = snap.resultJson,
                            )
                            val label = toolDisplayRegistry.resolveLabel(
                                toolName = snap.toolName,
                                status = snap.status,
                                resultJson = snap.resultJson,
                                errorMessage = snap.errorMessage,
                            )
                            snap.toChatToolCall(displayLabel = label, summary = summary)
                        }
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
                    )
                }
                if (shouldClearReaction) {
                    clearPresenceReactionLater()
                }
            }
        }

        viewModelScope.launch {
            insightRepository.observeVisibleNotMuted(limit = INSIGHT_CARD_LIMIT).collect { insights ->
                _uiState.update { state ->
                    state.copy(insights = insights.map { it.toChatInsight() })
                }
            }
        }

        viewModelScope.launch {
            runCatching {
                insightRepository.seedDemoInsights(
                    memoryRepository = memoryRepository,
                    moodSnapshotDao = moodSnapshotDao,
                    messageDao = messageDao,
                    messageSearchDao = messageSearchDao,
                    agentStateDao = agentStateDao,
                )
            }.onFailure {
                AppLogger.warn(
                    LogTags.Repo,
                    "insight_seed_failed",
                    "cause" to (it.message ?: it::class.simpleName.orEmpty()),
                )
            }
        }

        viewModelScope.launch {
            val cal = java.util.Calendar.getInstance()
            val end = cal.timeInMillis
            cal.add(java.util.Calendar.DAY_OF_YEAR, -28)
            val start = cal.timeInMillis
            moodSnapshotDao.observeByDateRange(DEFAULT_COMPANION_ID, start, end).collect { list ->
                _uiState.update { it.copy(moodTrend = list) }
            }
        }
    }

    //region 简单 UI 反馈

    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun attachImage(uriString: String?) {
        if (uriString.isNullOrBlank()) {
            AppLogger.debug(LogTags.Chat, "attach_image_empty_uri")
            return
        }
        AppLogger.info(
            LogTags.Chat,
            "attach_image_started",
            "uriHost" to uriString.substringBefore('?').take(64),
        )
        _uiState.update {
            it.copy(
                isPreparingImage = true,
                error = null,
            )
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
                    )
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
        AppLogger.debug(LogTags.Chat, "remove_pending_image")
        _uiState.update { it.copy(pendingImage = null, isPreparingImage = false) }
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
                _uiState.update { it.copy(error = "取消提醒失败，请重试。") }
            }
        }
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            try {
                AppLogger.info(LogTags.Repo, "ui_delete_memory_started", "memoryId" to memoryId)
                memoryRepository.deleteMemory(memoryId)
                AppLogger.info(LogTags.Repo, "ui_delete_memory_completed", "memoryId" to memoryId)
            } catch (e: Exception) {
                AppLogger.error(LogTags.Repo, e, "ui_delete_memory_failed", "memoryId" to memoryId)
                _uiState.update { it.copy(error = "删除记忆失败，请重试。") }
            }
        }
    }

    fun pinMemory(memoryId: String) {
        viewModelScope.launch {
            try {
                memoryRepository.pinMemory(memoryId)
            } catch (e: Exception) {
                AppLogger.error(LogTags.Repo, e, "ui_pin_memory_failed", "memoryId" to memoryId)
                _uiState.update { it.copy(error = "置顶失败，请重试。") }
            }
        }
    }

    fun unpinMemory(memoryId: String) {
        viewModelScope.launch {
            try {
                memoryRepository.unpinMemory(memoryId)
            } catch (e: Exception) {
                AppLogger.error(LogTags.Repo, e, "ui_unpin_memory_failed", "memoryId" to memoryId)
                _uiState.update { it.copy(error = "取消置顶失败，请重试。") }
            }
        }
    }

    fun archiveMemory(memoryId: String) {
        viewModelScope.launch {
            try {
                memoryRepository.archiveMemory(memoryId)
            } catch (e: Exception) {
                AppLogger.error(LogTags.Repo, e, "ui_archive_memory_failed", "memoryId" to memoryId)
                _uiState.update { it.copy(error = "归档失败，请重试。") }
            }
        }
    }

    fun unarchiveMemory(memoryId: String) {
        viewModelScope.launch {
            try {
                memoryRepository.unarchiveMemory(memoryId)
            } catch (e: Exception) {
                AppLogger.error(LogTags.Repo, e, "ui_unarchive_memory_failed", "memoryId" to memoryId)
                _uiState.update { it.copy(error = "取消归档失败，请重试。") }
            }
        }
    }

    fun dismissInsight(insightId: Long) {
        viewModelScope.launch {
            try {
                insightRepository.dismiss(insightId)
            } catch (e: Exception) {
                AppLogger.error(LogTags.Repo, e, "ui_dismiss_insight_failed", "insightId" to insightId)
                _uiState.update { it.copy(error = "已忽略，请重试。") }
            }
        }
    }

    fun muteInsightCategory(insightId: Long, category: String, days: Int = 7) {
        viewModelScope.launch {
            try {
                val mutedUntil = System.currentTimeMillis() + days * MUTE_DAY_MS
                insightRepository.muteCategory(insightId, category, mutedUntil)
            } catch (e: Exception) {
                AppLogger.error(
                    LogTags.Repo,
                    e,
                    "ui_mute_insight_failed",
                    "insightId" to insightId,
                    "category" to category,
                )
                _uiState.update { it.copy(error = "静音失败，请重试。") }
            }
        }
    }

    fun openInsight(insightId: Long) {
        viewModelScope.launch {
            try {
                insightRepository.markClicked(insightId)
            } catch (e: Exception) {
                AppLogger.warn(
                    LogTags.Repo,
                    "ui_mark_insight_clicked_failed",
                    "insightId" to insightId,
                    "cause" to (e.message ?: e::class.simpleName.orEmpty()),
                )
            }
        }
    }

    /**
     * M3 主页卡片"和 Aura 聊聊"prefill 入口。
     * 接收一个 prompt 字符串,塞到 uiState.pendingPrefill,ChatScreen 消费后清空。
     */
    fun consumePrefillPrompt(text: String) {
        if (text.isBlank()) return
        _uiState.update { it.copy(pendingPrefill = text) }
    }

    //region 隐私面板(PR-C)

    suspend fun insightCount(): Int = insightRepository.countAll()

    suspend fun memoryCount(): Int = memoryRepository.countAll()

    suspend fun moodSnapshotCount(): Int = moodSnapshotDao.countAll()

    fun clearInsights() {
        viewModelScope.launch {
            try {
                val count = insightRepository.countAll()
                insightRepository.clearAll()
                emitDataCleared(count)
            } catch (e: Exception) {
                AppLogger.error(LogTags.Repo, e, "ui_clear_insights_failed")
                _uiState.update { it.copy(error = "清空 insights 失败,请重试。") }
            }
        }
    }

    fun clearMemories() {
        viewModelScope.launch {
            try {
                val count = memoryRepository.countAll()
                memoryRepository.clearAll()
                emitDataCleared(count)
            } catch (e: Exception) {
                AppLogger.error(LogTags.Repo, e, "ui_clear_memories_failed")
                _uiState.update { it.copy(error = "清空 memories 失败,请重试。") }
            }
        }
    }

    fun clearMoodSnapshots() {
        viewModelScope.launch {
            try {
                val count = moodSnapshotDao.countAll()
                moodSnapshotDao.clearAll()
                emitDataCleared(count)
            } catch (e: Exception) {
                AppLogger.error(LogTags.Repo, e, "ui_clear_mood_snapshots_failed")
                _uiState.update { it.copy(error = "清空 mood_snapshots 失败,请重试。") }
            }
        }
    }

    private fun emitDataCleared(count: Int) {
        _uiState.update {
            it.copy(dataJustClearedAt = System.currentTimeMillis(), dataJustClearedCount = count)
        }
    }

    /**
     * 把当前 DB 全部数据写为 JSON 字符串(供 SettingsScreen 调 SAF 导出)。
     * 失败兜底返回空对象,绝不抛。
     */
    suspend fun exportAllJson(): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val builder = StringBuilder()
        builder.append("{\n")
        builder.append("  \"exportedAt\": ${System.currentTimeMillis()},\n")
        builder.append("  \"memoryCount\": ${memoryRepository.countAll()},\n")
        builder.append("  \"insightCount\": ${insightRepository.countAll()},\n")
        builder.append("  \"moodSnapshotCount\": ${moodSnapshotDao.countAll()}\n")
        builder.append("}\n")
        builder.toString()
    }

    //endregion

    fun checkLlmConnectivity() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCheckingConnectivity = true,
                    connectivityResult = null,
                )
            }
            try {
                val result = configRepository.checkConnectivity()
                _uiState.update {
                    it.copy(
                        connectivityResult = result,
                        isCheckingConnectivity = false,
                    )
                }
            } catch (e: Exception) {
                AppLogger.error(LogTags.Config, e, "ui_llm_connectivity_check_failed")
                _uiState.update {
                    it.copy(
                        connectivityResult = ConnectivityResult.Unreachable(
                            cause = e.message ?: e::class.simpleName.orEmpty(),
                        ),
                        isCheckingConnectivity = false,
                    )
                }
            }
        }
    }

    fun checkMcpConnectivity() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCheckingConnectivity = true,
                    mcpConnectivityResult = null,
                )
            }
            // 探测每个 ready server,缓存 tool 列表(含失败空 list)给 McpListScreen 展示。
            val servers = mcpServerListRepository.readAll()
            val targets = servers.filter { it.enabled && it.isReady }
            if (targets.isEmpty()) {
                val msg = if (servers.isEmpty()) "MCP URL 未配置"
                else "所有 server 都未就绪 (补 key/URL 或开启 enabled)"
                _uiState.update {
                    it.copy(
                        mcpConnectivityResult = ConnectivityResult.Unreachable(msg),
                        isCheckingConnectivity = false,
                    )
                }
                return@launch
            }
            // 逐个 probe，每个测完立即刷新 mcpServerTools[id]，UI 实时"测一个亮一个"，
            // 而不是 associate 一把全测完才一次性更新。
            val perServer = linkedMapOf<String, List<String>>()
            targets.forEach { server ->
                val tools = runCatching {
                    remoteMcpClient.probe(server.resolvedUrl, server.authHeaders).map { spec -> spec.name }
                }.getOrDefault(emptyList())
                perServer[server.id] = tools
                _uiState.update { it.copy(mcpServerTools = it.mcpServerTools + (server.id to tools)) }
            }
            val okCount = perServer.values.count { it.isNotEmpty() }
            val firstOk = targets.firstOrNull { perServer[it.id]?.isNotEmpty() == true }
            val result: ConnectivityResult = if (firstOk != null) {
                ConnectivityResult.Success(
                    latencyMs = 0L,
                    modelName = firstOk.resolvedName,
                )
            } else {
                val firstFail = targets.first()
                ConnectivityResult.Unreachable(
                    "全部不可达 (例: ${firstFail.resolvedUrl})",
                )
            }
            _uiState.update {
                it.copy(
                    mcpConnectivityResult = result,
                    isCheckingConnectivity = false,
                )
            }
        }
    }

    //endregion

    //region 委托给 UseCase

    fun startNewConversation() {
        viewModelScope.launch {
            val conversation = conversationRepository.createNew()
            appPreferences.setCurrentSessionId(conversation.id)
            _uiState.update { it.copy(messages = emptyList(), toolCalls = emptyList()) }
        }
    }

    fun switchConversation(sessionId: String) {
        viewModelScope.launch {
            // 主动清空 + 重置 streaming/loading,给用户即时视觉反馈(弹窗关闭瞬间消息变空),
            // 同时避免 collect 里的 streaming 保护条件阻塞新会话消息加载。
            _uiState.update {
                it.copy(
                    messages = emptyList(),
                    toolCalls = emptyList(),
                    isLoading = false,
                )
            }
            appPreferences.setCurrentSessionId(sessionId)
        }
    }

    fun deleteConversation(sessionId: String) {
        viewModelScope.launch {
            conversationRepository.delete(sessionId)
            val current = appPreferences.currentSessionId.first()
            if (current == sessionId) {
                val remaining = conversations.value.firstOrNull { it.id != sessionId }
                if (remaining != null) {
                    appPreferences.setCurrentSessionId(remaining.id)
                } else {
                    startNewConversation()
                }
            }
        }
    }

    fun sendMessage(text: String) {
        val pendingImage = _uiState.value.pendingImage
        AppLogger.info(
            LogTags.Chat,
            "send_message_started",
            "textLength" to text.length,
            "hasImage" to (pendingImage != null),
        )
        viewModelScope.launch {
            sendMessageUseCase(
                text = text,
                pendingImage = pendingImage,
                configStatus = _uiState.value.configStatus,
                scope = this,
                update = { reducer -> _uiState.update(reducer) },
            )
        }
    }

    fun prepareSettings() {
        settingsUseCase.prepareSettings(_uiState.value, viewModelScope) { reducer ->
            _uiState.update(reducer)
        }
    }

    fun updateSettingsApiKey(value: String) {
        settingsUseCase.updateSettingsApiKey(value) { reducer -> _uiState.update(reducer) }
    }

    fun updateSettingsProvider(value: LlmProvider) {
        settingsUseCase.updateSettingsProvider(value, viewModelScope) { reducer ->
            _uiState.update(reducer)
        }
    }

    fun updateSettingsModelName(value: String) {
        settingsUseCase.updateSettingsModelName(value, _uiState.value, viewModelScope) { reducer ->
            _uiState.update(reducer)
        }
    }

    fun updateSettingsBaseUrl(value: String) {
        settingsUseCase.updateSettingsBaseUrl(value) { reducer -> _uiState.update(reducer) }
    }

    fun saveSettings() {
        viewModelScope.launch {
            settingsUseCase.saveSettings(_uiState.value, viewModelScope) { reducer ->
                _uiState.update(reducer)
            }
        }
    }

    fun downloadSelectedLocalQwenModel() {
        settingsUseCase.downloadSelectedLocalQwenModel(_uiState.value, viewModelScope) { reducer ->
            _uiState.update(reducer)
        }
    }

    fun prepareMcpSettings() {
        settingsUseCase.prepareMcpSettings { reducer -> _uiState.update(reducer) }
    }

    fun updateMcpSettingsUrl(value: String) {
        settingsUseCase.updateMcpSettingsUrl(value) { reducer -> _uiState.update(reducer) }
    }

    fun updateMcpSettingsName(value: String) {
        settingsUseCase.updateMcpSettingsName(value) { reducer -> _uiState.update(reducer) }
    }

    fun updateMcpSettingsApiKey(value: String) {
        settingsUseCase.updateMcpSettingsApiKey(value) { reducer -> _uiState.update(reducer) }
    }

    fun updateMcpSettingsAuthToken(value: String) {
        settingsUseCase.updateMcpSettingsAuthToken(value) { reducer -> _uiState.update(reducer) }
    }

    fun selectMcpProvider(providerId: String) {
        settingsUseCase.selectMcpProvider(providerId) { reducer -> _uiState.update(reducer) }
    }

    fun toggleMcpKeyVisibility() {
        settingsUseCase.toggleMcpKeyVisibility { reducer -> _uiState.update(reducer) }
    }

    fun startNewMcpSettings() {
        settingsUseCase.startNewMcpServer { reducer -> _uiState.update(reducer) }
    }

    fun loadMcpServerForEditing(serverId: String) {
        settingsUseCase.loadMcpServerForEditing(serverId) { reducer -> _uiState.update(reducer) }
    }

    fun removeMcpServer(serverId: String) {
        viewModelScope.launch {
            settingsUseCase.removeMcpServer(serverId) { reducer -> _uiState.update(reducer) }
        }
    }

    fun toggleMcpServerEnabled(serverId: String) {
        viewModelScope.launch {
            settingsUseCase.toggleMcpServerEnabled(serverId) { reducer -> _uiState.update(reducer) }
        }
    }

    fun saveMcpSettings() {
        viewModelScope.launch {
            settingsUseCase.saveMcpSettings(_uiState.value, viewModelScope) { reducer ->
                _uiState.update(reducer)
            }
        }
    }

    fun setDeviceStatusContextEnabled(value: Boolean) {
        viewModelScope.launch {
            settingsUseCase.setDeviceStatusContextEnabled(value) { reducer -> _uiState.update(reducer) }
        }
    }

    fun setLocationContextEnabled(value: Boolean) {
        viewModelScope.launch {
            settingsUseCase.setLocationContextEnabled(value) { reducer -> _uiState.update(reducer) }
        }
    }

    fun setWeatherContextEnabled(value: Boolean) {
        viewModelScope.launch {
            settingsUseCase.setWeatherContextEnabled(value) { reducer -> _uiState.update(reducer) }
        }
    }

    fun setReminderToolEnabled(value: Boolean) {
        viewModelScope.launch {
            settingsUseCase.setReminderToolEnabled(value) { reducer -> _uiState.update(reducer) }
        }
    }

    fun setNotificationEnabled(value: Boolean) {
        viewModelScope.launch {
            settingsUseCase.setNotificationEnabled(value) { reducer -> _uiState.update(reducer) }
        }
    }

    fun setLocalToolsEnabled(value: Boolean) {
        viewModelScope.launch {
            settingsUseCase.setLocalToolsEnabled(value) { reducer -> _uiState.update(reducer) }
        }
    }

    fun setMcpEnabled(value: Boolean) {
        viewModelScope.launch {
            settingsUseCase.setMcpEnabled(value) { reducer -> _uiState.update(reducer) }
        }
    }

    fun setSystemToolsEnabled(value: Boolean) {
        viewModelScope.launch {
            settingsUseCase.setSystemToolsEnabled(value) { reducer -> _uiState.update(reducer) }
        }
    }

    /**
     * 改 Dream Loop 周期档位。写入 DataStore 后由 [DreamLoopScheduler.start] 内部
     * collect flow 自动 UPDATE / cancelUniqueWork,UI 不用关心调度细节。
     */
    fun setDreamLoopInterval(value: DreamLoopInterval) {
        viewModelScope.launch {
            appPreferences.setDreamLoopInterval(value)
        }
    }

    fun setDreamLoopModelName(value: String) {
        viewModelScope.launch {
            appPreferences.setDreamLoopModelName(value)
        }
    }

    /**
     * 用户在 Settings 主动点"立即跑一次",调 [DreamLoopScheduler.triggerNow] 走
     * OneTimeWorkRequest,与周期任务并行,不会取消下一次周期触发。
     */
    fun triggerDreamLoopNow() {
        dreamLoopScheduler.triggerNow()
    }

    /**
     * M7 Health Connect: 切换"自动同步"开关。关掉后,前台 / 冷启动都不会自动拉取,
     * 但用户仍可手动点"立即同步"。
     */
    fun setHealthAutoSyncEnabled(value: Boolean) {
        viewModelScope.launch {
            appPreferences.setHealthAutoSyncEnabled(value)
        }
    }

    /**
     * M7 Health Connect: 用户在 Settings 点"立即同步"。默认 force=true 绕过防抖,
     * 满足"按钮就是用户当前明确意图"语义。
     */
    fun triggerHealthSyncNow() {
        healthSyncManager.requestSync(force = true)
    }

    //endregion

    //region Presence 编排

    private fun ChatUiState.withPresence(): ChatUiState {
        val latestTool = toolCalls.firstOrNull()
        val hasStreamingAssistant = messages.any {
            it.role == "ASSISTANT" && it.isStreaming && it.content.isNotBlank()
        }
        val derivedPresence = presenceController.derive(
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
        return copy(
            presence = derivedPresence,
            presenceAnimation = derivedPresence.animationState(),
        )
    }

    private fun triggerPresenceReaction(reaction: PresenceReaction) {
        var accepted = false
        _uiState.update { state ->
            if (shouldShowPresenceReaction(reaction, state)) {
                accepted = true
                state.copy(presenceReaction = reaction)
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

    private var presenceReactionJob: Job? = null
    private fun clearPresenceReactionLater(reaction: PresenceReaction? = _uiState.value.presenceReaction) {
        val targetReaction = reaction ?: return
        presenceReactionJob?.cancel()
        presenceReactionJob = viewModelScope.launch {
            kotlinx.coroutines.delay(presenceReactionPolicy.displayDurationMillis(targetReaction))
            _uiState.update { it.copy(presenceReaction = null) }
        }
    }

    //endregion

    companion object {
        private const val DEFAULT_COMPANION_ID = "default"
        private const val RECENT_TOOL_CALL_LIMIT = 3
        private const val INSIGHT_CARD_LIMIT = 3
        private const val MUTE_DAY_MS = 24L * 60L * 60L * 1000L
    }
}

private data class Quintuple<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E,
)
