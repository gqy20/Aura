package com.xiaoqi.companion.core.local

import kotlinx.coroutines.flow.Flow

data class LocalQwenRequest(
    val systemPrompt: String,
    val userMessage: String,
    val modelName: String = "",
    val allowTools: Boolean = false,
    val imageBase64: String? = null,
    val imageMediaType: String? = null,
    val inferenceConfig: MnnInferenceConfig? = null,
)

interface LocalQwenEngine {
    fun stream(request: LocalQwenRequest): Flow<String>
}
