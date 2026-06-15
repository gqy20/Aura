package com.xiaoqi.companion.feature.chat

import com.xiaoqi.companion.core.companion.model.ToolCallStatus
import com.xiaoqi.companion.core.llm.ConnectivityResult
import com.xiaoqi.companion.core.mcp.McpServerConfig
import com.xiaoqi.companion.core.presence.PresenceReaction
import com.xiaoqi.companion.core.presence.PresenceUiState
import com.xiaoqi.companion.data.db.converter.LlmProvider
import com.xiaoqi.companion.data.repository.DefaultLlmValues

data class ChatMessage(
    val id: String,
    val role: String, // "USER" or "ASSISTANT"
    val content: String,
    val imageUri: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val toolStatus: String? = null,
    val renderBlocks: List<MessageRenderBlock> = emptyList(),
    val renderDraft: String = "",
    val isRenderDraftCode: Boolean = false,
)

data class ChatToolCall(
    val id: String,
    val toolName: String,
    val toolStatus: ToolCallStatus,
    val label: String,
    val status: String,
    val durationMs: Long? = null,
    val errorMessage: String? = null,
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
    /** 多 MCP server 列表 — 替代老的单 server 字段 (mcpProviderId/mcpApiKey/mcpServerName/mcpHttpUrl)。 */
    val mcpServers: List<McpServerConfig> = emptyList(),
)

data class LocalQwenDownloadUiState(
    val modelName: String = DefaultLlmValues.LOCAL_QWEN_MODEL,
    val isInstalled: Boolean = false,
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
    val memories: List<ChatMemory> = emptyList(),
    val reminders: List<ChatReminder> = emptyList(),
    val status: CompanionStatus = CompanionStatus(),
    val presenceReaction: PresenceReaction? = null,
    val presence: PresenceUiState = PresenceUiState(),
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
    val mcpSettingsKeyVisible: Boolean = false,
    /** 当前 editor 正在编辑的 server id;null = 新建。 */
    val mcpEditingServerId: String? = null,
    val mcpSettingsMessage: String? = null,
    /**
     * 一次性"已保存"事件,每次 saveMcpSettings 成功时 +1。UI 用 [LaunchedEffect] 监听
     * 这个字段的变化来切回 list 模式 + 展示 snackbar。仅作信号用,UI 不要按它的值做业务判断。
     */
    val mcpEditorJustSaved: Long = 0L,
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
    val insights: List<ChatInsight> = emptyList(),
    val moodTrend: List<com.xiaoqi.companion.data.db.entity.MoodSnapshotEntity> = emptyList(),
    val pendingPrefill: String? = null,
)
