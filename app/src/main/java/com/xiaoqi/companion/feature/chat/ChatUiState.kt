package com.xiaoqi.companion.feature.chat

import com.xiaoqi.companion.data.db.converter.MessageRole

data class ChatMessage(
    val id: String,
    val role: String, // "USER" or "ASSISTANT"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val inputText: String = "",
    val error: String? = null,
)
