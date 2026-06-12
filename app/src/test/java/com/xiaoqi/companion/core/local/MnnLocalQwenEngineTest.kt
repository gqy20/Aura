package com.xiaoqi.companion.core.local

import app.cash.turbine.test
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MnnLocalQwenEngineTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun stream_loadsSessionFromModelConfigAndStreamsBridgeTokens() = runTest {
        val modelDir = temp.newFolder("Qwen3.5-2B-MNN")
        File(modelDir, "config.json").writeText("{}")
        val bridge = FakeMnnLlmBridge(listOf("hello", " ", "mnn"))
        val engine = MnnLocalQwenEngine(
            modelLocator = StaticModelLocator(modelDir),
            bridgeFactory = StaticBridgeFactory(bridge),
        )

        engine.stream(LocalQwenRequest(systemPrompt = "system", userMessage = "hi")).test {
            assertEquals("hello", awaitItem())
            assertEquals(" ", awaitItem())
            assertEquals("mnn", awaitItem())
            awaitComplete()
        }

        assertEquals(File(modelDir, "config.json").absolutePath, bridge.loadedConfigPath)
        assertEquals("system", bridge.systemPrompt)
        assertEquals("hi", bridge.userMessage)
        assertTrue(bridge.loaded)
        assertFalse(bridge.released)
    }

    @Test
    fun stream_reusesLoadedBridgeForSameModelConfig() = runTest {
        val modelDir = temp.newFolder("Qwen3.5-2B-MNN")
        File(modelDir, "config.json").writeText("{}")
        val bridge = FakeMnnLlmBridge(listOf("ok"))
        val engine = MnnLocalQwenEngine(
            modelLocator = StaticModelLocator(modelDir),
            bridgeFactory = StaticBridgeFactory(bridge),
        )

        engine.stream(LocalQwenRequest(systemPrompt = "system", userMessage = "first")).test {
            assertEquals("ok", awaitItem())
            awaitComplete()
        }
        engine.stream(LocalQwenRequest(systemPrompt = "system", userMessage = "second")).test {
            assertEquals("ok", awaitItem())
            awaitComplete()
        }

        assertEquals(1, bridge.loadCount)
        assertEquals(listOf("system" to "first", "system" to "second"), bridge.prompts)
        assertFalse(bridge.released)
    }

    @Test
    fun stream_whenModelConfigMissing_failsBeforeBridgeLoad() = runTest {
        val modelDir = temp.newFolder("Qwen3.5-2B-MNN")
        val bridge = FakeMnnLlmBridge(listOf("unused"))
        val engine = MnnLocalQwenEngine(
            modelLocator = StaticModelLocator(modelDir),
            bridgeFactory = StaticBridgeFactory(bridge),
        )

        engine.stream(LocalQwenRequest(systemPrompt = "system", userMessage = "hi")).test {
            val error = awaitError()
            assertTrue(error is IllegalStateException)
            assertTrue(error.message.orEmpty().contains("config.json"))
        }

        assertFalse(bridge.loaded)
    }

    private class StaticModelLocator(
        private val modelDir: File?,
    ) : LocalQwenModelLocator {
        override fun findModelDir(modelName: String): File? = modelDir
    }

    private class FakeMnnLlmBridge(
        private val chunks: List<String>,
    ) : MnnLlmBridge {
        var loaded = false
        var released = false
        var loadCount = 0
        var loadedConfigPath: String? = null
        var systemPrompt: String? = null
        var userMessage: String? = null
        val prompts = mutableListOf<Pair<String, String>>()

        override suspend fun load(configPath: String) {
            loaded = true
            loadCount++
            loadedConfigPath = configPath
        }

        override fun generate(
            systemPrompt: String,
            userMessage: String,
            onToken: (String) -> Boolean,
        ): Map<String, Any> {
            this.systemPrompt = systemPrompt
            this.userMessage = userMessage
            prompts += systemPrompt to userMessage
            chunks.forEach { chunk ->
                if (onToken(chunk)) return mapOf("stopped" to true)
            }
            onToken("")
            return mapOf("success" to true)
        }

        override fun release() {
            released = true
        }
    }

    private class StaticBridgeFactory(
        private val bridge: MnnLlmBridge,
    ) : MnnLlmBridgeFactory {
        override fun create(): MnnLlmBridge = bridge
    }
}
