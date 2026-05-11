package com.xiaoqi.companion.data.repository

import app.cash.turbine.test
import com.xiaoqi.companion.data.datastore.AppPreferences
import com.xiaoqi.companion.data.db.converter.LlmProvider
import com.xiaoqi.companion.data.db.converter.ThemeMode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigRepositoryTest {

    @Test
    fun getCurrentLlmConfig_returnsCombinedConfig() = runTest {
        val prefs: AppPreferences = mockk {
            every { apiKey } returns flowOf("test-key-123")
            every { llmProvider } returns flowOf(LlmProvider.GLM)
            every { modelName } returns flowOf("glm-5v-turbo")
        }

        val repo = ConfigRepositoryImpl(prefs)

        repo.getCurrentLlmConfig().test {
            val config = awaitItem()
            assertEquals(LlmProvider.GLM, config.provider)
            assertEquals("test-key-123", config.apiKey)
            assertEquals("glm-5v-turbo", config.modelName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getCurrentLlmConfig_kimiProvider() = runTest {
        val prefs: AppPreferences = mockk {
            every { apiKey } returns flowOf("kimi-key")
            every { llmProvider } returns flowOf(LlmProvider.KIMI)
            every { modelName } returns flowOf("kimi-latest")
        }

        val repo = ConfigRepositoryImpl(prefs)

        repo.getCurrentLlmConfig().test {
            val config = awaitItem()
            assertEquals(LlmProvider.KIMI, config.provider)
            assertEquals("kimi-latest", config.modelName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun themeMode_flowDelegatesToPrefs() = runTest {
        val prefs: AppPreferences = mockk {
            every { themeMode } returns flowOf(ThemeMode.DARK)
        }

        val repo = ConfigRepositoryImpl(prefs)
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

        val repo = ConfigRepositoryImpl(prefs)
        repo.setApiKey("new-key")
    }
}
