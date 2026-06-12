package com.xiaoqi.companion.core.local

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeMnnLlmBridgeTest {

    @Test
    fun factory_createsNativeBridge() {
        val bridge = NativeMnnLlmBridgeFactory().create()

        assertTrue(bridge is NativeMnnLlmBridge)
    }

    @Test
    fun load_whenNativeLibraryUnavailable_reportsActionableError() = runTest {
        val bridge = NativeMnnLlmBridge(
            native = object : NativeMnnLlmApi {
                override fun loadLibrary(): Boolean = false
                override fun init(configPath: String): Long = 0
                override fun submit(
                    instanceId: Long,
                    prompt: String,
                    listener: NativeMnnProgressListener,
                ): Map<String, Any> = emptyMap()
                override fun release(instanceId: Long) = Unit
            }
        )

        val error = runCatching { bridge.load("config.json") }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("aura_mnn_llm"))
    }
}
