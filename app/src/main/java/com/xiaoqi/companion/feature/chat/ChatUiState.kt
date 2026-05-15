package com.xiaoqi.companion.feature.chat

data class ChatMessage(
    val id: String,
    val role: String, // "USER" or "ASSISTANT"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val toolStatus: String? = null,
)

data class ChatToolCall(
    val id: String,
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
)

data class ChatConfigStatus(
    val label: String = "模型配置检查中",
    val isReady: Boolean = false,
    val detail: String = "",
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val toolCalls: List<ChatToolCall> = emptyList(),
    val memories: List<ChatMemory> = emptyList(),
    val status: CompanionStatus = CompanionStatus(),
    val configStatus: ChatConfigStatus = ChatConfigStatus(),
    val isMemoryRoomOpen: Boolean = false,
    val isLoading: Boolean = false,
    val inputText: String = "",
    val error: String? = null,
)
