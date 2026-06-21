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

    /**
     * 预加载模型权重到内存（fire-and-forget）。已加载相同模型时为 no-op。
     * 默认空实现，不影响 fake engine / 测试。
     */
    suspend fun preload(modelName: String) { }
}
