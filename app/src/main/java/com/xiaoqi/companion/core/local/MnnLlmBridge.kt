package com.xiaoqi.companion.core.local

interface MnnLlmBridge {
    suspend fun load(configPath: String)
    fun generate(prompt: String, onToken: (String) -> Boolean): Map<String, Any>
    fun release()
}

interface MnnLlmBridgeFactory {
    fun create(): MnnLlmBridge
}

class MissingNativeMnnLlmBridgeFactory @javax.inject.Inject constructor() : MnnLlmBridgeFactory {
    override fun create(): MnnLlmBridge = MissingNativeMnnLlmBridge
}

private object MissingNativeMnnLlmBridge : MnnLlmBridge {
    override suspend fun load(configPath: String) {
        throw IllegalStateException("MNN native bridge is not integrated yet.")
    }

    override fun generate(prompt: String, onToken: (String) -> Boolean): Map<String, Any> =
        error("MNN native bridge is not integrated yet.")

    override fun release() = Unit
}
