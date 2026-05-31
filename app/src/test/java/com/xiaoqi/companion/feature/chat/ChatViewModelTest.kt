package com.xiaoqi.companion.feature.chat

import app.cash.turbine.test
import com.xiaoqi.companion.core.companion.CompanionRuntime
import com.xiaoqi.companion.core.companion.ConversationContextBuilder
import com.xiaoqi.companion.core.companion.ConversationReflectionResult
import com.xiaoqi.companion.core.companion.OutputParser
import com.xiaoqi.companion.core.companion.model.AgentError
import com.xiaoqi.companion.core.companion.model.AgentEvent
import com.xiaoqi.companion.core.companion.model.AgentToolCall
import com.xiaoqi.companion.core.companion.model.ToolCallStatus
import com.xiaoqi.companion.core.companion.model.UserInput
import com.xiaoqi.companion.core.local.LocalQwenModelDownloadState
import com.xiaoqi.companion.core.local.LocalQwenModelDownloader
import com.xiaoqi.companion.core.presence.PresenceController
import com.xiaoqi.companion.core.presence.PresenceMode
import com.xiaoqi.companion.core.tools.ToolDisplayRegistry
import com.xiaoqi.companion.data.db.dao.AgentStateDao
import com.xiaoqi.companion.data.db.dao.MemoryDao
import com.xiaoqi.companion.data.db.converter.MessageRole
import com.xiaoqi.companion.data.db.entity.MessageEntity
import com.xiaoqi.companion.data.db.entity.ReminderEntity
import com.xiaoqi.companion.data.datastore.AppPreferences
import com.xiaoqi.companion.data.repository.ConfigRepository
import com.xiaoqi.companion.data.repository.DefaultLlmValues
import com.xiaoqi.companion.data.repository.LlmConfig
import com.xiaoqi.companion.data.repository.LlmConfigStatus
import com.xiaoqi.companion.data.repository.MemoryRepository
import com.xiaoqi.companion.data.repository.PromptMemoryContext
import com.xiaoqi.companion.data.repository.MessageRepository
import com.xiaoqi.companion.data.repository.ReminderRepository
import com.xiaoqi.companion.data.repository.ToolCallRepository
import com.xiaoqi.companion.data.repository.ToolCallSnapshot
import io.mockk.coEvery
import io.mockk.every
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private lateinit var viewModel: ChatViewModel
    private lateinit var fakeRuntime: FakeCompanionRuntime
    private lateinit var toolCallRepository: FakeToolCallRepository
    private lateinit var reminderRepository: FakeReminderRepository
    private lateinit var imageProcessor: FakeChatImageProcessor
    private lateinit var memoryDao: MemoryDao
    private lateinit var agentStateDao: AgentStateDao
    private lateinit var appPreferences: AppPreferences
    private lateinit var localQwenDownloader: FakeLocalQwenModelDownloader
    private val testDispatcher = UnconfinedTestDispatcher()

    private val configRepo: ConfigRepository = mockk(relaxed = true) {
        every { getCurrentLlmConfig() } returns flowOf(
            LlmConfig(
                provider = com.xiaoqi.companion.data.db.converter.LlmProvider.GLM,
                baseUrl = "https://open.bigmodel.cn/api/paas/v1",
                apiKey = "test-key",
                modelName = "glm-5v-turbo",
            )
        )
        every { observeLlmConfigStatus() } returns flowOf(
            LlmConfigStatus(
                provider = com.xiaoqi.companion.data.db.converter.LlmProvider.GLM,
                baseUrl = "https://open.bigmodel.cn/api/paas/v1",
                hasApiKey = true,
                modelName = "glm-5v-turbo",
            )
        )
    }

    private val messageRepo: MessageRepository = mockk(relaxed = true) {
        every { getMessagesBySession("default") } returns flowOf(emptyList())
    }

    private fun mockAppPreferences(): AppPreferences =
        mockk(relaxed = true) {
            every { deviceStatusContextEnabled } returns flowOf(true)
            every { locationContextEnabled } returns flowOf(true)
            every { weatherContextEnabled } returns flowOf(true)
            every { reminderToolEnabled } returns flowOf(true)
            every { notificationEnabled } returns flowOf(true)
            every { mcpServerName } returns flowOf("Local MCP")
            every { mcpHttpUrl } returns flowOf("https://old.example/mcp")
        }

    private class FakeToolCallRepository : ToolCallRepository {
        val calls = MutableStateFlow<List<ToolCallSnapshot>>(emptyList())

        override fun observeBySession(sessionId: String) = calls
    }

    private class FakeReminderRepository : ReminderRepository {
        val reminders = MutableStateFlow<List<ReminderEntity>>(emptyList())
        val canceledIds = mutableListOf<String>()

        override fun observeReminders(): Flow<List<ReminderEntity>> = reminders
        override fun canScheduleExactReminders(): Boolean = true
        override suspend fun createReminder(
            title: String,
            message: String,
            triggerAtMillis: Long,
            exact: Boolean,
            source: String,
        ) = error("not used in ChatViewModelTest")

        override suspend fun cancelReminder(reminderId: String) {
            canceledIds += reminderId
        }
    }

    private class FakeLocalQwenModelDownloader : LocalQwenModelDownloader {
        val requestedDownloads = mutableListOf<String>()
        var installed = false

        override fun observeStatus(modelName: String): Flow<LocalQwenModelDownloadState> =
            flowOf(
                LocalQwenModelDownloadState(
                    modelName = modelName,
                    isInstalled = installed,
                    message = if (installed) "Installed" else "Not installed",
                )
            )

        override fun download(modelName: String): Flow<LocalQwenModelDownloadState> = flow {
            requestedDownloads += modelName
            emit(
                LocalQwenModelDownloadState(
                    modelName = modelName,
                    isInstalled = false,
                    isDownloading = true,
                    progress = 0.5f,
                    message = "Downloading",
                )
            )
            installed = true
            emit(
                LocalQwenModelDownloadState(
                    modelName = modelName,
                    isInstalled = true,
                    progress = 1f,
                    message = "Download complete",
                )
            )
        }
    }

    private class FakeCompanionRuntime(
        configRepo: ConfigRepository,
        messageRepo: MessageRepository,
    ) : CompanionRuntime(
        configRepository = configRepo,
        koogAgentFactory = mockk(),
        promptBuilder = mockk(),
        outputParser = OutputParser(),
        messageRepository = messageRepo,
        memoryRepository = mockk<MemoryRepository>(relaxed = true) {
            coEvery { selectPromptContext(any()) } returns PromptMemoryContext(
                memorySnippets = emptyList(),
                memoryIds = emptyList(),
                summarySnippets = emptyList(),
                summaryIds = emptyList(),
            )
        },
        conversationContextBuilder = ConversationContextBuilder(messageRepo),
        conversationReflection = mockk(relaxed = true) {
            coEvery { reflectAndSave(any(), any(), any()) } returns ConversationReflectionResult()
        },
        emotionMachine = mockk(relaxed = true),
        relationshipModel = mockk(relaxed = true),
    ) {
        var rawResponse = "[mood:happy][intensity:0.7] 你好呀！"
        var shouldFail = false
        var emitToolEvents = false
        var toolEvents: List<AgentToolCall> = emptyList()
        var memorySavedCount = 0
        var emitStreaming = false
        var streamingDeltas: List<String> = emptyList()
        var failAfterStreaming = false
        var completeDelayMs = 0L
        var sendCalled = false
        var lastInput: UserInput? = null

        override suspend fun send(input: UserInput) = flow<AgentEvent> {
            sendCalled = true
            lastInput = input
            if (shouldFail) {
                emit(AgentEvent.Error(AgentError.ApiError("API error")))
            } else {
                if (emitToolEvents) {
                    emit(AgentEvent.ToolCallUpdated(AgentToolCall("save_memory", ToolCallStatus.STARTED)))
                    emit(AgentEvent.ToolCallUpdated(AgentToolCall("save_memory", ToolCallStatus.SUCCEEDED)))
                }
                toolEvents.forEach { call ->
                    emit(AgentEvent.ToolCallUpdated(call))
                }
                if (memorySavedCount > 0) {
                    emit(AgentEvent.MemorySaved(memorySavedCount))
                }
                if (emitStreaming) {
                    emit(AgentEvent.Streaming("hello"))
                }
                streamingDeltas.forEach { delta ->
                    emit(AgentEvent.Streaming(delta))
                }
                if (failAfterStreaming) {
                    emit(AgentEvent.Error(AgentError.ApiError("stream interrupted")))
                    return@flow
                }
                if (completeDelayMs > 0L) {
                    delay(completeDelayMs)
                }
                val parsed = OutputParser().parse(rawResponse)
                emit(AgentEvent.Complete(parsed))
            }
        }
    }

    private class FakeChatImageProcessor : ChatImageProcessor {
        var shouldFail = false

        override suspend fun prepare(uriString: String): PreparedChatImage {
            if (shouldFail) error("bad image")
            return PreparedChatImage(
                uriString = uriString,
                imageBase64 = "prepared-base64",
                mediaType = "image/jpeg",
            )
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRuntime = FakeCompanionRuntime(configRepo, messageRepo)
        toolCallRepository = FakeToolCallRepository()
        reminderRepository = FakeReminderRepository()
        imageProcessor = FakeChatImageProcessor()
        memoryDao = mockk(relaxed = true) {
            every { observeAll() } returns flowOf(emptyList())
        }
        agentStateDao = mockk(relaxed = true) {
            every { observeByCompanionId("default") } returns flowOf(null)
        }
        appPreferences = mockAppPreferences()
        localQwenDownloader = FakeLocalQwenModelDownloader()
        viewModel = ChatViewModel(
            fakeRuntime,
            ToolDisplayRegistry(),
            toolCallRepository,
            configRepo,
            imageProcessor,
            messageRepo,
            memoryDao,
            agentStateDao,
            PresenceController(),
            appPreferences,
            reminderRepository,
            localQwenDownloader,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_isIdleWithEmptyMessages() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.messages.isEmpty())
            assertFalse(state.isLoading)
            assertEquals("", state.inputText)
            assertNull(state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun sendMessage_appendsUserMessageAndShowsReply() = runTest {
        viewModel.sendMessage("hello")

        assertTrue(viewModel.uiState.value.messages.any { it.content == "hello" })
        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.messages.any { it.role == "ASSISTANT" })
    }

    @Test
    fun sendMessage_clearsInputText() = runTest {
        viewModel.updateInputText("hello")
        assertEquals("hello", viewModel.uiState.value.inputText)

        viewModel.sendMessage("hello")
        assertEquals("", viewModel.uiState.value.inputText)
    }

    @Test
    fun sendMessage_ignoresEmptyInput() = runTest {
        viewModel.sendMessage("")
        viewModel.sendMessage("   ")
        assertFalse(fakeRuntime.sendCalled)
    }

    @Test
    fun attachImage_preparesImageAndSendMessageUsesVisionInput() = runTest {
        viewModel.attachImage("content://image/1")
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.pendingImage)

        viewModel.sendMessage("看这个")

        val input = fakeRuntime.lastInput
        assertTrue(input is UserInput.Vision)
        input as UserInput.Vision
        assertEquals("看这个", input.text)
        assertEquals("prepared-base64", input.imageBase64)
        assertNull(viewModel.uiState.value.pendingImage)
        assertTrue(viewModel.uiState.value.messages.any { it.imageUri == "content://image/1" })
    }

    @Test
    fun sendMessage_withImageOnlyUsesDefaultVisionPrompt() = runTest {
        viewModel.attachImage("content://image/2")
        advanceUntilIdle()

        viewModel.sendMessage("")

        val input = fakeRuntime.lastInput
        assertTrue(input is UserInput.Vision)
        input as UserInput.Vision
        assertTrue(input.text.contains("给你看这张图片"))
        assertEquals("Shared a picture", input.content)
        assertTrue(viewModel.uiState.value.messages.any { it.content == "Shared a picture" })
    }

    @Test
    fun restoredImageMessage_usesDataUriForDisplay() = runTest {
        val imageMessageRepo: MessageRepository = mockk(relaxed = true) {
            every { getMessagesBySession("default") } returns flowOf(
                listOf(
                    MessageEntity(
                        id = "image-message",
                        sessionId = "default",
                        role = MessageRole.USER,
                        content = "Shared a picture",
                        imageBase64 = "stored-base64",
                        timestamp = 1_000L,
                    )
                )
            )
        }
        val imageViewModel = ChatViewModel(
            fakeRuntime,
            ToolDisplayRegistry(),
            toolCallRepository,
            configRepo,
            imageProcessor,
            imageMessageRepo,
            memoryDao,
            agentStateDao,
            PresenceController(),
            appPreferences,
            reminderRepository,
            localQwenDownloader,
        )

        advanceUntilIdle()

        val message = imageViewModel.uiState.value.messages.single()
        assertEquals("data:image/jpeg;base64,stored-base64", message.imageUri)
    }

    @Test
    fun attachImage_whenProcessorFailsShowsError() = runTest {
        imageProcessor.shouldFail = true

        viewModel.attachImage("content://image/bad")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pendingImage)
        assertEquals("图片处理失败，请换一张试试。", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isPreparingImage)
    }

    @Test
    fun deleteMemory_removesMemoryById() = runTest {
        viewModel.deleteMemory("memory-1")
        advanceUntilIdle()

        coVerify { memoryDao.deleteById("memory-1") }
    }

    @Test
    fun sendMessage_blocksWhenConfigIsNotReady() = runTest {
        val blockedConfigRepo: ConfigRepository = mockk {
            every { observeLlmConfigStatus() } returns flowOf(
                LlmConfigStatus(
                    provider = com.xiaoqi.companion.data.db.converter.LlmProvider.GLM,
                    baseUrl = "https://open.bigmodel.cn/api/paas/v1",
                    hasApiKey = false,
                    modelName = "glm-5v-turbo",
                )
            )
            every { getCurrentLlmConfig() } returns flowOf(
                LlmConfig(
                    provider = com.xiaoqi.companion.data.db.converter.LlmProvider.GLM,
                    baseUrl = "https://open.bigmodel.cn/api/paas/v1",
                    apiKey = "",
                    modelName = "glm-5v-turbo",
                )
            )
        }
        val blockedRuntime = FakeCompanionRuntime(blockedConfigRepo, messageRepo)
        val blockedViewModel = ChatViewModel(
            blockedRuntime,
            ToolDisplayRegistry(),
            toolCallRepository,
            blockedConfigRepo,
            imageProcessor,
            messageRepo,
            memoryDao,
            agentStateDao,
            PresenceController(),
            appPreferences,
            reminderRepository,
            localQwenDownloader,
        )

        blockedViewModel.sendMessage("hello")

        assertFalse(blockedRuntime.sendCalled)
        assertEquals("缺少 API Key", blockedViewModel.uiState.value.error)
    }

    @Test
    fun saveSettings_persistsProviderModelAndApiKey() = runTest {
        viewModel.openSettings()
        viewModel.updateSettingsProvider(com.xiaoqi.companion.data.db.converter.LlmProvider.KIMI)
        viewModel.updateSettingsModelName(DefaultLlmValues.KIMI_MODEL)
        viewModel.updateSettingsApiKey("new-key")

        viewModel.saveSettings()
        advanceUntilIdle()

        coVerify { configRepo.setLlmProvider(com.xiaoqi.companion.data.db.converter.LlmProvider.KIMI) }
        coVerify { configRepo.setModelName(DefaultLlmValues.KIMI_MODEL) }
        coVerify { configRepo.setBaseUrl(DefaultLlmValues.KIMI_BASE_URL) }
        coVerify { configRepo.setApiKey("new-key") }
        assertFalse(viewModel.uiState.value.isSettingsOpen)
    }

    @Test
    fun saveSettings_blankModelNameFallsBackToProviderDefault() = runTest {
        viewModel.openSettings()
        viewModel.updateSettingsModelName("   ")

        viewModel.saveSettings()
        advanceUntilIdle()

        coVerify { configRepo.setModelName(DefaultLlmValues.GLM_MODEL) }
        assertFalse(viewModel.uiState.value.isSettingsOpen)
    }

    @Test
    fun downloadSelectedLocalQwenModel_updatesModelDownloadState() = runTest {
        viewModel.openSettings()
        viewModel.updateSettingsProvider(com.xiaoqi.companion.data.db.converter.LlmProvider.LOCAL_QWEN)
        viewModel.updateSettingsModelName(DefaultLlmValues.LOCAL_QWEN_MODEL)

        viewModel.downloadSelectedLocalQwenModel()
        advanceUntilIdle()

        assertEquals(listOf(DefaultLlmValues.LOCAL_QWEN_MODEL), localQwenDownloader.requestedDownloads)
        assertTrue(viewModel.uiState.value.localQwenDownload.isInstalled)
        assertEquals(1f, viewModel.uiState.value.localQwenDownload.progress, 0.001f)
    }

    @Test
    fun openMcpSettings_loadsCurrentMcpUrl() = runTest {
        advanceUntilIdle()

        viewModel.openMcpSettings()

        assertTrue(viewModel.uiState.value.isMcpSettingsOpen)
        assertEquals("https://old.example/mcp", viewModel.uiState.value.mcpSettingsUrl)
    }

    @Test
    fun saveMcpSettings_persistsMcpUrl() = runTest {
        viewModel.openMcpSettings()
        viewModel.updateMcpSettingsUrl("https://new.example/mcp")

        viewModel.saveMcpSettings()
        advanceUntilIdle()

        coVerify { appPreferences.setMcpHttpUrl("https://new.example/mcp") }
        assertFalse(viewModel.uiState.value.isMcpSettingsOpen)
    }

    @Test
    fun saveMcpSettings_rejectsInvalidUrl() = runTest {
        viewModel.openMcpSettings()
        viewModel.updateMcpSettingsUrl("ftp://bad.example/mcp")

        viewModel.saveMcpSettings()

        assertEquals("MCP URL must start with http:// or https://", viewModel.uiState.value.mcpSettingsMessage)
        assertTrue(viewModel.uiState.value.isMcpSettingsOpen)
    }

    @Test
    fun observeReminders_updatesReminderState() = runTest {
        reminderRepository.reminders.value = listOf(
            reminderEntity(id = "reminder-1", title = "Water", status = "SCHEDULED")
        )
        advanceUntilIdle()

        assertEquals("Water", viewModel.uiState.value.reminders.single().title)
        assertEquals("SCHEDULED", viewModel.uiState.value.reminders.single().status)
    }

    @Test
    fun cancelReminder_delegatesToRepository() = runTest {
        viewModel.cancelReminder("reminder-1")
        advanceUntilIdle()

        assertEquals(listOf("reminder-1"), reminderRepository.canceledIds)
    }

    @Test
    fun sendMessage_onError_setsErrorAndStopsLoading() = runTest {
        fakeRuntime.shouldFail = true
        viewModel.sendMessage("hi")

        assertNotNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun sendMessage_errorAfterStreaming_keepsPartialAssistantMessage() = runTest {
        fakeRuntime.streamingDeltas = listOf("partial reply")
        fakeRuntime.failAfterStreaming = true

        viewModel.sendMessage("hi")

        val assistant = viewModel.uiState.value.messages.first { it.role == "ASSISTANT" }
        assertEquals("partial reply", assistant.content)
        assertFalse(assistant.isStreaming)
        assertEquals("回复未完整完成", assistant.toolStatus)
        assertNotNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun sendMessage_afterError_canRetry() = runTest {
        fakeRuntime.shouldFail = true
        viewModel.sendMessage("fail")
        assertNotNull(viewModel.uiState.value.error)

        fakeRuntime.shouldFail = false
        viewModel.sendMessage("retry")

        assertTrue(viewModel.uiState.value.messages.any { it.role == "ASSISTANT" })
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun clearError_removesErrorState() = runTest {
        fakeRuntime.shouldFail = true
        viewModel.sendMessage("fail")
        assertNotNull(viewModel.uiState.value.error)

        viewModel.clearError()
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun updateInputText_reflectsInState() = runTest {
        viewModel.updateInputText("new text")
        viewModel.uiState.test { assertEquals("new text", awaitItem().inputText); cancelAndIgnoreRemainingEvents() }
    }

    @Test
    fun sendMessage_toolEvents_showStatusOnAssistantBubble() = runTest {
        fakeRuntime.emitToolEvents = true

        viewModel.sendMessage("remember tea")

        val assistant = viewModel.uiState.value.messages.first { it.role == "ASSISTANT" }
        assertEquals("已保存记忆", assistant.toolStatus)
    }

    @Test
    fun sendMessage_memorySavedEvent_showsPostResponseMemoryStatus() = runTest {
        fakeRuntime.memorySavedCount = 1

        viewModel.sendMessage("remember tea")

        val assistant = viewModel.uiState.value.messages.first { it.role == "ASSISTANT" }
        assertEquals("已记住 1 条", assistant.toolStatus)
    }

    @Test
    fun sendMessage_exactReminderMissingPermission_showsPermissionPrompt() = runTest {
        fakeRuntime.toolEvents = listOf(
            AgentToolCall(
                name = "create_local_reminder",
                status = ToolCallStatus.SUCCEEDED,
                resultJson = """{"status":"disabled","reason":"exact_alarm_permission_missing"}""",
            )
        )

        viewModel.sendMessage("remind me exactly in 10 minutes")

        val prompt = viewModel.uiState.value.permissionPrompt
        assertEquals(ChatPermissionType.EXACT_ALARM, prompt?.type)
        assertEquals("Open settings", prompt?.primaryActionLabel)
    }

    @Test
    fun dismissPermissionPrompt_clearsPrompt() = runTest {
        fakeRuntime.toolEvents = listOf(
            AgentToolCall(
                name = "create_local_reminder",
                status = ToolCallStatus.SUCCEEDED,
                resultJson = """{"status":"disabled","reason":"exact_alarm_permission_missing"}""",
            )
        )
        viewModel.sendMessage("remind me exactly in 10 minutes")

        viewModel.dismissPermissionPrompt()

        assertEquals(null, viewModel.uiState.value.permissionPrompt)
    }

    @Test
    fun updateInputText_setsPresenceToListening() = runTest {
        viewModel.updateInputText("hello")

        assertEquals(PresenceMode.LISTENING, viewModel.uiState.value.presence.mode)
    }

    @Test
    fun sendMessage_streamingDelta_setsPresenceToSpeaking() = runTest {
        fakeRuntime.emitStreaming = true

        viewModel.sendMessage("hello")

        assertEquals(PresenceMode.HAPPY, viewModel.uiState.value.presence.mode)
        assertTrue(viewModel.uiState.value.messages.any { it.role == "ASSISTANT" })
    }

    @Test
    fun sendMessage_batchesSmallStreamingDeltas() = runTest {
        fakeRuntime.rawResponse = "abc"
        fakeRuntime.streamingDeltas = listOf("a", "b", "c")
        fakeRuntime.completeDelayMs = 200L

        viewModel.sendMessage("hello")

        assertEquals("a", viewModel.uiState.value.messages.last { it.role == "ASSISTANT" }.content)

        advanceTimeBy(91L)

        assertEquals("abc", viewModel.uiState.value.messages.last { it.role == "ASSISTANT" }.content)

        advanceUntilIdle()
    }

    @Test
    fun toolRepositoryRunningSearch_setsPresenceToSearching() = runTest {
        toolCallRepository.calls.value = listOf(
            toolCallSnapshot("search", "search_memory", ToolCallStatus.STARTED),
        )

        assertEquals(PresenceMode.SEARCHING, viewModel.uiState.value.presence.mode)
    }

    @Test
    fun onPresenceTapped_setsTemporaryTouchReaction() = runTest {
        viewModel.onPresenceTapped()

        assertEquals(
            com.xiaoqi.companion.core.presence.PresenceReaction.TOUCH_NUZZLE,
            viewModel.uiState.value.presence.reaction,
        )

        advanceTimeBy(1_301L)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.presence.reaction)
    }

    @Test
    fun sendMessage_completeEvent_persistsCompanionStatus() = runTest {
        fakeRuntime.rawResponse = "[mood:happy][intensity:0.7][affinity:+0.2] 你好呀！"

        viewModel.sendMessage("hello")

        coVerify {
            agentStateDao.insert(match {
                it.companionId == "default" &&
                    it.mood == "happy" &&
                    it.relationshipLevel == 0.2f &&
                    it.emotionVector.contains("0.7")
            })
        }
    }

    @Test
    fun init_observesRecentToolCalls() = runTest {
        toolCallRepository.calls.value = listOf(
            toolCallSnapshot("1", "save_memory", ToolCallStatus.SUCCEEDED, completedAt = 1_100L),
            toolCallSnapshot("2", "search_memory", ToolCallStatus.STARTED),
            toolCallSnapshot("3", "update_mood", ToolCallStatus.FAILED, errorMessage = "bad args"),
            toolCallSnapshot("4", "update_relationship", ToolCallStatus.SUCCEEDED),
        )

        val calls = viewModel.uiState.value.toolCalls
        assertEquals(3, calls.size)
        assertEquals("已保存记忆", calls[0].label)
        assertEquals("Done", calls[0].status)
        assertEquals(100L, calls[0].durationMs)
        assertEquals("搜索记忆中", calls[1].label)
        assertEquals("Failed", calls[2].status)
        assertEquals("bad args", calls[2].errorMessage)
    }

    private fun toolCallSnapshot(
        id: String,
        toolName: String,
        status: ToolCallStatus,
        completedAt: Long? = null,
        errorMessage: String? = null,
    ) = ToolCallSnapshot(
        id = id,
        sessionId = "default",
        toolName = toolName,
        status = status,
        argumentsJson = "{}",
        resultJson = null,
        errorMessage = errorMessage,
        startedAt = 1_000L,
        completedAt = completedAt,
    )

    private fun reminderEntity(
        id: String,
        title: String,
        status: String,
    ) = ReminderEntity(
        id = id,
        title = title,
        message = "Time to $title",
        triggerAtMillis = 2_000L,
        delayMillis = 1_000L,
        exact = true,
        status = status,
        createdAt = 1_000L,
        updatedAt = 1_000L,
    )
}
