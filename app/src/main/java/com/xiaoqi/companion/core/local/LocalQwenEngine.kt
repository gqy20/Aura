package com.xiaoqi.companion.core.local

import kotlinx.coroutines.flow.Flow

data class LocalQwenRequest(
    val systemPrompt: String,
    val userMessage: String,
    val modelName: String = "",
    val allowTools: Boolean = false,
)

interface LocalQwenEngine {
    fun stream(request: LocalQwenRequest): Flow<String>
}
