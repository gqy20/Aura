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

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val toolCalls: List<ChatToolCall> = emptyList(),
    val isLoading: Boolean = false,
    val inputText: String = "",
    val error: String? = null,
)
