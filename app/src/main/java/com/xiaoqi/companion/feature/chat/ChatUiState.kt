package com.xiaoqi.companion.feature.chat

import com.xiaoqi.companion.core.companion.model.ToolCallStatus
import com.xiaoqi.companion.core.llm.ConnectivityResult
import com.xiaoqi.companion.core.mcp.McpServerConfig
import com.xiaoqi.companion.core.presence.PresenceAnimationState
import com.xiaoqi.companion.core.presence.PresenceReaction
import com.xiaoqi.companion.core.presence.PresenceUiState
import com.xiaoqi.companion.core.task.AgentLongTask
import com.xiaoqi.companion.data.db.converter.LlmProvider
import com.xiaoqi.companion.data.repository.DefaultLlmValues

data class ChatMessage(
    val id: String,
    val role: String, // "USER" or "ASSISTANT"
    val content: String,
    val imageUri: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    /**
     * 非工具类状态文案(看图/本地模型加载/Progress 阶段提示)。
     * 工具调用的过程叙事走 [toolSteps],不再挤占这个字段。
     */
    val toolStatus: String? = null,
    val toolStatusType: ToolCallStatus? = null,
    /**
     * 模型在调工具前流出的过渡文本("我先帮你查查…")。
     * StreamingReset 时从正文降级到这里保留展示,不进最终 [content](runtime 不持久化这段)。
     */
    val intentText: String = "",
    /**
     * Room 落库后的行 id。LazyColumn 的 key 始终用 [id](transient id),
     * 完成/DB 刷新时靠本字段对齐行,避免换 key 导致整条消息重淡入。
     */
    val persistedId: String? = null,
    /** 工具步骤时间线,按触发顺序累积,复合工具调用的过程叙事载体。 */
    val toolSteps: List<ChatToolStep> = emptyList(),
    val renderBlocks: List<MessageRenderBlock> = emptyList(),
    val renderDraft: String = "",
    val isRenderDraftCode: Boolean = false,
    /**
     * 本条消息触发的 tool call id 列表(只对 ASSISTANT 消息有意义)。
     * 用于 detail panel 反查 `uiState.toolCalls`,避免整个 `toolCalls` 池混淆。
     */
    val toolCallIds: List<String> = emptyList(),
    /**
     * ASSISTANT 消息的性能指标(wall clock + 字符数估算 tok/s)。
     * 只在消息完成后填充,流式中为 null。
     */
    val performanceInfo: PerformanceInfo? = null,
    val completionState: ChatMessageCompletionState? = null,
)

/**
 * 气泡下工具时间线的单步。id 唯一标识一步([callId] 为空时用合成 id),
 * ToolStarted/ToolFinished 无 callId,靠 name 匹配未终结的步骤补齐。
 */
data class ChatToolStep(
    val id: String,
    val name: String,
    val callId: String? = null,
    val label: String,
    val status: ToolCallStatus,
    val startedAtMs: Long,
    val durationMs: Long? = null,
)

enum class ChatMessageCompletionState {
    STOPPED,
    FAILED,
}

data class ChatToolCall(
    val id: String,
    val toolName: String,
    val toolStatus: ToolCallStatus,
    val label: String,
    val status: String,
    val durationMs: Long? = null,
    val completedAt: Long? = null,
    val errorMessage: String? = null,
    val mapInteraction: com.xiaoqi.companion.feature.chat.map.MapToolInteraction? = null,
    /**
     * 工具结果的结构化摘要,供 detail panel 渲染。
     * - STARTED 时为 null(还没结果)
     * - SUCCEEDED 时可能是 ListHits / SavedOne / Scheduled / KeyValueReport / Empty
     * - FAILED 时是 Failed
     * - 兜底时是 Unknown
     */
    val summary: com.xiaoqi.companion.core.tools.parser.ToolResultSummary? = null,
)

data class CompanionStatus(
    val mood: String = "neutral",
    val intensity: Float = 0.5f,
    val relationshipLevel: Float = 0f,
) {
    val relationshipLabel: String
        get() = when {
            relationshipLevel >= 0.8f -> "亲密"
            relationshipLevel >= 0.5f -> "熟悉"
            relationshipLevel >= 0.2f -> "初识"
            else -> "陌生"
        }
}

data class ChatMemory(
    val id: String,
    val content: String,
    val type: String,
    val importance: Float,
    val source: String = "",
    val timestamp: Long = 0L,
    val pinned: Boolean = false,
    val archived: Boolean = false,
)

data class ChatReminder(
    val id: String,
    val title: String,
    val message: String,
    val triggerAtMillis: Long,
    val exact: Boolean,
    val status: String,
)

data class ChatInsight(
    val id: Long,
    val triggerType: String,
    val category: String,
    val headline: String,
    val bodyMarkdown: String,
    val confidence: Float,
    val relevanceWindow: String,
    val status: String,
    val evidenceView: com.xiaoqi.companion.data.repository.InsightEvidenceView =
        com.xiaoqi.companion.data.repository.InsightEvidenceView(),
)

/** 跨会话消息搜索结果条目,点击跳转到对应会话并定位消息。 */
data class ChatMessageSearchHit(
    val messageId: String,
    val sessionId: String,
    val sessionTitle: String,
    val role: String,
    val preview: String,
    val timestamp: Long,
)

data class ChatConfigStatus(
    val label: String = "正在检测模型",
    val isReady: Boolean = false,
    val detail: String = "",
    val provider: LlmProvider = LlmProvider.GLM,
    val modelName: String = DefaultLlmValues.GLM_MODEL,
    val baseUrl: String = "",
)

data class ChatToolCapabilitySettings(
    val deviceStatusEnabled: Boolean = true,
    val locationContextEnabled: Boolean = true,
    val weatherContextEnabled: Boolean = true,
    val reminderToolEnabled: Boolean = true,
    val notificationEnabled: Boolean = true,
    /** 本地模型工具调用开关。默认 false。 */
    val localToolsEnabled: Boolean = false,
    /** MCP 总开关。默认 true,关掉后所有 MCP server 的工具都不注册。 */
    val mcpEnabled: Boolean = true,
    /** 系统默认工具(记忆/时间/提醒/Health 等)开关。默认 true。 */
    val systemToolsEnabled: Boolean = true,
    /** 多 MCP server 列表 — 替代老的单 server 字段 (mcpProviderId/mcpApiKey/mcpServerName/mcpHttpUrl)。 */
    val mcpServers: List<McpServerConfig> = emptyList(),
)

data class LocalQwenDownloadUiState(
    val modelName: String = DefaultLlmValues.LOCAL_QWEN_MODEL,
    val isInstalled: Boolean = false,
    val isChecking: Boolean = false,
    val isDownloading: Boolean = false,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long? = null,
    val message: String? = null,
    val error: String? = null,
)

data class ChatImageAttachment(
    val uriString: String,
    val imageBase64: String,
    val mediaType: String = "image/jpeg",
)

enum class ChatPermissionType {
    EXACT_ALARM,
}

data class ChatPermissionPrompt(
    val type: ChatPermissionType,
    val title: String,
    val message: String,
    val primaryActionLabel: String,
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val toolCalls: List<ChatToolCall> = emptyList(),
    /** 独立保留地图产物，避免复合任务把历史卡片挤出最近工具窗口。 */
    val mapToolCalls: List<ChatToolCall> = emptyList(),
    val memories: List<ChatMemory> = emptyList(),
    val reminders: List<ChatReminder> = emptyList(),
    val longTasks: List<AgentLongTask> = emptyList(),
    val status: CompanionStatus = CompanionStatus(),
    val presenceReaction: PresenceReaction? = null,
    val presence: PresenceUiState = PresenceUiState(),
    val presenceAnimation: PresenceAnimationState = PresenceAnimationState(
        pulseDurationMillis = 2600,
        breathDurationMillis = 2600,
        shimmerDurationMillis = 1900,
        tapScaleTarget = 0.94f,
        orbitParticleCount = 3,
        orbitRadiusScale = 1f,
        petFrameDurationScale = 1f,
        haloBoost = 0f,
        breathAmplitude = 0.012f,
        shimmerAmplitude = 0.012f,
        pulseAmplitude = 0.016f,
        ringAlpha = 0.16f,
        glowAlpha = 0.28f,
        sparkAlpha = 0.24f,
    ),
    val configStatus: ChatConfigStatus = ChatConfigStatus(),
    val settingsApiKey: String = "",
    val settingsBaseUrl: String = "",
    val settingsProvider: LlmProvider = LlmProvider.GLM,
    val settingsModelName: String = DefaultLlmValues.GLM_MODEL,
    val settingsMessage: String? = null,
    val localQwenDownload: LocalQwenDownloadUiState = LocalQwenDownloadUiState(),
    val mcpSettingsName: String = "",
    val mcpSettingsUrl: String = "",
    val mcpSettingsProviderId: String = "amap",
    val mcpSettingsApiKey: String = "",
    val mcpSettingsAuthToken: String = "",
    val mcpSettingsKeyVisible: Boolean = false,
    /** 当前 editor 正在编辑的 server id;null = 新建。 */
    val mcpEditingServerId: String? = null,
    val mcpSettingsMessage: String? = null,
    /**
     * 一次性"已保存"事件,每次 saveMcpSettings 成功时 +1。UI 用 [LaunchedEffect] 监听
     * 这个字段的变化来切回 list 模式 + 展示 snackbar。仅作信号用,UI 不要按它的值做业务判断。
     */
    val mcpEditorJustSaved: Long = 0L,
    /**
     * 一次性"已清空"事件,清空 insights / memories / mood_snapshots 成功时写入
     * (timestamp, count) 元组。UI 用 [LaunchedEffect] 监听 timestamp 弹 snackbar;
     * count 用于文案 "已清空 N 条"。仅作信号用,不要按 timestamp 的值做业务判断。
     */
    val dataJustClearedAt: Long = 0L,
    val dataJustClearedCount: Int = 0,
    val toolCapabilitySettings: ChatToolCapabilitySettings = ChatToolCapabilitySettings(),
    val pendingImage: ChatImageAttachment? = null,
    val isPreparingImage: Boolean = false,
    val isLoading: Boolean = false,
    val inputText: String = "",
    val permissionPrompt: ChatPermissionPrompt? = null,
    val error: String? = null,
    val connectivityResult: ConnectivityResult? = null,
    val mcpConnectivityResult: ConnectivityResult? = null,
    val isCheckingConnectivity: Boolean = false,
    /**
     * 上次 probe 后缓存的每个 server 的 tool 名列表,key = server id。
     * McpListScreen 卡片展开时用它显示该 server 提供的具体工具。
     * 值为 emptyList 表示"已探但 0 个 tool"(实际不应该)或"探失败"(见 isCheckingConnectivity)。
     * 整个 key 不在 map 里表示"还没探过"。
     */
    val mcpServerTools: Map<String, List<String>> = emptyMap(),
    /** 上次连接检查中每个 server 的可读失败原因；成功后会移除对应项。 */
    val mcpServerErrors: Map<String, String> = emptyMap(),
    val insights: List<ChatInsight> = emptyList(),
    /** 本地模型是否正在分析对话生成洞察(首页显示"正在思考..."指示器) */
    val isInsightAnalyzing: Boolean = false,
    val moodTrend: List<com.xiaoqi.companion.data.db.entity.MoodSnapshotEntity> = emptyList(),
    val pendingPrefill: String? = null,
    /** 对话列表 Sheet 的消息搜索框文本;≥3 字符触发 FTS 查询(trigram 下限)。 */
    val messageSearchQuery: String = "",
    val messageSearchResults: List<ChatMessageSearchHit> = emptyList(),
    /** 搜索结果跳转目标消息 id;ChatScreen 滚动定位后调用 consumeScrollTarget 清除。 */
    val pendingScrollTargetId: String? = null,
)

/**
 * ASSISTANT 消息的性能指标。
 * - [durationMs]: 从发消息到回复完成的总耗时(wall clock)
 * - [estimatedTokens]: 根据输出字符数粗估的 token 数(中文≈1.2 char/token,英文≈4 char/token)
 * - [tokensPerSecond]: estimatedTokens / (durationMs / 1000)
 */
data class PerformanceInfo(
    val durationMs: Long,
    val estimatedTokens: Int,
) {
    val tokensPerSecond: Float
        get() = if (durationMs > 0) estimatedTokens * 1000f / durationMs else 0f

    fun format(): String {
        val seconds = durationMs / 1000.0
        return if (estimatedTokens > 0 && durationMs > 0) {
            "%.1fs · %.0f tok/s".format(seconds, tokensPerSecond)
        } else {
            "%.1fs".format(seconds)
        }
    }
}
