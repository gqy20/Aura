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
        // 流式 trailing 兜底窗口——8ms (~120Hz 节奏)。
        // 在 60Hz 屏幕上 vsync 会把相邻 flush 合并到同一帧,UI 仍按 16.67ms 节奏更新;
        // timer 频率加倍缩短"未达字符阈值的小增量"的兜底延迟。
        private const val STREAMING_RENDER_BATCH_MS = 8L
        private const val STREAMING_RENDER_BATCH_CHARS = 48
        // Leading flush 阈值——首字符到达后立即 flush 到 UI。
        private const val STREAMING_LEADING_FLUSH_CHARS = 1
        private const val IMAGE_ONLY_PROMPT = "我想给你看这张图片。先说说你看到了什么，再自然地回应我。"

        // LLM 输出的控制标签(与 OutputParser.allTagRegex 同源):
        //   [mood:happy][intensity:0.6][affinity:+0.02][topics:a,b][action:foo[text:bar]]
        // 这些是 LLM 给 parser 的协议,不是给用户看的正文。Streaming 路径
        // 直接累加原始 delta,需要在 UseCase 输出层过滤。
        //
        // 关键:流式 chunk 切在标签**中间**(例 `][`、`aff`、`inity`),简单的
        // per-delta `Regex.replace` 会让"[aff"这种"半个标签"被当成正文推
        // 进 draft 区,UI 就会显示成 "affinity...]" 半成品。改为**单遍状态机**:
        // 边扫描边记录'已发现 `[` 但未配对 `]`'的尾段,完整标签命中才吞掉,
        // 部分标签保留在 tail 等后续 chunk 续上。
        // 末尾未配对的 `[` 必须等流结束后再判定(Complete 事件里走一遍
        // flushTail 清空,Complete 阶段也走 parser 重新提取,这里不会
        // 损失真实标签)。
        private const val CONTROL_TAG_PREFIX = '['
        private const val CONTROL_TAG_SUFFIX = ']'
        // "半个标签"的预估上限:超过这个长度还没找到 ']',就不再等配对,
        // 把这段当成正文推出去(防止一个未闭合的 `[` 永远滞留 buffer)。
        private const val CONTROL_TAG_MAX_TAIL = 64
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
        // 流式过滤跨 chunk 标签用:见 filterControlTags 注释。
        var pendingTagTail: StringBuilder = StringBuilder()
        var streamingRenderJob: Job? = null
        var idleTimeoutJob: Job? = null
        var timedOut = false
        val toolCallIds = mutableListOf<String>()
        // P0:跟踪最近一次 assistant 消息的 tool chip,用于 MemorySaved 时判断
        // 是否已有真实工具 chip 显示(避免"已记住"覆盖"已创建提醒")。
        // UseCase 不直接持有 MutableStateFlow,通过 updateAssistantToolStatus
        // 闭包写入来同步。
        var lastAssistantToolStatus: String? = null

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
            val pendingLen = pendingStreamingContent.length
            if (pendingLen >= STREAMING_RENDER_BATCH_CHARS) {
                streamingRenderJob?.cancel()
                streamingRenderJob = null
                flushStreamingContent()
                return
            }
            if (pendingLen >= STREAMING_LEADING_FLUSH_CHARS &&
                streamingRenderJob?.isActive != true
            ) {
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
            lastAssistantToolStatus = status
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
                        // LLM 输出含 [mood:happy][intensity:0.6] 这类控制标签,
                        // OutputParser 只在 Complete 事件时清理,但 streaming draft
                        // 区是原始 delta 累积,UI 会显示成 raw 标签。
                        // 在 UseCase 输出层做一次过滤,让 draft 始终是干净文本,
                        // UI 渲染层(MarkdownMessageText.sanitizeDisplayMarkdown)
                        // 不用再操心这层协议,职责更清晰。
                        //
                        // 状态机式过滤:跨 chunk 的"半个标签"不会泄漏到 draft。
                        val cleanDelta = filterControlTags(event.delta, pendingTagTail)
                        AppLogger.info(
                            LogTags.Chat,
                            "streaming_delta",
                            "rawLen" to event.delta.length,
                            "cleanLen" to cleanDelta.length,
                            "filtered" to (cleanDelta != event.delta),
                            "rawPreview" to event.delta.take(80),
                            "cleanPreview" to cleanDelta.take(80),
                            "tailLen" to pendingTagTail.length,
                        )
                        assistantContent += cleanDelta
                        pendingStreamingContent.append(cleanDelta)
                        scheduleStreamingRender()
                    }
                    is AgentEvent.ToolCallUpdated -> {
                        maybeShowPermissionPrompt(event.call, update)
                        // P0 修复:切到 resolveLabel 动态路径,带 resultJson 的工具可以
                        // 产出"已创建提醒 · 吃药"等带数据的 chip 文案(不再丢失 subject)。
                        // 没有 resultJson 或解析失败时回退到静态 label,行为兼容。
                        val statusLabel = when (event.call.status) {
                            ToolCallStatus.FAILED -> formatToolError(event.call)
                            else -> toolDisplayRegistry.resolveLabel(
                                toolName = event.call.name,
                                status = event.call.status,
                                resultJson = event.call.resultJson,
                                errorMessage = event.call.errorMessage,
                            )
                        }
                        updateAssistantToolStatus(statusLabel, event.call.status)
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
                        // P0 修复:后置记忆抽取的"已记住 N 条"提示是本轮 conversation
                        // 级别的 reflection 信号,不应覆盖 assistant 消息上刚刚的
                        // tool call chip(例如用户调用 create_local_reminder 时,
                        // "已记住"会覆盖"已创建提醒 · 吃药",造成信息丢失)。
                        //
                        // 策略:只有当 assistant 消息还没显示 tool chip 时才覆盖,
                        // 否则只通过 presence 反应通知用户,不挤占 chip 字段。
                        if (lastAssistantToolStatus.isNullOrBlank()) {
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
                        streamingRenderJob?.cancel()
                        flushStreamingContent()
                        // Complete 时再清一次 pendingTagTail 的剩余尾巴
                        // (例如 LLM 最后 chunk 把 '[' 切了,没续完)。
                        // 状态机 flush 策略: 把尾巴里所有 '[' 也吐回正文,
                        // 避免永远滞留 buffer 污染下一轮。
                        val tailFlush = pendingTagTail.toString()
                        if (tailFlush.isNotEmpty()) {
                            // pendingTagTail 此时只可能含 '[...' 类未闭合片段,
                            // 全部当作正文推一次清空。
                            assistantContent += tailFlush
                        }
                        pendingTagTail.clear()

                        val finalReply = event.parsed.textReply
                        AppLogger.info(
                            LogTags.Chat,
                            "complete_text_reply",
                            "len" to finalReply.length,
                            "tailFlushed" to tailFlush.length,
                            "preview" to finalReply.take(200),
                        )
                        AppLogger.info(
                            LogTags.Chat,
                            "message_send_completed",
                            "requestHash" to LogFieldSanitizer.hash(requestId),
                            "replyLength" to finalReply.length,
                        )
                        updateAssistantMessage(assistantId, update) {
                            it.copy(
                                content = finalReply,
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

    /**
     * 流式过滤 LLM 控制标签(状态机版)。
     *
     * 背景:per-delta `Regex("""\[[^\]]+]""").replace(delta, "")` 在 chunk 切到标签
     * 中间时会把"半个标签"字符当成正文推给 draft,UI 显示成
     * "[mood" / "aff" / "inity" / "][" 这种半成品。
     *
     * 解决:维护一个跨 chunk 的 `pendingTagTail`(在调用方持有):
     * 1. 把上次的未配对尾巴 + 本次 chunk 拼起来当作完整输入
     * 2. 扫描输入:遇到 `[` 开始一节候选标签,遇到第一个 `]` 立刻吞掉整节
     *    `[` 之前的字符当正文输出
     * 3. 扫描到末尾若仍在标签中(没遇到 `]`),把这段留在 pendingTagTail
     *    等下次 chunk 续上
     * 4. 若未配对长度超过 [CONTROL_TAG_MAX_TAIL],放弃,作为正文吐出
     *    (防止一个未闭合的 `[` 永远滞留 buffer)
     *
     * @param chunk 本次 streaming delta
     * @param pendingTagTail 调用方持有的跨 chunk 状态(mutable)
     * @return 推给 draft 区的干净文本
     */
    private fun filterControlTags(chunk: String, pendingTagTail: StringBuilder): String {
        if (chunk.isEmpty() && pendingTagTail.isEmpty()) return ""
        val combined = buildString(pendingTagTail.length + chunk.length) {
            append(pendingTagTail)
            append(chunk)
        }
        val out = StringBuilder(combined.length)
        var inTag = false
        var tagStart = -1  // combined 中 '[' 的索引(用于 inTag 状态下超长兜底)
        var i = 0
        while (i < combined.length) {
            val c = combined[i]
            if (inTag) {
                if (c == CONTROL_TAG_SUFFIX) {
                    // 完整标签 — 吞掉
                    inTag = false
                    tagStart = -1
                    i++
                } else {
                    i++
                }
            } else {
                if (c == CONTROL_TAG_PREFIX) {
                    inTag = true
                    tagStart = i
                    i++
                } else {
                    out.append(c)
                    i++
                }
            }
        }

        // 处理尾巴:inTag=true 表示有未配对的 '[';否则清空
        pendingTagTail.clear()
        if (inTag) {
            val tailLen = combined.length - tagStart
            if (tailLen >= CONTROL_TAG_MAX_TAIL) {
                // 超长未闭合 — 放弃等待,作为正文吐出
                out.append(combined, tagStart, combined.length)
            } else {
                pendingTagTail.append(combined, tagStart, combined.length)
            }
        }
        return out.toString()
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

    /**
     * 把单个 tool call 的失败原因翻译成用户可读字符串。
     *
     * 输入可来自两个字段:
     * - `call.resultJson`: envelope error 文本(优先,理由更具体)
     * - `call.errorMessage`: 早期异常的纯字符串兜底
     *
     * 输出策略:
     * - 有 envelope reason → 返回 "工具失败 · <reason>"(不暴露 hint,因为 hint 是给 LLM 的)
     * - 有 errorMessage → 返回 "工具失败 · <errorMessage>"
     * - 都无 → 返回 "工具失败"
     */
    private fun formatToolError(call: AgentToolCall): String {
        val resultJson = call.resultJson
        if (!resultJson.isNullOrBlank() && com.xiaoqi.companion.core.tools.isError(resultJson)) {
            val reason = com.xiaoqi.companion.core.tools.parseErrorReason(resultJson)
            if (!reason.isNullOrBlank()) return "工具失败 · $reason"
        }
        val msg = call.errorMessage?.takeIf { it.isNotBlank() }
        return if (msg != null) "工具失败 · $msg" else "工具失败"
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
