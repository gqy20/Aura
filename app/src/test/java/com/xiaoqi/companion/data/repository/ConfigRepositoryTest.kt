package com.xiaoqi.companion.data.repository

import app.cash.turbine.test
import com.xiaoqi.companion.core.llm.ConnectivityResult
import com.xiaoqi.companion.core.llm.LlmConnectivityChecker
import com.xiaoqi.companion.data.datastore.AppPreferences
import com.xiaoqi.companion.data.db.converter.LlmProvider
import com.xiaoqi.companion.data.db.converter.ThemeMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ConfigRepositoryTest {

    private fun buildRepo(
        prefs: AppPreferences,
        checker: LlmConnectivityChecker = mockk(relaxed = true),
    ): ConfigRepositoryImpl = ConfigRepositoryImpl(prefs, checker)

    @Test
    fun getCurrentLlmConfig_returnsCombinedConfig() = runTest {
        val prefs: AppPreferences = mockk {
            every { apiKey } returns flowOf("test-key-123")
            every { llmProvider } returns flowOf(LlmProvider.GLM)
            every { modelName } returns flowOf("glm-5v-turbo")
            every { baseUrl } returns flowOf("https://example.test/v1")
        }

        val repo = buildRepo(prefs)

        repo.getCurrentLlmConfig().test {
            val config = awaitItem()
            assertEquals(LlmProvider.GLM, config.provider)
            assertEquals("test-key-123", config.apiKey)
            assertEquals(DefaultLlmValues.GLM_BASE_URL, config.baseUrl)
            assertEquals("glm-5v-turbo", config.modelName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getCurrentLlmConfig_kimiProvider() = runTest {
        val prefs: AppPreferences = mockk {
            every { apiKey } returns flowOf("kimi-key")
            every { llmProvider } returns flowOf(LlmProvider.KIMI)
            every { modelName } returns flowOf(DefaultLlmValues.KIMI_MODEL)
            every { baseUrl } returns flowOf("")
        }

        val repo = buildRepo(prefs)

        repo.getCurrentLlmConfig().test {
            val config = awaitItem()
            assertEquals(LlmProvider.KIMI, config.provider)
            assertEquals(DefaultLlmValues.KIMI_BASE_URL, config.baseUrl)
            assertEquals(DefaultLlmValues.KIMI_MODEL, config.modelName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getCurrentLlmConfig_modelScopeProvider_usesAnthropicCompatibleEndpoint() = runTest {
        val prefs: AppPreferences = mockk {
            every { apiKey } returns flowOf("ms-test-token")
            every { llmProvider } returns flowOf(LlmProvider.MODELSCOPE)
            every { modelName } returns flowOf(DefaultLlmValues.MODELSCOPE_MODEL)
            every { baseUrl } returns flowOf("https://stale.example.com/v1")
        }

        val repo = buildRepo(prefs)

        repo.getCurrentLlmConfig().test {
            val config = awaitItem()
            assertEquals(LlmProvider.MODELSCOPE, config.provider)
            assertEquals(DefaultLlmValues.MODELSCOPE_BASE_URL, config.baseUrl)
            assertEquals(DefaultLlmValues.MODELSCOPE_MODEL, config.modelName)
            assertEquals("ms-test-token", config.apiKey)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getCurrentLlmConfig_localQwenProvider_doesNotRequireApiKey() = runTest {
        val prefs: AppPreferences = mockk {
            every { apiKey } returns flowOf(null)
            every { llmProvider } returns flowOf(LlmProvider.LOCAL_QWEN)
            every { modelName } returns flowOf("")
            every { baseUrl } returns flowOf("")
        }

        val repo = buildRepo(prefs)

        repo.getCurrentLlmConfig().test {
            val config = awaitItem()
            assertEquals(LlmProvider.LOCAL_QWEN, config.provider)
            assertEquals("", config.apiKey)
            assertEquals("", config.baseUrl)
            assertEquals(DefaultLlmValues.LOCAL_QWEN_MODEL, config.modelName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeLlmConfigStatus_localQwenProvider_isReadyWithoutApiKey() = runTest {
        val prefs: AppPreferences = mockk {
            every { apiKey } returns flowOf(null)
            every { llmProvider } returns flowOf(LlmProvider.LOCAL_QWEN)
            every { modelName } returns flowOf(DefaultLlmValues.LOCAL_QWEN_MODEL)
            every { baseUrl } returns flowOf("")
        }

        val repo = buildRepo(prefs)

        repo.observeLlmConfigStatus().test {
            val status = awaitItem()
            assertEquals(LlmProvider.LOCAL_QWEN, status.provider)
            assertEquals(DefaultLlmValues.LOCAL_QWEN_MODEL, status.modelName)
            assertEquals("", status.baseUrl)
            assertEquals(false, status.hasApiKey)
            assertEquals(true, status.isReady)
            assertEquals(null, status.missingReason)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun themeMode_flowDelegatesToPrefs() = runTest {
        val prefs: AppPreferences = mockk {
            every { themeMode } returns flowOf(ThemeMode.DARK)
        }

        val repo = buildRepo(prefs)
        repo.themeMode.test {
            assertEquals(ThemeMode.DARK, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun setApiKey_delegatesToPrefs() = runTest {
        val prefs: AppPreferences = mockk(relaxed = true) {
            coEvery { setApiKey(any()) } returns Unit
        }

        val repo = buildRepo(prefs)
        repo.setApiKey("new-key")
    }

    @Test
    fun setLlmProvider_delegatesToPrefs() = runTest {
        val prefs: AppPreferences = mockk(relaxed = true) {
            coEvery { setLlmProvider(any()) } returns Unit
        }

        val repo = buildRepo(prefs)
        repo.setLlmProvider(LlmProvider.KIMI)
    }

    @Test
    fun setModelName_delegatesToPrefs() = runTest {
        val prefs: AppPreferences = mockk(relaxed = true) {
            coEvery { setModelName(any()) } returns Unit
        }

        val repo = buildRepo(prefs)
        repo.setModelName("kimi-latest")
    }

    @Test
    fun setBaseUrl_delegatesToPrefs() = runTest {
        val prefs: AppPreferences = mockk(relaxed = true) {
            coEvery { setBaseUrl(any()) } returns Unit
        }

        val repo = buildRepo(prefs)
        repo.setBaseUrl("https://example.test/v1")

        coVerify { prefs.setBaseUrl("https://example.test/v1") }
    }

    @Test
    fun checkConnectivity_delegatesToChecker() = runTest {
        val prefs: AppPreferences = mockk {
            every { apiKey } returns flowOf("key-abc")
            every { llmProvider } returns flowOf(LlmProvider.GLM)
            every { modelName } returns flowOf("glm-5v-turbo")
            every { baseUrl } returns flowOf("")
        }
        val expected = ConnectivityResult.Success(latencyMs = 123L, modelName = "glm-5v-turbo")
        val checker: LlmConnectivityChecker = mockk {
            coEvery { check(any()) } returns expected
        }
        val repo = buildRepo(prefs, checker)

        val actual = repo.checkConnectivity()

        assertSame(expected, actual)
    }

    @Test
    fun checkConnectivity_propagatesUnreachable() = runTest {
        val prefs: AppPreferences = mockk {
            every { apiKey } returns flowOf("k")
            every { llmProvider } returns flowOf(LlmProvider.GLM)
            every { modelName } returns flowOf("glm-5v-turbo")
            every { baseUrl } returns flowOf("")
        }
        val expected = ConnectivityResult.Unreachable("timeout")
        val checker: LlmConnectivityChecker = mockk {
            coEvery { check(any()) } returns expected
        }
        val repo = buildRepo(prefs, checker)

        val actual = repo.checkConnectivity()

        assertSame(expected, actual)
    }
}

