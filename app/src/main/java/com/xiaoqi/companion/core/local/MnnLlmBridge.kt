package com.xiaoqi.companion.core.local

interface MnnLlmBridge {
    suspend fun load(configPath: String, runtimeConfig: String = "")
    fun generate(
        systemPrompt: String,
        userMessage: String,
        onToken: (String) -> Boolean,
    ): Map<String, Any>
    fun generateWithImage(
        systemPrompt: String,
        userMessage: String,
        imageBytes: ByteArray,
        imageMediaType: String,
        onToken: (String) -> Boolean,
    ): Map<String, Any>
    fun release()
}

interface MnnLlmBridgeFactory {
    fun create(): MnnLlmBridge
}
