package com.xiaoqi.companion.feature.chat.usecase

import com.xiaoqi.companion.core.local.LocalQwenModelDownloadState
import com.xiaoqi.companion.core.local.LocalQwenModelDownloader
import com.xiaoqi.companion.core.mcp.McpServerConfig
import com.xiaoqi.companion.data.datastore.AppPreferences
import com.xiaoqi.companion.data.db.converter.LlmProvider
import com.xiaoqi.companion.data.repository.ConfigRepository
import com.xiaoqi.companion.data.repository.DefaultLlmValues
import com.xiaoqi.companion.data.repository.McpServerListRepository
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
    private val initialCustomServer = McpServerConfig(
        id = "id-existing",
        displayName = "Local MCP",
        providerId = "custom",
        customUrl = "https://old.example/mcp",
        enabled = true,
    )
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
                mcpServers = listOf(initialCustomServer),
            ),
        )
    )
    private val update: (ChatUiState.() -> ChatUiState) -> Unit = { reducer -> state.update(reducer) }

    private val configRepository: ConfigRepository = mockk(relaxed = true)
    private val appPreferences: AppPreferences = mockk(relaxed = true)
    private val localQwenDownloader: LocalQwenModelDownloader = mockk(relaxed = true)
    private val mcpServerListRepository: McpServerListRepository = mockk(relaxed = true)
    private lateinit var useCase: SettingsUseCase

    @Before
    fun setUp() {
        useCase = SettingsUseCase(
            configRepository = configRepository,
            appPreferences = appPreferences,
            localQwenModelDownloader = localQwenDownloader,
            mcpServerListRepository = mcpServerListRepository,
        )
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
        every { localQwenDownloader.download(DefaultLlmValues.LOCAL_QWEN_MODEL, any()) } returns flow {
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
    fun prepareMcpSettings_loadsFirstServerIntoEditor() = runTest {
        // 已有 server → editor 默认加载第一项
        useCase.prepareMcpSettings(update)
        assertEquals("id-existing", state.value.mcpEditingServerId)
        assertEquals("custom", state.value.mcpSettingsProviderId)
        assertEquals("Local MCP", state.value.mcpSettingsName)
        assertEquals("https://old.example/mcp", state.value.mcpSettingsUrl)
    }

    @Test
    fun prepareMcpSettings_fallsBackToNewAmapWhenListEmpty() = runTest {
        // 新用户: list 空 → 进"新建"模式,默认 amap
        state.update { it.copy(toolCapabilitySettings = ChatToolCapabilitySettings()) }
        useCase.prepareMcpSettings(update)
        assertEquals(null, state.value.mcpEditingServerId)
        assertEquals("amap", state.value.mcpSettingsProviderId)
        assertEquals("", state.value.mcpSettingsApiKey)
    }

    @Test
    fun startNewMcpSettings_resetsEditor() = runTest {
        // 已经在编辑某项,点 Add 想新建 → editor 应清空
        useCase.prepareMcpSettings(update)
        useCase.startNewMcpServer(update)
        assertEquals(null, state.value.mcpEditingServerId)
        assertEquals("amap", state.value.mcpSettingsProviderId)
        assertEquals("", state.value.mcpSettingsName)
        assertEquals("", state.value.mcpSettingsApiKey)
    }

    @Test
    fun loadMcpServerForEditing_replacesEditorWithChosenServer() = runTest {
        val second = McpServerConfig(id = "id-2", providerId = "amap", apiKey = "k2", displayName = "Amap #2")
        state.update { it.copy(toolCapabilitySettings = ChatToolCapabilitySettings(mcpServers = listOf(initialCustomServer, second))) }
        useCase.loadMcpServerForEditing("id-2", update)
        assertEquals("id-2", state.value.mcpEditingServerId)
        assertEquals("amap", state.value.mcpSettingsProviderId)
        assertEquals("k2", state.value.mcpSettingsApiKey)
        assertEquals("Amap #2", state.value.mcpSettingsName)
    }

    @Test
    fun loadMcpServerForEditing_unknownIdIsNoOp() = runTest {
        useCase.prepareMcpSettings(update)
        useCase.loadMcpServerForEditing("does-not-exist", update)
        // 仍保留 prepare 时的状态
        assertEquals("id-existing", state.value.mcpEditingServerId)
    }

    @Test
    fun selectMcpProvider_amapClearsCustomUrl() = runTest {
        // 当前在 custom 编辑 → 切到 amap → url 应清空(amap 不该手填 url)
        useCase.prepareMcpSettings(update)  // 编辑 id-existing (custom, url=...)
        useCase.selectMcpProvider("amap", update)
        assertEquals("amap", state.value.mcpSettingsProviderId)
        assertEquals("", state.value.mcpSettingsUrl)
    }

    @Test
    fun saveMcpSettings_persistsNewAmapServer() = runTest {
        // 新建 amap → 应调 mcpServerListRepository.add,带正确字段
        useCase.startNewMcpServer(update)
        state.update { it.copy(mcpSettingsName = "MyAmap", mcpSettingsApiKey = "k123") }
        useCase.saveMcpSettings(state.value, this, update)
        advanceUntilIdle()

        coVerify {
            mcpServerListRepository.add(match { config ->
                config.providerId == "amap" &&
                    config.apiKey == "k123" &&
                    config.displayName == "MyAmap" &&
                    config.customUrl == "" &&
                    config.enabled
            })
        }
    }

    @Test
    fun saveMcpSettings_persistsUpdatedCustomServer() = runTest {
        // 编辑现有 custom server → 应调 mcpServerListRepository.update
        useCase.prepareMcpSettings(update)
        state.update { it.copy(mcpSettingsUrl = "https://new.example/mcp") }
        useCase.saveMcpSettings(state.value, this, update)
        advanceUntilIdle()

        coVerify {
            mcpServerListRepository.update(match { config ->
                config.id == "id-existing" &&
                    config.providerId == "custom" &&
                    config.customUrl == "https://new.example/mcp"
            })
        }
    }

    @Test
    fun saveMcpSettings_amapRejectsBlankKey() = runTest {
        // 默认 providerId=amap,user 没填 key → 报"key 不能为空",不写 repo
        useCase.saveMcpSettings(state.value, this, update)
        advanceUntilIdle()
        assertEquals("高德 API Key不能为空", state.value.mcpSettingsMessage)
        coVerify(exactly = 0) { mcpServerListRepository.add(any()) }
        coVerify(exactly = 0) { mcpServerListRepository.update(any()) }
    }

    @Test
    fun saveMcpSettings_customRejectsInvalidUrl() = runTest {
        useCase.selectMcpProvider("custom", update)
        state.update { it.copy(mcpSettingsName = "X", mcpSettingsUrl = "ftp://bad.example/mcp") }
        useCase.saveMcpSettings(state.value, this, update)
        advanceUntilIdle()
        assertEquals("MCP URL 必须以 http:// 或 https:// 开头", state.value.mcpSettingsMessage)
    }

    @Test
    fun saveMcpSettings_customRejectsBlankUrl() = runTest {
        useCase.selectMcpProvider("custom", update)
        useCase.saveMcpSettings(state.value, this, update)
        advanceUntilIdle()
        assertEquals("请填写 MCP URL", state.value.mcpSettingsMessage)
    }

    @Test
    fun removeMcpServer_clearsEditorWhenCurrentDeleted() = runTest {
        useCase.prepareMcpSettings(update)
        useCase.removeMcpServer("id-existing", update)
        advanceUntilIdle()
        coVerify { mcpServerListRepository.remove("id-existing") }
        // editor 应被清空(因为删的就是当前编辑的)
        assertEquals(null, state.value.mcpEditingServerId)
    }

    @Test
    fun removeMcpServer_keepsEditorWhenOtherDeleted() = runTest {
        val other = McpServerConfig(id = "id-other", providerId = "custom", customUrl = "https://other.example/mcp")
        state.update { it.copy(toolCapabilitySettings = ChatToolCapabilitySettings(mcpServers = listOf(initialCustomServer, other))) }
        useCase.prepareMcpSettings(update)  // 编辑 id-existing
        useCase.removeMcpServer("id-other", update)
        advanceUntilIdle()
        coVerify { mcpServerListRepository.remove("id-other") }
        // editor 不应被清
        assertEquals("id-existing", state.value.mcpEditingServerId)
    }

    @Test
    fun toggleMcpServerEnabled_delegatesToRepository() = runTest {
        useCase.toggleMcpServerEnabled("id-existing", update)
        advanceUntilIdle()
        coVerify { mcpServerListRepository.toggleEnabled("id-existing") }
    }

    @Test
    fun saveMcpSettings_preservesDisabledStateWhenEditingExistingServer() = runTest {
        state.update {
            it.copy(
                toolCapabilitySettings = ChatToolCapabilitySettings(
                    mcpServers = listOf(initialCustomServer.copy(enabled = false)),
                ),
            )
        }
        useCase.prepareMcpSettings(update)
        state.update { it.copy(mcpSettingsUrl = "https://new.example/mcp") }
        useCase.saveMcpSettings(state.value, this, update)
        advanceUntilIdle()

        coVerify {
            mcpServerListRepository.update(match { config ->
                config.id == "id-existing" &&
                    !config.enabled &&
                    config.customUrl == "https://new.example/mcp"
            })
        }
    }
}
