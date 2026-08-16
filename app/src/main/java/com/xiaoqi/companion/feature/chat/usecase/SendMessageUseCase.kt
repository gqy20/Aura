package com.xiaoqi.companion.feature.chat.usecase

import com.xiaoqi.companion.core.companion.CompanionRuntime
import com.xiaoqi.companion.core.companion.EmotionStateMachine
import com.xiaoqi.companion.core.companion.RelationshipModel
import com.xiaoqi.companion.core.companion.model.AgentError
import com.xiaoqi.companion.core.companion.model.AgentEvent
import com.xiaoqi.companion.core.companion.model.AgentToolCall
import com.xiaoqi.companion.core.companion.model.ToolCallStatus
import com.xiaoqi.companion.core.companion.model.UserInput
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogFieldSanitizer
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.core.presence.PresenceController
import com.xiaoqi.companion.core.presence.PresenceEvent
import com.xiaoqi.companion.core.tools.ToolDisplayRegistry
import com.xiaoqi.companion.data.db.entity.AgentStateEntity
import com.xiaoqi.companion.data.db.converter.LlmProvider
import com.xiaoqi.companion.data.repository.MemoryRepository
import com.xiaoqi.companion.feature.chat.ChatConfigStatus
import com.xiaoqi.companion.feature.chat.ChatImageAttachment
import com.xiaoqi.companion.feature.chat.ChatMessage
import com.xiaoqi.companion.feature.chat.ChatMessageCompletionState
import com.xiaoqi.companion.feature.chat.ChatToolStep
import com.xiaoqi.companion.feature.chat.PerformanceInfo
import com.xiaoqi.companion.feature.chat.ChatPermissionPrompt
import com.xiaoqi.companion.feature.chat.ChatPermissionType
import com.xiaoqi.companion.feature.chat.ChatUiState
import com.xiaoqi.companion.feature.chat.CompanionStatus
import com.xiaoqi.companion.feature.chat.StreamingMarkdownChunker
import com.xiaoqi.companion.feature.chat.mapper.after
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * 发送一条聊天消息并消费 [CompanionRuntime] 事件流。
 *
 * 行为契约:
 * - **空消息 + 无图片** → 立即返回,无副作用
 * - **配置未就绪** → 设置 [ChatUiState.error] 并返回(不发请求)
 * - **Vision 输入** → 自动准备 base64 并发送 [UserInput.Vision]
 * - **纯文本** → 发送 [UserInput.Text]
 * - **流式响应** → 33ms ticker 节奏渲染 + 30s 空闲超时
 * - **AgentEvent.MemorySaved** → 触发 Presence 反应 + 状态提示
 * - **create_local_reminder 缺权限** → 设置精确闹钟权限提示
 * - **Complete** → 持久化 [CompanionStatus](mood/intensity/relationshipLevel) 到 AgentStateDao
 *
 * 状态写入:
 * - 通过 [update] 回调修改 [ChatUiState],**不持有** MutableStateFlow。
 *   这让 UseCase 可独立于 VM 单元测试。
 * - 副 coroutine(idleTimeoutJob / streamingTickerJob) 通过 [scope] 启动,与调用方生命周期一致。
 */
class SendMessageUseCase @Inject constructor(
    private val runtime: CompanionRuntime,
    private val imageProcessor: com.xiaoqi.companion.feature.chat.ChatImageProcessor,
    private val toolDisplayRegistry: ToolDisplayRegistry,
    private val presenceController: PresenceController,
    private val agentStateDao: com.xiaoqi.companion.data.db.dao.AgentStateDao,
    private val memoryRepository: MemoryRepository,
    private val emotionMachine: EmotionStateMachine,
    private val relationshipModel: RelationshipModel,
) {
    companion object {
        private const val DEFAULT_SESSION_ID = "default"
        private const val STREAMING_IDLE_TIMEOUT_MS = 30_000L
        // 流式渲染是节奏驱动而非到达驱动：delta 进队列，ticker 每 33ms 按预算出字，
        // 网络抖动的"一坨坨"到达被摊平成匀速流出。预算随积压加速(积压≥96 字符时
        // 每 tick 排空 1/4)，排空即真实速度，不整体拖慢回复。
        private const val STREAMING_TICK_MS = 33L
        private const val STREAMING_BASELINE_CHARS_PER_TICK = 4
        private const val STREAMING_TICKER_PARK_IDLE_TICKS = 12
        // 首包立即上屏的字符上限：让"在动"即刻可见，又不把大 chunk 一次倒出
        private const val STREAMING_LEADING_FLUSH_CHARS = 16
        private const val IMAGE_ONLY_PROMPT = "我想给你看这张图片。先说说你看到了什么，再自然地回应我。"
        private const val LOCAL_GENERATION_STATUS = "本地模型加载并生成中"
        private const val VISION_READING_STATUS = "正在看图"
        private const val TOOL_FAILED_STEP_LABEL = "工具未完成"
    }

    suspend operator fun invoke(
        text: String,
        pendingImage: ChatImageAttachment?,
        configStatus: ChatConfigStatus,
        scope: CoroutineScope,
        update: (ChatUiState.() -> ChatUiState) -> Unit,
    ) {
        val trimmed = text.trim()
        val startedAt = System.currentTimeMillis()
        if (trimmed.isEmpty() && pendingImage == null) return
        if (!configStatus.isReady) {
            update { copy(error = configStatus.detail.ifBlank { "模型配置未完成" }) }
            return
        }

        val requestId = UUID.randomUUID().toString()
        AppLogger.info(
            LogTags.Chat,
            "message_send_started",
            "requestHash" to LogFieldSanitizer.hash(requestId),
            "textLength" to trimmed.length,
            "hasImage" to (pendingImage != null),
        )

        val userPrompt = trimmed.ifBlank { IMAGE_ONLY_PROMPT }
        val userDisplayContent = trimmed.ifBlank { "分享了一张图片" }
        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = "USER",
            content = userDisplayContent,
            imageUri = pendingImage?.uriString,
        )
        update {
            copy(
                messages = messages + userMsg,
                inputText = "",
                pendingImage = null,
                isLoading = true,
                error = null,
            )
        }

        val assistantId = UUID.randomUUID().toString()
        var assistantContent = ""
        var streamingChunker = StreamingMarkdownChunker()
        val pendingStreamingContent = StringBuilder()
        var streamingTickerJob: Job? = null
        var hasFlushedLeadingDelta = false
        var idleTimeoutJob: Job? = null
        var timedOut = false
        val toolCallIds = mutableListOf<String>()
        var toolStepCounter = 0
        var sawToolStep = false
        // Progress 类状态(看图/本地加载/阶段提示)走 toolStatus 单值;
        // 工具调用过程走 toolSteps 时间线,两者不再互相覆盖。
        var lastAssistantToolStatus: String? = null
        var isProgressStatus = false

        fun resetIdleTimer() {
            timedOut = false
            idleTimeoutJob?.cancel()
            idleTimeoutJob = scope.launch {
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

        fun flushStreamingContent(maxChars: Int = Int.MAX_VALUE) {
            if (pendingStreamingContent.isEmpty()) return
            val chunk = if (maxChars < pendingStreamingContent.length) {
                val head = pendingStreamingContent.substring(0, maxChars)
                pendingStreamingContent.delete(0, maxChars)
                head
            } else {
                val all = pendingStreamingContent.toString()
                pendingStreamingContent.setLength(0)
                all
            }
            val renderState = streamingChunker.append(chunk)
            updateAssistantMessage(assistantId, update) {
                it.copy(
                    content = renderState.rawText,
                    renderBlocks = renderState.committedBlocks,
                    renderDraft = renderState.draftText,
                    isRenderDraftCode = renderState.isDraftCode,
                )
            }
        }

        fun ensureStreamingTicker() {
            if (streamingTickerJob?.isActive == true) return
            streamingTickerJob = scope.launch {
                var idleTicks = 0
                while (idleTicks < STREAMING_TICKER_PARK_IDLE_TICKS) {
                    delay(STREAMING_TICK_MS)
                    val backlog = pendingStreamingContent.length
                    if (backlog == 0) {
                        idleTicks += 1
                        continue
                    }
                    idleTicks = 0
                    val budget = when {
                        backlog >= 96 -> backlog / 4
                        backlog >= 48 -> 12
                        else -> STREAMING_BASELINE_CHARS_PER_TICK
                    }
                    flushStreamingContent(budget)
                }
            }
        }

        fun finishWithError(message: String) {
            idleTimeoutJob?.cancel()
            streamingTickerJob?.cancel()
            flushStreamingContent()
            update {
                // 意图段/工具时间线也算部分回复:工具阶段后的失败保留过程叙事,
                // 不把消息整条删成光秃秃的错误卡
                val hasPartialAssistantReply = assistantContent.isNotBlank() ||
                    messages.any { msg ->
                        msg.id == assistantId && (
                            msg.content.isNotBlank() ||
                                msg.intentText.isNotBlank() ||
                                msg.toolSteps.isNotEmpty()
                            )
                    }
                val updatedMessages = if (hasPartialAssistantReply) {
                    messages.map { msg ->
                        if (msg.id == assistantId) {
                            msg.copy(
                                isStreaming = false,
                                renderBlocks = emptyList(),
                                renderDraft = "",
                                isRenderDraftCode = false,
                                completionState = ChatMessageCompletionState.FAILED,
                            )
                        } else {
                            msg
                        }
                    }
                } else {
                    messages.filter { it.id != assistantId }
                }
                copy(
                    messages = updatedMessages,
                    isLoading = false,
                    error = message,
                )
            }
        }

        fun finishCancelled() {
            idleTimeoutJob?.cancel()
            streamingTickerJob?.cancel()
            flushStreamingContent()
            update {
                val updatedMessages = messages.mapNotNull { message ->
                    if (message.id != assistantId) return@mapNotNull message
                    if (message.content.isBlank()) return@mapNotNull null
                    message.copy(
                        isStreaming = false,
                        renderBlocks = emptyList(),
                        renderDraft = "",
                        isRenderDraftCode = false,
                        completionState = ChatMessageCompletionState.STOPPED,
                    )
                }
                copy(
                    messages = updatedMessages,
                    isLoading = false,
                    error = null,
                )
            }
        }

        fun updateAssistantToolStatus(
            status: String,
            type: ToolCallStatus? = null,
            progress: Boolean = false,
        ) {
            lastAssistantToolStatus = status
            isProgressStatus = progress
            updateAssistantMessage(assistantId, update) { it.copy(toolStatus = status, toolStatusType = type) }
        }

        // 工具步骤时间线：复合调用按序累积,每步 spinner→✓/✗。
        // ToolStarted/ToolFinished 无 callId,靠"name + STARTED 未终结"匹配;
        // ToolCallUpdated 有 callId,优先精确匹配,其次认领同名未终结的合成步骤,
        // 保证两种事件顺序下同一次调用只占一步。
        fun upsertToolStepFromCall(call: AgentToolCall) {
            sawToolStep = true
            val now = System.currentTimeMillis()
            val callId = call.callId?.takeIf { it.isNotBlank() }
            updateAssistantMessage(assistantId, update) { msg ->
                val steps = msg.toolSteps.toMutableList()
                val index = steps.indexOfFirst { it.callId != null && it.callId == callId }
                    .takeIf { it >= 0 }
                    ?: steps.indexOfLast {
                        it.name == call.name && it.status == ToolCallStatus.STARTED && it.callId == null
                    }.takeIf { it >= 0 }
                val label = if (call.status == ToolCallStatus.FAILED) {
                    TOOL_FAILED_STEP_LABEL
                } else {
                    toolDisplayRegistry.resolveLabel(
                        toolName = call.name,
                        status = call.status,
                        resultJson = call.resultJson,
                        errorMessage = call.errorMessage,
                    )
                }
                if (index != null) {
                    val prev = steps[index]
                    val duration = if (call.status == ToolCallStatus.STARTED) {
                        prev.durationMs
                    } else {
                        (now - prev.startedAtMs).coerceAtLeast(0)
                    }
                    steps[index] = prev.copy(
                        callId = callId ?: prev.callId,
                        label = label,
                        status = call.status,
                        durationMs = duration,
                    )
                } else {
                    toolStepCounter += 1
                    steps += ChatToolStep(
                        id = callId ?: "step-${call.name}-$toolStepCounter",
                        name = call.name,
                        callId = callId,
                        label = label,
                        status = call.status,
                        startedAtMs = now,
                        durationMs = if (call.status == ToolCallStatus.STARTED) null else 0L,
                    )
                }
                msg.copy(toolSteps = steps)
            }
        }

        fun appendToolStartedStep(name: String) {
            sawToolStep = true
            val now = System.currentTimeMillis()
            updateAssistantMessage(assistantId, update) { msg ->
                val hasInFlightStep = msg.toolSteps.any {
                    it.name == name && it.status == ToolCallStatus.STARTED
                }
                if (hasInFlightStep) {
                    msg
                } else {
                    toolStepCounter += 1
                    msg.copy(
                        toolSteps = msg.toolSteps + ChatToolStep(
                            id = "step-$name-$toolStepCounter",
                            name = name,
                            label = toolDisplayRegistry.label(name, ToolCallStatus.STARTED),
                            status = ToolCallStatus.STARTED,
                            startedAtMs = now,
                        )
                    )
                }
            }
        }

        fun finishToolStep(name: String, status: ToolCallStatus) {
            sawToolStep = true
            val now = System.currentTimeMillis()
            updateAssistantMessage(assistantId, update) { msg ->
                val index = msg.toolSteps.indexOfLast {
                    it.name == name && it.status == ToolCallStatus.STARTED
                }
                if (index < 0) {
                    msg
                } else {
                    val steps = msg.toolSteps.toMutableList()
                    val prev = steps[index]
                    steps[index] = prev.copy(
                        label = toolDisplayRegistry.label(name, status),
                        status = status,
                        durationMs = (now - prev.startedAtMs).coerceAtLeast(0),
                    )
                    msg.copy(toolSteps = steps)
                }
            }
        }

        try {
            update {
                copy(
                    messages = messages + ChatMessage(
                        id = assistantId,
                        role = "ASSISTANT",
                        content = "",
                        isStreaming = true,
                        toolStatus = if (configStatus.provider == LlmProvider.LOCAL_QWEN) {
                            LOCAL_GENERATION_STATUS
                        } else if (pendingImage != null) {
                            VISION_READING_STATUS
                        } else {
                            null
                        },
                        toolStatusType = if (pendingImage != null) ToolCallStatus.STARTED else null,
                    )
                )
            }

            val userInput = if (pendingImage != null) {
                AppLogger.info(
                    LogTags.Chat,
                    "vision_image_prepared",
                    "requestHash" to LogFieldSanitizer.hash(requestId),
                    "mediaType" to pendingImage.mediaType,
                    "base64Length" to pendingImage.imageBase64.length,
                )
                // M4:vision memory 持久化（fire-and-forget,不阻塞消息发送)。
                // 失败仅 log,不影响主流程 — 见 MemoryRepository.saveVisionMemory 注释。
                scope.launch {
                    try {
                        memoryRepository.saveVisionMemory(
                            summary = trimmed,
                            imageBase64 = pendingImage.imageBase64,
                            imageMediaType = pendingImage.mediaType,
                            sourceMessageId = userMsg.id,
                        )
                        AppLogger.info(
                            LogTags.Chat,
                            "vision_memory_saved",
                            "requestHash" to LogFieldSanitizer.hash(requestId),
                            "mediaType" to pendingImage.mediaType,
                        )
                    } catch (e: Exception) {
                        AppLogger.warn(
                            LogTags.Chat,
                            "vision_memory_save_failed",
                            "requestHash" to LogFieldSanitizer.hash(requestId),
                            "cause" to (e.message ?: e::class.simpleName.orEmpty()),
                        )
                    }
                }
                UserInput.Vision(
                    text = userPrompt,
                    imageBase64 = pendingImage.imageBase64,
                    mediaType = pendingImage.mediaType,
                    displayText = userDisplayContent,
                )
            } else {
                UserInput.Text(userPrompt)
            }

            runtime.send(userInput)
                .transformWhile { event ->
                    emit(event)
                    event !is AgentEvent.Complete && event !is AgentEvent.Error
                }
                .collect { event ->
                when (event) {
                    is AgentEvent.Streaming -> {
                        resetIdleTimer()
                        if (isProgressStatus) {
                            isProgressStatus = false
                            lastAssistantToolStatus = null
                            updateAssistantMessage(assistantId, update) {
                                it.copy(toolStatus = null, toolStatusType = null)
                            }
                        }
                        AppLogger.info(
                            LogTags.Chat,
                            "streaming_delta",
                            "rawLen" to event.delta.length,
                            "rawPreview" to event.delta.take(80),
                        )
                        assistantContent += event.delta
                        pendingStreamingContent.append(event.delta)
                        if (!hasFlushedLeadingDelta) {
                            hasFlushedLeadingDelta = true
                            flushStreamingContent(STREAMING_LEADING_FLUSH_CHARS)
                        }
                        ensureStreamingTicker()
                    }
                    AgentEvent.StreamingReset -> {
                        streamingTickerJob?.cancel()
                        streamingTickerJob = null
                        pendingStreamingContent.clear()
                        assistantContent = ""
                        streamingChunker = StreamingMarkdownChunker()
                        hasFlushedLeadingDelta = false
                        // 不清屏：工具调用前流出的过渡文本降级为"意图段"保留展示，
                        // 最终回答接着流。runtime 不持久化这段文本,所以只存内存字段。
                        updateAssistantMessage(assistantId, update) {
                            if (it.content.isBlank()) {
                                it
                            } else {
                                it.copy(
                                    intentText = if (it.intentText.isBlank()) {
                                        it.content
                                    } else {
                                        it.intentText + "\n" + it.content
                                    },
                                    content = "",
                                    renderBlocks = emptyList(),
                                    renderDraft = "",
                                    isRenderDraftCode = false,
                                )
                            }
                        }
                    }
                    is AgentEvent.Progress -> {
                        updateAssistantToolStatus(
                            formatProgress(event.message.ifBlank { event.stage }),
                            ToolCallStatus.STARTED,
                            progress = true,
                        )
                    }
                    is AgentEvent.RemoteStatus -> {
                        AppLogger.info(
                            LogTags.Chat,
                            "remote_agent_status",
                            "requestHash" to LogFieldSanitizer.hash(requestId),
                            "runId" to LogFieldSanitizer.hash(event.runId),
                            "status" to event.status,
                        )
                    }
                    is AgentEvent.ToolCallUpdated -> {
                        maybeShowPermissionPrompt(event.call, update)
                        // 带 resultJson 的工具经 resolveLabel 产出"已创建提醒 · 吃药"
                        // 等带数据的步骤文案;没有 resultJson 或解析失败回退静态 label。
                        upsertToolStepFromCall(event.call)
                        // 记录 callId 到消息,让 detail panel 能反查具体 tool call
                        event.call.callId?.takeIf { it.isNotBlank() }?.let { id ->
                            if (id !in toolCallIds) {
                                toolCallIds += id
                                updateAssistantMessage(assistantId, update) {
                                    it.copy(toolCallIds = toolCallIds.toList())
                                }
                            }
                        }
                    }
                    is AgentEvent.ToolStarted -> {
                        appendToolStartedStep(event.name)
                    }
                    is AgentEvent.ToolFinished -> {
                        finishToolStep(event.name, ToolCallStatus.SUCCEEDED)
                    }
                    is AgentEvent.MemorySaved -> {
                        // 后置记忆抽取的"已记住 N 条"是 conversation 级 reflection 信号,
                        // 不挤占工具时间线/工具状态;没有工具活动时才以 chip 呈现。
                        if (!sawToolStep && lastAssistantToolStatus.isNullOrBlank()) {
                            updateAssistantToolStatus(
                                if (event.count == 1) "已记住 1 条" else "已记住 ${event.count} 条",
                                ToolCallStatus.SUCCEEDED,
                            )
                        }
                        scope.launch {
                            presenceController.reactionFor(PresenceEvent.MemorySaved(event.count))
                        }
                    }
                    is AgentEvent.Complete -> {
                        idleTimeoutJob?.cancel()
                        streamingTickerJob?.cancel()
                        flushStreamingContent()

                        val finalReply = event.textReply
                        AppLogger.info(
                            LogTags.Chat,
                            "complete_text_reply",
                            "len" to finalReply.length,
                            "preview" to finalReply.take(200),
                        )
                        val durationMs = System.currentTimeMillis() - startedAt
                        val estimatedTokens = estimateTokens(finalReply)
                        val perfInfo = PerformanceInfo(
                            durationMs = durationMs,
                            estimatedTokens = estimatedTokens,
                        )
                        AppLogger.info(
                            LogTags.Chat,
                            "message_send_completed",
                            "requestHash" to LogFieldSanitizer.hash(requestId),
                            "replyLength" to finalReply.length,
                            "durationMs" to durationMs,
                            "estimatedTokens" to estimatedTokens,
                            "tokensPerSecond" to perfInfo.tokensPerSecond,
                        )
                        update {
                            val persistedId = event.persistedMessageId
                            val transientMessage = messages.firstOrNull { it.id == assistantId }
                            val persistedMessage = persistedId?.let { id ->
                                messages.firstOrNull { it.id == id }
                            }
                            val source = transientMessage ?: persistedMessage ?: ChatMessage(
                                id = assistantId,
                                role = "ASSISTANT",
                                content = "",
                            )
                            val completedMessage = source.copy(
                                // 保持列表里已有的 id(transient 或 reconciler 换过的),
                                // 换 key 会让 LazyColumn 把完成的消息当新条目重淡入;
                                // DB 行 id 存 persistedId 供后续刷新对齐。
                                persistedId = persistedId ?: source.persistedId,
                                content = finalReply,
                                isStreaming = false,
                                renderBlocks = emptyList(),
                                renderDraft = "",
                                isRenderDraftCode = false,
                                toolStatus = if (source.toolStatus == LOCAL_GENERATION_STATUS) {
                                    null
                                } else if (source.toolStatus == VISION_READING_STATUS) {
                                    null
                                } else {
                                    source.toolStatus
                                },
                                performanceInfo = perfInfo,
                                completionState = null,
                            )
                            val completedIds = buildSet {
                                add(assistantId)
                                persistedId?.let(::add)
                            }
                            val firstCompletedIndex = messages.indexOfFirst { it.id in completedIds }
                            val remaining = messages.filterNot { it.id in completedIds }.toMutableList()
                            val insertionIndex = if (firstCompletedIndex < 0) {
                                remaining.size
                            } else {
                                messages.take(firstCompletedIndex).count { it.id !in completedIds }
                            }
                            remaining.add(insertionIndex.coerceAtMost(remaining.size), completedMessage)
                            copy(messages = remaining)
                        }
                        update {
                            val nextStatus = status.after(
                                mood = emotionMachine.currentMood,
                                intensity = emotionMachine.latestIntensity,
                                relationshipLevel = relationshipModel.currentLevel,
                            )
                            persistStatus(nextStatus, scope)
                            copy(
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
                finishWithError("回复超时，请重试。")
            }
        } catch (cancelled: CancellationException) {
            AppLogger.info(
                LogTags.Chat,
                "message_send_cancelled",
                "requestHash" to LogFieldSanitizer.hash(requestId),
            )
            finishCancelled()
            throw cancelled
        } catch (e: Exception) {
            AppLogger.error(
                LogTags.Chat,
                e,
                "message_send_failed",
                "requestHash" to LogFieldSanitizer.hash(requestId),
                "textLength" to trimmed.length,
            )
            finishWithError("发送失败，请重试。")
        }
    }

    private fun updateAssistantMessage(
        assistantId: String,
        update: (ChatUiState.() -> ChatUiState) -> Unit,
        transform: (ChatMessage) -> ChatMessage,
    ) {
        update {
            val updated = messages.map { msg ->
                if (msg.id == assistantId) transform(msg) else msg
            }
            copy(messages = updated)
        }
    }

    private fun maybeShowPermissionPrompt(
        call: AgentToolCall,
        update: (ChatUiState.() -> ChatUiState) -> Unit,
    ) {
        if (call.name != "create_local_reminder" || call.status != ToolCallStatus.SUCCEEDED) return
        val result = call.resultJson.orEmpty()
        if (!result.contains("exact_alarm_permission_missing")) return

        update {
            copy(
                permissionPrompt = ChatPermissionPrompt(
                    type = ChatPermissionType.EXACT_ALARM,
                    title = "开启精准提醒",
                    message = "Aura 需要「闹钟与提醒」权限才能准时提醒你。",
                    primaryActionLabel = "去设置",
                )
            )
        }
    }

    private fun formatError(error: AgentError): String =
        when (error) {
            is AgentError.NetworkTimeout -> "网络超时，请检查连接。"
            is AgentError.RateLimited -> "请求过于频繁，稍后再试。"
            is AgentError.ApiError -> "服务暂时没有响应，请重试。"
            is AgentError.ParseError -> "回复解析失败，请重试。"
        }

    private fun formatProgress(raw: String): String {
        val value = raw.trim()
        if (value.isBlank()) return "正在处理…"
        if (value.length > 48 || value.any { it == '{' || it == '}' || it == '[' || it == ']' }) {
            return "正在处理…"
        }
        return value
    }

    // 原始异常与 JSON 保留在工具详情中,时间线只展示"工具未完成"这类可行动文案。
    private fun persistStatus(status: CompanionStatus, scope: CoroutineScope) {
        scope.launch {
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

    /**
     * 粗估 token 数:中文字符≈1 token,英文≈4 char/token。
     * CJK Unified Ideographs 范围 U+4E00–U+9FFF 覆盖常用中文。
     */
    private fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        var cjk = 0
        var other = 0
        for (c in text) {
            if (c.code in 0x4E00..0x9FFF || c.code in 0x3000..0x303F) {
                cjk++
            } else if (!c.isWhitespace()) {
                other++
            }
        }
        return cjk + other / 4
    }
}
