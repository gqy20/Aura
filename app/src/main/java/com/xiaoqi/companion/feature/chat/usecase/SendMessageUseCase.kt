package com.xiaoqi.companion.feature.chat.usecase

import com.xiaoqi.companion.core.companion.CompanionRuntime
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
import com.xiaoqi.companion.data.repository.MemoryRepository
import com.xiaoqi.companion.feature.chat.ChatConfigStatus
import com.xiaoqi.companion.feature.chat.ChatImageAttachment
import com.xiaoqi.companion.feature.chat.ChatMessage
import com.xiaoqi.companion.feature.chat.ChatPermissionPrompt
import com.xiaoqi.companion.feature.chat.ChatPermissionType
import com.xiaoqi.companion.feature.chat.ChatUiState
import com.xiaoqi.companion.feature.chat.CompanionStatus
import com.xiaoqi.companion.feature.chat.StreamingMarkdownChunker
import com.xiaoqi.companion.feature.chat.mapper.after
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
 * - **流式响应** → 90ms 批量渲染 + 30s 空闲超时
 * - **AgentEvent.MemorySaved** → 触发 Presence 反应 + 状态提示
 * - **create_local_reminder 缺权限** → 设置精确闹钟权限提示
 * - **Complete** → 持久化 [CompanionStatus](mood/intensity/relationshipLevel) 到 AgentStateDao
 *
 * 状态写入:
 * - 通过 [update] 回调修改 [ChatUiState],**不持有** MutableStateFlow。
 *   这让 UseCase 可独立于 VM 单元测试。
 * - 副 coroutine(idleTimeoutJob / streamingRenderJob) 通过 [scope] 启动,与调用方生命周期一致。
 */
class SendMessageUseCase @Inject constructor(
    private val runtime: CompanionRuntime,
    private val imageProcessor: com.xiaoqi.companion.feature.chat.ChatImageProcessor,
    private val toolDisplayRegistry: ToolDisplayRegistry,
    private val presenceController: PresenceController,
    private val agentStateDao: com.xiaoqi.companion.data.db.dao.AgentStateDao,
    private val memoryRepository: MemoryRepository,
) {
    companion object {
        private const val DEFAULT_SESSION_ID = "default"
        private const val STREAMING_IDLE_TIMEOUT_MS = 30_000L
        private const val STREAMING_RENDER_BATCH_MS = 90L
        private const val STREAMING_RENDER_BATCH_CHARS = 48
        private const val IMAGE_ONLY_PROMPT = "我想给你看这张图片。先说说你看到了什么，再自然地回应我。"
    }

    suspend operator fun invoke(
        text: String,
        pendingImage: ChatImageAttachment?,
        configStatus: ChatConfigStatus,
        scope: CoroutineScope,
        update: (ChatUiState.() -> ChatUiState) -> Unit,
    ) {
        val trimmed = text.trim()
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
        val streamingChunker = StreamingMarkdownChunker()
        val pendingStreamingContent = StringBuilder()
        var streamingRenderJob: Job? = null
        var idleTimeoutJob: Job? = null
        var timedOut = false

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

        fun flushStreamingContent() {
            if (pendingStreamingContent.isEmpty()) return
            val renderState = streamingChunker.append(pendingStreamingContent.toString())
            pendingStreamingContent.clear()
            updateAssistantMessage(assistantId, update) {
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
            streamingRenderJob = scope.launch {
                delay(STREAMING_RENDER_BATCH_MS)
                flushStreamingContent()
                streamingRenderJob = null
            }
        }

        fun finishWithError(message: String) {
            idleTimeoutJob?.cancel()
            streamingRenderJob?.cancel()
            flushStreamingContent()
            update {
                val hasPartialAssistantReply = assistantContent.isNotBlank() ||
                    messages.any { it.id == assistantId && it.content.isNotBlank() }
                val updatedMessages = if (hasPartialAssistantReply) {
                    messages.map { msg ->
                        if (msg.id == assistantId) {
                            msg.copy(
                                isStreaming = false,
                                renderBlocks = emptyList(),
                                renderDraft = "",
                                isRenderDraftCode = false,
                                toolStatus = "回复未完整完成",
                                toolStatusType = ToolCallStatus.FAILED,
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

        fun updateAssistantToolStatus(status: String, type: ToolCallStatus? = null) {
            updateAssistantMessage(assistantId, update) { it.copy(toolStatus = status, toolStatusType = type) }
        }

        try {
            update {
                copy(
                    messages = messages + ChatMessage(
                        id = assistantId,
                        role = "ASSISTANT",
                        content = "",
                        isStreaming = true,
                    )
                )
            }

            val userInput = if (pendingImage != null) {
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

            runtime.send(userInput).collect { event ->
                when (event) {
                    is AgentEvent.Streaming -> {
                        resetIdleTimer()
                        assistantContent += event.delta
                        pendingStreamingContent.append(event.delta)
                        scheduleStreamingRender()
                    }
                    is AgentEvent.ToolCallUpdated -> {
                        maybeShowPermissionPrompt(event.call, update)
                        updateAssistantToolStatus(
                            toolDisplayRegistry.label(event.call.name, event.call.status),
                            event.call.status,
                        )
                    }
                    is AgentEvent.ToolStarted -> {
                        updateAssistantToolStatus(
                            toolDisplayRegistry.label(event.name, ToolCallStatus.STARTED),
                            ToolCallStatus.STARTED,
                        )
                    }
                    is AgentEvent.ToolFinished -> {
                        updateAssistantToolStatus(
                            toolDisplayRegistry.label(event.name, ToolCallStatus.SUCCEEDED),
                            ToolCallStatus.SUCCEEDED,
                        )
                    }
                    is AgentEvent.MemorySaved -> {
                        updateAssistantToolStatus(
                            if (event.count == 1) "已记住 1 条" else "已记住 ${event.count} 条",
                            ToolCallStatus.SUCCEEDED,
                        )
                        scope.launch {
                            presenceController.reactionFor(PresenceEvent.MemorySaved(event.count))
                        }
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
                        updateAssistantMessage(assistantId, update) {
                            it.copy(
                                content = event.parsed.textReply,
                                isStreaming = false,
                                renderBlocks = emptyList(),
                                renderDraft = "",
                                isRenderDraftCode = false,
                            )
                        }
                        update {
                            val nextStatus = status.after(
                                mood = event.parsed.emotionSignal.mood,
                                intensity = event.parsed.emotionSignal.intensity,
                                affinityDelta = event.parsed.interactionSignal.affinityDelta,
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
            is AgentError.ApiError -> error.message
            is AgentError.ParseError -> error.reason
        }

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
}
