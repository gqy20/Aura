package com.xiaoqi.companion.core.local

interface MnnLlmBridge {
    suspend fun load(configPath: String)
    fun generate(prompt: String, onToken: (String) -> Boolean): Map<String, Any>
    fun release()
}

interface MnnLlmBridgeFactory {
    fun create(): MnnLlmBridge
}
