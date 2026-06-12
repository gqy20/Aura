package com.xiaoqi.companion.core.local

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
                    systemPrompt: String,
                    userMessage: String,
                    listener: NativeMnnProgressListener,
                ): Map<String, Any> = emptyMap()
                override fun release(instanceId: Long) = Unit
            }
        )

        val error = runCatching { bridge.load("config.json") }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("aura_mnn_llm"))
    }

    @Test
    fun generate_submitsStructuredSystemAndUserPrompts() = runTest {
        val native = RecordingNativeMnnLlmApi()
        val bridge = NativeMnnLlmBridge(native = native)

        bridge.load("config.json")
        bridge.generate(systemPrompt = "system", userMessage = "hi") { false }

        assertEquals("system", native.systemPrompt)
        assertEquals("hi", native.userMessage)
    }

    private class RecordingNativeMnnLlmApi : NativeMnnLlmApi {
        var systemPrompt: String? = null
        var userMessage: String? = null

        override fun loadLibrary(): Boolean = true
        override fun init(configPath: String): Long = 1L

        override fun submit(
            instanceId: Long,
            systemPrompt: String,
            userMessage: String,
            listener: NativeMnnProgressListener,
        ): Map<String, Any> {
            this.systemPrompt = systemPrompt
            this.userMessage = userMessage
            return emptyMap()
        }

        override fun release(instanceId: Long) = Unit
    }
}
