package com.xiaoqi.companion.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoqi.companion.core.local.LocalQwenModelDownloader
import com.xiaoqi.companion.core.presence.runtime.LocalQwenExecutor
import com.xiaoqi.companion.core.insight.InsightPrompts
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
import com.xiaoqi.companion.data.repository.ToolCallSnapshot
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
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
    private val localQwenExecutor: LocalQwenExecutor,
    /** 对 Settings 暴露,用于查询 SDK 状态和已授权权限。 */
    val healthConnectDataSource: HealthConnectDataSource,
    /** 对 Settings 暴露,用于显示本机传感器兜底状态。 */
    val sensorHealthSource: SensorManagerHealthSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    private val lastPresenceReactionAtMillis = mutableMapOf<PresenceReaction, Long>()
    //region 对话后即时洞察:防抖 + 防重入
    private var lastPostChatInsightAt = 0L
    private var postChatInsightJob: Job? = null
    private var sendMessageJob: Job? = null
    private var generationCancelledByUser = false
    private val POST_CHAT_INSIGHT_COOLDOWN_MS = 3L * 60L * 1000L
    private val POST_CHAT_INSIGHT_MIN_MESSAGES = 2

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
                    val dbMessages = messages.map { it.toChatMessage() }
                    val streamingTail = state.messages.lastOrNull { it.isStreaming }
                    if (streamingTail != null) {
                        // 过滤掉与 streaming tail 内容重复的 DB 消息，
                        // 避免 saveAssistantMessage 先于 Complete 到达时出现两份相同文本。
                        // 用 contains 而非 == 兼容 stripStructuredTags：流式内容可能
                        // 带 [mood:xxx] 前缀标签，DB 版本已剥离，但主体文本一致。
                        val hasOverlap = dbMessages.any { dbMsg ->
                            dbMsg.role == "ASSISTANT" &&
                                dbMsg.content.isNotEmpty() &&
                                streamingTail.content.contains(dbMsg.content)
                        }
                        if (hasOverlap) {
                            // DB 已包含 streaming 回复，用 DB 版本接管。
                            // 但 DB 不存 performanceInfo / toolStatus 等内存态字段，
                            // 需要从 streaming tail 搬运过来，否则 tok/s pill 和工具状态消失。
                            val enriched = dbMessages.map { dbMsg ->
                                if (dbMsg.role == "ASSISTANT" &&
                                    dbMsg.content.isNotEmpty() &&
                                    streamingTail.content.contains(dbMsg.content)
                                ) {
                                    dbMsg.copy(
                                        performanceInfo = streamingTail.performanceInfo,
                                        toolStatus = streamingTail.toolStatus,
                                        toolStatusType = streamingTail.toolStatusType,
                                        toolCallIds = streamingTail.toolCallIds,
                                        completionState = streamingTail.completionState,
                                    )
                                } else {
                                    dbMsg
                                }
                            }
                            state.copy(messages = enriched)
                        } else {
                            // DB 尚未包含 streaming 回复（如切换会话），保留 tail
                            state.copy(messages = dbMessages + streamingTail)
                        }
                    } else {
                        // 无 streaming 消息时，从旧 state 搬运 performanceInfo。
                        val previousAssistants = state.messages.filter { previous ->
                            !previous.isStreaming && previous.role == "ASSISTANT"
                        }
                        val enriched = dbMessages.map { dbMsg ->
                            val previous = previousAssistants.lastOrNull { it.content == dbMsg.content }
                            if (previous == null || dbMsg.role != "ASSISTANT") {
                                dbMsg
                            } else {
                                dbMsg.copy(
                                    performanceInfo = previous.performanceInfo,
                                    toolStatus = previous.toolStatus,
                                    toolStatusType = previous.toolStatusType,
                                    toolCallIds = previous.toolCallIds,
                                    completionState = previous.completionState,
                                )
                            }
                        }
                        val prevAssistantWithPerf = previousAssistants.lastOrNull { it.performanceInfo != null }
                        // 调试:跟踪 performanceInfo 是否成功搬运
                        val dbHasPerf = enriched.any { it.performanceInfo != null }
                        val prevHasPerf = prevAssistantWithPerf != null
                        if (prevHasPerf && !dbHasPerf) {
                            AppLogger.warn(
                                LogTags.Chat,
                                "perf_info_lost_in_db_collector",
                                "prevContentLen" to (prevAssistantWithPerf?.content?.length ?: 0),
                                "dbAssistantCount" to dbMessages.count { it.role == "ASSISTANT" },
                                "contentMatch" to dbMessages.any { it.role == "ASSISTANT" && it.content == prevAssistantWithPerf?.content },
                            )
                        }
                        state.copy(messages = enriched)
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
                    fun ToolCallSnapshot.toUiCall(): ChatToolCall {
                        val summary = toolCallResultParser.parse(
                            toolName = toolName,
                            resultJson = resultJson,
                        )
                        val label = toolDisplayRegistry.resolveLabel(
                            toolName = toolName,
                            status = status,
                            resultJson = resultJson,
                            errorMessage = errorMessage,
                        )
                        return toChatToolCall(displayLabel = label, summary = summary)
                    }

                    val visibleCalls = calls.take(RECENT_TOOL_CALL_LIMIT).map { it.toUiCall() }
                    val mapCalls = calls.asSequence()
                        .filter { snap ->
                            val name = snap.toolName.lowercase()
                            "maps_" in name || "amap" in name
                        }
                        .map { it.toUiCall() }
                        .filter { it.mapInteraction != null }
                        .take(RECENT_MAP_TOOL_CALL_LIMIT)
                        .toList()
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
                        mapToolCalls = mapCalls,
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
                    mcpServerErrors = emptyMap(),
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
            val startedAt = System.currentTimeMillis()
            val outcomes = supervisorScope {
                targets.map { server ->
                    async {
                        val probeResult = runCatching {
                            remoteMcpClient.probe(server.resolvedUrl, server.authHeaders)
                                .map { spec -> spec.name }
                        }
                        val tools = probeResult.getOrDefault(emptyList())
                        val error = probeResult.exceptionOrNull()?.toMcpFailureLabel()
                        _uiState.update { state ->
                            state.copy(
                                mcpServerTools = state.mcpServerTools + (server.id to tools),
                                mcpServerErrors = if (error == null) {
                                    state.mcpServerErrors - server.id
                                } else {
                                    state.mcpServerErrors + (server.id to error)
                                },
                            )
                        }
                        McpProbeOutcome(tools, error)
                    }
                }.awaitAll()
            }
            val okCount = outcomes.count { it.tools.isNotEmpty() }
            val durationMs = System.currentTimeMillis() - startedAt
            val result: ConnectivityResult = if (okCount > 0) {
                ConnectivityResult.Success(
                    latencyMs = durationMs,
                    modelName = "$okCount/${targets.size} 可用",
                )
            } else {
                ConnectivityResult.Unreachable(
                    outcomes.firstNotNullOfOrNull { it.error } ?: "全部不可达",
                )
            }
            AppLogger.info(
                LogTags.Config,
                "ui_mcp_connectivity_check_completed",
                "targetCount" to targets.size,
                "okCount" to okCount,
                "durationMs" to durationMs,
                "parallel" to true,
            )
            _uiState.update {
                it.copy(
                    mcpConnectivityResult = result,
                    isCheckingConnectivity = false,
                )
            }
        }
    }

    private fun Throwable.toMcpFailureLabel(): String {
        val raw = message.orEmpty()
        return when {
            raw.contains("401") || raw.contains("token", ignoreCase = true) || raw.contains("令牌") ->
                "令牌无效或已过期"
            raw.contains("403") || raw.contains("forbidden", ignoreCase = true) ->
                "没有访问权限"
            raw.contains("412") || raw.contains("CAExited", ignoreCase = true) ||
                raw.contains("instance exited", ignoreCase = true) -> "远程服务启动失败"
            raw.contains("timeout", ignoreCase = true) || raw.contains("timed out", ignoreCase = true) ||
                raw.contains("超时") -> "连接超时"
            raw.isBlank() -> "连接失败"
            else -> raw.replace(Regex("https?://\\S+"), "远程服务").take(80)
        }
    }

    private data class McpProbeOutcome(
        val tools: List<String>,
        val error: String?,
    )

    //endregion

    //region 委托给 UseCase

    fun startNewConversation() {
        viewModelScope.launch {
            val conversation = conversationRepository.createNew()
            appPreferences.setCurrentSessionId(conversation.id)
            _uiState.update {
                it.copy(messages = emptyList(), toolCalls = emptyList(), mapToolCalls = emptyList())
            }
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
                    mapToolCalls = emptyList(),
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
        sendMessageJob?.takeIf { it.isActive }?.let { activeJob ->
            if (_uiState.value.isLoading) {
                AppLogger.debug(LogTags.Chat, "send_message_ignored_generation_active")
                return
            }
            AppLogger.warn(LogTags.Chat, "send_message_stale_job_recovered")
            activeJob.cancel()
            sendMessageJob = null
        }
        val pendingImage = _uiState.value.pendingImage
        AppLogger.info(
            LogTags.Chat,
            "send_message_started",
            "textLength" to text.length,
            "hasImage" to (pendingImage != null),
        )
        generationCancelledByUser = false
        val job = viewModelScope.launch {
            sendMessageUseCase(
                text = text,
                pendingImage = pendingImage,
                configStatus = _uiState.value.configStatus,
                scope = this,
                update = { reducer ->
                    _uiState.update { state ->
                        val prev = state
                        val next = state.reducer()
                        // 检测 isLoading: true → false(对话结束),触发本地模型即时洞察
                        if (prev.isLoading && !next.isLoading && !generationCancelledByUser) {
                            AppLogger.info(
                                LogTags.Chat,
                                "post_chat_trigger_check",
                                "prevLoading" to prev.isLoading,
                                "nextLoading" to next.isLoading,
                                "hasError" to (next.error != null),
                            )
                            if (next.error == null) {
                                triggerPostChatInsight()
                            }
                        }
                        next
                    }
                },
            )
        }
        sendMessageJob = job
        job.invokeOnCompletion {
            if (sendMessageJob === job) {
                sendMessageJob = null
                generationCancelledByUser = false
            }
        }
    }

    fun stopGenerating() {
        val job = sendMessageJob?.takeIf { it.isActive } ?: return
        generationCancelledByUser = true
        AppLogger.info(LogTags.Chat, "generation_stop_requested")
        job.cancel()
    }

    fun retryMessage(messageId: String) {
        if (_uiState.value.isLoading || _uiState.value.isPreparingImage) return
        val messages = _uiState.value.messages
        val targetIndex = messages.indexOfFirst { it.id == messageId }
        if (targetIndex < 0) return
        val userMessage = if (messages[targetIndex].role == "USER") {
            messages[targetIndex]
        } else {
            messages.subList(0, targetIndex).lastOrNull { it.role == "USER" }
        } ?: return
        resendUserMessage(userMessage)
    }

    fun retryLastMessage() {
        if (_uiState.value.isLoading || _uiState.value.isPreparingImage) return
        _uiState.value.messages.lastOrNull { it.role == "USER" }?.let(::resendUserMessage)
    }

    fun editMessage(messageId: String) {
        if (_uiState.value.isLoading) return
        val message = _uiState.value.messages.firstOrNull {
            it.id == messageId && it.role == "USER"
        } ?: return
        _uiState.update { it.copy(inputText = message.content, error = null) }
        message.imageUri?.let(::attachImage)
    }

    private fun resendUserMessage(message: ChatMessage) {
        _uiState.update { it.copy(error = null) }
        val imageUri = message.imageUri
        if (imageUri == null) {
            sendMessage(message.content)
            return
        }
        _uiState.update { it.copy(isPreparingImage = true) }
        viewModelScope.launch {
            try {
                val prepared = imageProcessor.prepare(imageUri)
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
                sendMessage(message.content)
            } catch (e: Exception) {
                AppLogger.warn(
                    LogTags.Chat,
                    "retry_image_prepare_failed",
                    "message" to (e.message ?: e::class.simpleName.orEmpty()),
                )
                _uiState.update {
                    it.copy(
                        pendingImage = null,
                        isPreparingImage = false,
                        error = "原图片无法重新读取，请重新选择图片。",
                    )
                }
            }
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

    //region 对话后即时洞察(本地模型核心价值展示)

    /**
     * 对话结束后触发本地模型即时反思。
     *
     * 策略:
     * - 防抖: 15 分钟内最多跑一次,避免连续对话时频繁分析
     * - 防重入: 上一次分析还没跑完不重复触发
     * - 延迟启动: 等待 3 秒再跑,避免与消息保存竞态
     */
    private fun triggerPostChatInsight() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastPostChatInsightAt
        AppLogger.info(
            LogTags.Chat,
            "post_chat_trigger_entered",
            "elapsedMs" to elapsed,
            "cooldownMs" to POST_CHAT_INSIGHT_COOLDOWN_MS,
            "jobActive" to (postChatInsightJob?.isActive == true),
        )
        if (elapsed < POST_CHAT_INSIGHT_COOLDOWN_MS) {
            AppLogger.info(LogTags.Chat, "post_chat_trigger_cooldown_skip", "remainingMs" to (POST_CHAT_INSIGHT_COOLDOWN_MS - elapsed))
            return
        }
        if (postChatInsightJob?.isActive == true) {
            AppLogger.info(LogTags.Chat, "post_chat_trigger_job_active_skip")
            return
        }
        postChatInsightJob = viewModelScope.launch {
            val installedModel = withContext(Dispatchers.IO) {
                runCatching { localQwenModelDownloader.findAnyInstalledModel() }.getOrNull()
            }
            if (installedModel == null) {
                AppLogger.info(LogTags.Chat, "post_chat_trigger_skipped_model_missing")
                return@launch
            }
            lastPostChatInsightAt = now
            AppLogger.info(
                LogTags.Chat,
                "post_chat_trigger_fired",
                "model" to installedModel,
            )
            kotlinx.coroutines.delay(3000L)
            runPostChatInsight()
        }
    }

    /**
     * 跑本地模型分析最近对话,产出即时洞察。
     *
     * 流程:
     * 1. 取当前会话最近 N 条消息(过滤空消息)
     * 2. 如果消息数 < 4,跳过(对话太短没有价值)
     * 3. 拼接成 prompt,调用 LocalQwenExecutor
     * 4. 解析输出,经 Validator 存入 DB
     * 5. 首页 Flow 自动感知新 insight 出现
     */
    private suspend fun runPostChatInsight() {
        val startedAt = System.currentTimeMillis()
        AppLogger.info(LogTags.Chat, "post_chat_insight_started")
        _uiState.update { it.copy(isInsightAnalyzing = true) }
        try {
            val sessionId = appPreferences.currentSessionId.first()
            val recentMessages = messageRepository.getRecentMessages(sessionId, limit = 12)
            val meaningful = recentMessages.filter { it.content.isNotBlank() }
            if (meaningful.size < POST_CHAT_INSIGHT_MIN_MESSAGES) {
                AppLogger.info(
                    LogTags.Chat,
                    "post_chat_insight_skipped_too_short",
                    "messageCount" to meaningful.size,
                )
                return
            }
            val conversationText = meaningful.joinToString("\n") { msg ->
                val role = if (msg.role.name == "USER") "用户" else "Aura"
                "$role: ${msg.content.take(300)}"
            }
            val result = localQwenExecutor.execute(
                LocalQwenExecutor.Request(
                    systemPrompt = InsightPrompts.conversationReflection,
                    userMessage = conversationText,
                    maxTokens = 300,
                    temperature = 0.5f,
                ),
            )
            AppLogger.info(
                LogTags.Chat,
                "post_chat_insight_raw_output",
                "textLength" to result.text.length,
                "latencyMs" to result.latencyMs,
                "preview" to result.text.take(120),
            )
            if (result.errorMessage != null || result.text.isBlank()) {
                AppLogger.warn(
                    LogTags.Chat,
                    "post_chat_insight_failed",
                    "error" to (result.errorMessage ?: "empty output"),
                )
                return
            }
            val drafts = localQwenExecutor.parsePatternDetectOutput(result.text)
            if (drafts.isEmpty()) {
                AppLogger.info(LogTags.Chat, "post_chat_insight_no_drafts")
                return
            }
            // 重写 triggerType 为 POST_CHAT,让 UI 区分来源
            val postChatDrafts = drafts.map { draft ->
                draft.copy(
                    triggerType = "POST_CHAT",
                    relevanceWindow = "刚刚",
                    // 小模型常输出 confidence=0,强制拉高到可用水位
                    confidence = draft.confidence.coerceAtLeast(0.5f),
                    // 把当前消息 id 作为 evidence,让 Validator 能通过
                    evidenceMessageIds = meaningful.takeLast(6).map { it.id },
                )
            }
            var savedCount = 0
            postChatDrafts.forEach { draft ->
                val id = insightRepository.saveIfValid(draft)
                if (id != null) savedCount++
            }
            AppLogger.info(
                LogTags.Chat,
                "post_chat_insight_completed",
                "draftsParsed" to drafts.size,
                "saved" to savedCount,
                "durationMs" to (System.currentTimeMillis() - startedAt),
            )
        } catch (e: Exception) {
            AppLogger.warn(
                LogTags.Chat,
                "post_chat_insight_error",
                "cause" to (e.message ?: e::class.simpleName.orEmpty()),
            )
        } finally {
            _uiState.update { it.copy(isInsightAnalyzing = false) }
        }
    }

    //endregion

    companion object {
        private const val DEFAULT_COMPANION_ID = "default"
        private const val RECENT_TOOL_CALL_LIMIT = 3
        private const val RECENT_MAP_TOOL_CALL_LIMIT = 64
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
