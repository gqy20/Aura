package com.xiaoqi.companion.feature.chat.usecase

import com.xiaoqi.companion.core.local.LocalQwenModelDownloadState
import com.xiaoqi.companion.core.local.LocalQwenModelDownloader
import com.xiaoqi.companion.data.datastore.AppPreferences
import com.xiaoqi.companion.data.db.converter.LlmProvider
import com.xiaoqi.companion.data.repository.ConfigRepository
import com.xiaoqi.companion.data.repository.DefaultLlmValues
import com.xiaoqi.companion.feature.chat.ChatConfigStatus
import com.xiaoqi.companion.feature.chat.ChatToolCapabilitySettings
import com.xiaoqi.companion.feature.chat.ChatUiState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsUseCaseTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val state = MutableStateFlow(
        ChatUiState(
            configStatus = ChatConfigStatus(
                label = "GLM",
                isReady = true,
                detail = "Ready",
                provider = LlmProvider.GLM,
                modelName = "glm-5v-turbo",
                baseUrl = "https://open.bigmodel.cn/api/paas/v1",
            ),
            toolCapabilitySettings = ChatToolCapabilitySettings(
                mcpServerName = "Local MCP",
                mcpHttpUrl = "https://old.example/mcp",
            ),
        )
    )
    private val update: (ChatUiState.() -> ChatUiState) -> Unit = { reducer -> state.update(reducer) }

    private val configRepository: ConfigRepository = mockk(relaxed = true)
    private val appPreferences: AppPreferences = mockk(relaxed = true)
    private val localQwenDownloader: LocalQwenModelDownloader = mockk(relaxed = true)
    private lateinit var useCase: SettingsUseCase

    @Before
    fun setUp() {
        useCase = SettingsUseCase(configRepository, appPreferences, localQwenDownloader)
    }

    @Test
    fun prepareSettings_loadsCurrentConfig() = runTest {
        useCase.prepareSettings(state.value, this, update)
        assertEquals(LlmProvider.GLM, state.value.settingsProvider)
        assertEquals("glm-5v-turbo", state.value.settingsModelName)
        assertEquals("https://open.bigmodel.cn/api/paas/v1", state.value.settingsBaseUrl)
    }

    @Test
    fun saveSettings_persistsProviderModelAndApiKey() = runTest {
        useCase.prepareSettings(state.value, this, update)
        state.update { it.copy(settingsProvider = LlmProvider.KIMI, settingsModelName = DefaultLlmValues.KIMI_MODEL) }
        state.update { it.copy(settingsApiKey = "new-key") }

        useCase.saveSettings(state.value, this, update)
        advanceUntilIdle()

        coVerify { configRepository.setLlmProvider(LlmProvider.KIMI) }
        coVerify { configRepository.setModelName(DefaultLlmValues.KIMI_MODEL) }
        coVerify { configRepository.setBaseUrl(DefaultLlmValues.KIMI_BASE_URL) }
        coVerify { configRepository.setApiKey("new-key") }
    }

    @Test
    fun saveSettings_blankModelNameFallsBackToProviderDefault() = runTest {
        state.update { it.copy(settingsModelName = "   ") }
        useCase.saveSettings(state.value, this, update)
        advanceUntilIdle()
        coVerify { configRepository.setModelName(DefaultLlmValues.GLM_MODEL) }
    }

    @Test
    fun saveSettings_localQwenDoesNotRequireBaseUrlOrApiKey() = runTest {
        state.update {
            it.copy(
                settingsProvider = LlmProvider.LOCAL_QWEN,
                settingsModelName = DefaultLlmValues.LOCAL_QWEN_MODEL,
            )
        }
        useCase.saveSettings(state.value, this, update)
        advanceUntilIdle()

        coVerify { configRepository.setLlmProvider(LlmProvider.LOCAL_QWEN) }
        coVerify { configRepository.setModelName(DefaultLlmValues.LOCAL_QWEN_MODEL) }
        coVerify { configRepository.setBaseUrl(DefaultLlmValues.LOCAL_QWEN_BASE_URL) }
    }

    @Test
    fun downloadSelectedLocalQwenModel_updatesState() = runTest {
        state.update {
            it.copy(
                settingsProvider = LlmProvider.LOCAL_QWEN,
                settingsModelName = DefaultLlmValues.LOCAL_QWEN_MODEL,
            )
        }
        every { localQwenDownloader.download(DefaultLlmValues.LOCAL_QWEN_MODEL) } returns flow {
            emit(
                LocalQwenModelDownloadState(
                    modelName = DefaultLlmValues.LOCAL_QWEN_MODEL,
                    isInstalled = false,
                    isDownloading = true,
                    progress = 0.5f,
                )
            )
            emit(
                LocalQwenModelDownloadState(
                    modelName = DefaultLlmValues.LOCAL_QWEN_MODEL,
                    isInstalled = true,
                    progress = 1f,
                )
            )
        }

        useCase.downloadSelectedLocalQwenModel(state.value, this, update)
        advanceUntilIdle()

        assertTrue(state.value.localQwenDownload.isInstalled)
        assertEquals(1f, state.value.localQwenDownload.progress, 0.001f)
    }

    @Test
    fun prepareMcpSettings_loadsCurrentMcpUrl() = runTest {
        useCase.prepareMcpSettings(update)
        assertEquals("https://old.example/mcp", state.value.mcpSettingsUrl)
        assertEquals("Local MCP", state.value.mcpSettingsName)
    }

    @Test
    fun saveMcpSettings_persistsMcpUrl() = runTest {
        state.update { it.copy(mcpSettingsName = "New MCP", mcpSettingsUrl = "https://new.example/mcp") }
        useCase.saveMcpSettings(state.value, this, update)
        advanceUntilIdle()

        coVerify { appPreferences.setMcpHttpUrl("https://new.example/mcp") }
        coVerify { appPreferences.setMcpServerName("New MCP") }
    }

    @Test
    fun saveMcpSettings_rejectsInvalidUrl() = runTest {
        state.update { it.copy(mcpSettingsName = "X", mcpSettingsUrl = "ftp://bad.example/mcp") }
        useCase.saveMcpSettings(state.value, this, update)
        advanceUntilIdle()
        assertEquals("MCP URL must start with http:// or https://", state.value.mcpSettingsMessage)
    }
}
