package com.xiaoqi.companion.feature.chat

import com.xiaoqi.companion.core.companion.model.ToolCallStatus
import com.xiaoqi.companion.core.presence.PresenceReaction
import com.xiaoqi.companion.core.presence.PresenceUiState
import com.xiaoqi.companion.data.db.converter.LlmProvider

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
            relationshipLevel >= 0.8f -> "非常亲密"
            relationshipLevel >= 0.5f -> "比较熟悉"
            relationshipLevel >= 0.2f -> "刚认识不久"
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
)

data class ChatConfigStatus(
    val label: String = "模型配置检查中",
    val isReady: Boolean = false,
    val detail: String = "",
    val provider: LlmProvider = LlmProvider.GLM,
    val modelName: String = "glm-5v-turbo",
    val baseUrl: String = "",
)

data class ChatToolCapabilitySettings(
    val deviceStatusEnabled: Boolean = true,
    val locationContextEnabled: Boolean = true,
    val weatherContextEnabled: Boolean = true,
    val reminderToolEnabled: Boolean = true,
    val notificationEnabled: Boolean = true,
    val mcpHttpUrl: String = "",
)

data class ChatImageAttachment(
    val uriString: String,
    val imageBase64: String,
    val mediaType: String = "image/jpeg",
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val toolCalls: List<ChatToolCall> = emptyList(),
    val memories: List<ChatMemory> = emptyList(),
    val status: CompanionStatus = CompanionStatus(),
    val presenceReaction: PresenceReaction? = null,
    val presence: PresenceUiState = PresenceUiState(),
    val configStatus: ChatConfigStatus = ChatConfigStatus(),
    val isMemoryRoomOpen: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val settingsApiKey: String = "",
    val settingsBaseUrl: String = "",
    val settingsMcpHttpUrl: String = "",
    val settingsProvider: LlmProvider = LlmProvider.GLM,
    val settingsModelName: String = "glm-5v-turbo",
    val settingsMessage: String? = null,
    val toolCapabilitySettings: ChatToolCapabilitySettings = ChatToolCapabilitySettings(),
    val pendingImage: ChatImageAttachment? = null,
    val isPreparingImage: Boolean = false,
    val isLoading: Boolean = false,
    val inputText: String = "",
    val error: String? = null,
)
