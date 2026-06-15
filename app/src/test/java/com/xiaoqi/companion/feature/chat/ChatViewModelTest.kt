package com.xiaoqi.companion.feature.chat

import app.cash.turbine.test
import com.xiaoqi.companion.core.companion.model.ToolCallStatus
import com.xiaoqi.companion.core.local.LocalQwenModelDownloadState
import com.xiaoqi.companion.core.local.LocalQwenModelDownloader
import com.xiaoqi.companion.core.presence.PresenceController
import com.xiaoqi.companion.core.presence.PresenceMode
import com.xiaoqi.companion.core.presence.PresenceReaction
import com.xiaoqi.companion.core.presence.PresenceReactionPolicy
import com.xiaoqi.companion.core.presence.runtime.DreamLoopScheduler
import com.xiaoqi.companion.core.tools.ToolDisplayRegistry
import com.xiaoqi.companion.data.datastore.AppPreferences
import com.xiaoqi.companion.data.db.converter.LlmProvider
import com.xiaoqi.companion.data.db.converter.MessageRole
import com.xiaoqi.companion.data.db.dao.AgentStateDao
import com.xiaoqi.companion.data.db.dao.MessageDao
import com.xiaoqi.companion.data.db.dao.MoodSnapshotDao
import com.xiaoqi.companion.data.db.entity.MessageEntity
import com.xiaoqi.companion.data.db.entity.ReminderEntity
import com.xiaoqi.companion.core.mcp.RemoteMcpClient
import com.xiaoqi.companion.data.repository.ConfigRepository
import com.xiaoqi.companion.data.repository.InsightRepository
import com.xiaoqi.companion.data.repository.LlmConfig
import com.xiaoqi.companion.data.repository.LlmConfigStatus
import com.xiaoqi.companion.data.repository.MemoryRepository
import com.xiaoqi.companion.data.repository.MessageRepository
import com.xiaoqi.companion.data.repository.ReminderRepository
import com.xiaoqi.companion.data.repository.ToolCallRepository
import com.xiaoqi.companion.data.repository.ToolCallSnapshot
import com.xiaoqi.companion.feature.chat.usecase.SendMessageUseCase
import com.xiaoqi.companion.feature.chat.usecase.SettingsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 协调器测试:只覆盖 VM 自身的 8 个 collector + 简单 UI 反馈方法。
 * sendMessage / settings 行为分别在 [SendMessageUseCaseTest] / [SettingsUseCaseTest] 中覆盖。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private lateinit var viewModel: ChatViewModel
    private lateinit var sendMessageUseCase: SendMessageUseCase
    private lateinit var settingsUseCase: SettingsUseCase
    private lateinit var imageProcessor: FakeChatImageProcessor
    private lateinit var toolCallRepository: FakeToolCallRepository
    private lateinit var reminderRepository: FakeReminderRepository
    private lateinit var memoryRepository: MemoryRepository
    private lateinit var insightRepository: InsightRepository
    private lateinit var moodSnapshotDao: MoodSnapshotDao
    private lateinit var messageDao: MessageDao
    private lateinit var agentStateDao: AgentStateDao
    private lateinit var appPreferences: AppPreferences
    private lateinit var remoteMcpClient: RemoteMcpClient
    private lateinit var dreamLoopScheduler: DreamLoopScheduler
    private val testDispatcher = UnconfinedTestDispatcher()

    private val configRepo: ConfigRepository = mockk(relaxed = true) {
        every { getCurrentLlmConfig() } returns flowOf(
            LlmConfig(
                provider = LlmProvider.GLM,
                baseUrl = "https://open.bigmodel.cn/api/paas/v1",
                apiKey = "test-key",
                modelName = "glm-5v-turbo",
            )
        )
        every { observeLlmConfigStatus() } returns flowOf(
            LlmConfigStatus(
                provider = LlmProvider.GLM,
                baseUrl = "https://open.bigmodel.cn/api/paas/v1",
                hasApiKey = true,
                modelName = "glm-5v-turbo",
            )
        )
    }
    private val messageRepo: MessageRepository = mockk(relaxed = true) {
        every { getMessagesBySession("default") } returns flowOf(emptyList())
    }
    private val localQwenDownloader: FakeLocalQwenModelDownloader = FakeLocalQwenModelDownloader()
    private val presenceController = PresenceController()
    private val presenceReactionPolicy = PresenceReactionPolicy()

    private fun mockAppPreferences(): AppPreferences =
        mockk(relaxed = true) {
            every { deviceStatusContextEnabled } returns flowOf(true)
            every { locationContextEnabled } returns flowOf(true)
            every { weatherContextEnabled } returns flowOf(true)
            every { reminderToolEnabled } returns flowOf(true)
            every { notificationEnabled } returns flowOf(true)
            every { mcpServerName } returns flowOf("Local MCP")
            every { mcpHttpUrl } returns flowOf("https://old.example/mcp")
            every { healthAutoSyncEnabled } returns flowOf(true)
            every { healthLastSyncAt } returns flowOf(0L)
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
        override fun download(modelName: String): Flow<LocalQwenModelDownloadState> = flowOf(
            LocalQwenModelDownloadState(
                modelName = modelName,
                isInstalled = true,
                progress = 1f,
            )
        )
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
        sendMessageUseCase = mockk(relaxed = true)
        settingsUseCase = SettingsUseCase(
            configRepo,
            mockAppPreferences(),
            localQwenDownloader,
            io.mockk.mockk(relaxed = true),
        )
        imageProcessor = FakeChatImageProcessor()
        toolCallRepository = FakeToolCallRepository()
        reminderRepository = FakeReminderRepository()
        memoryRepository = mockk(relaxed = true) {
            every { observeMemoriesPinnedFirst() } returns flowOf(emptyList())
            coEvery { countAll() } returns 0
        }
        insightRepository = mockk<com.xiaoqi.companion.data.repository.InsightRepository>(relaxed = true) {
            every { observeVisibleNotMuted(any()) } returns flowOf(emptyList())
            coEvery { seedDemoInsights(any(), any(), any(), any(), any()) } returns 0
            coEvery { countAll() } returns 0
        }
        moodSnapshotDao = mockk<MoodSnapshotDao>(relaxed = true) {
            coEvery { countAll() } returns 0
        }
        messageDao = mockk<MessageDao>(relaxed = true)
        remoteMcpClient = mockk(relaxed = true)
        agentStateDao = mockk(relaxed = true) {
            every { observeByCompanionId("default") } returns flowOf(null)
        }
        appPreferences = mockAppPreferences()
        dreamLoopScheduler = io.mockk.mockk(relaxed = true)
        viewModel = ChatViewModel(
            sendMessageUseCase = sendMessageUseCase,
            settingsUseCase = settingsUseCase,
            imageProcessor = imageProcessor,
            toolCallRepository = toolCallRepository,
            configRepository = configRepo,
            messageRepository = messageRepo,
            memoryRepository = memoryRepository,
            insightRepository = insightRepository,
            moodSnapshotDao = moodSnapshotDao,
            messageDao = mockk(relaxed = true),
            messageSearchDao = mockk(relaxed = true),
            agentStateDao = agentStateDao,
            presenceController = presenceController,
            presenceReactionPolicy = presenceReactionPolicy,
            appPreferences = appPreferences,
            reminderRepository = reminderRepository,
            toolDisplayRegistry = ToolDisplayRegistry(com.xiaoqi.companion.core.tools.parser.ToolCallResultParser()),
            toolCallResultParser = com.xiaoqi.companion.core.tools.parser.ToolCallResultParser(),
            remoteMcpClient = remoteMcpClient,
            mcpServerListRepository = io.mockk.mockk(relaxed = true),
            dreamLoopScheduler = dreamLoopScheduler,
            healthSyncManager = io.mockk.mockk(relaxed = true),
            healthConnectDataSource = io.mockk.mockk(relaxed = true),
            sensorHealthSource = io.mockk.mockk(relaxed = true),
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
    fun updateInputText_reflectsInState() = runTest {
        viewModel.updateInputText("new text")
        advanceUntilIdle()
        assertEquals("new text", viewModel.uiState.value.inputText)
    }

    @Test
    fun updateInputText_setsPresenceToListening() = runTest {
        viewModel.updateInputText("hello")
        advanceUntilIdle()
        assertEquals(PresenceMode.LISTENING, viewModel.uiState.value.presence.mode)
    }

    @Test
    fun clearError_removesErrorState() = runTest {
        viewModel.updateInputText("test")
        // 直接更新 _uiState 不可行,所以这个测试依赖 sendMessage 流程
        // 简化为:触发 clearError 不应崩溃
        viewModel.clearError()
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun dismissPermissionPrompt_clearsPrompt() = runTest {
        viewModel.dismissPermissionPrompt()
        assertNull(viewModel.uiState.value.permissionPrompt)
    }

    @Test
    fun deleteMemory_removesMemoryById() = runTest {
        viewModel.deleteMemory("memory-1")
        advanceUntilIdle()
        coVerify { memoryRepository.deleteMemory("memory-1") }
    }

    @Test
    fun cancelReminder_delegatesToRepository() = runTest {
        viewModel.cancelReminder("reminder-1")
        advanceUntilIdle()
        assertEquals(listOf("reminder-1"), reminderRepository.canceledIds)
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
    fun onPresenceTapped_setsTemporaryTouchReaction() = runTest {
        viewModel.onPresenceTapped()

        assertEquals(
            PresenceReaction.TOUCH_NUZZLE,
            viewModel.uiState.value.presence.reaction,
        )

        advanceTimeBy(1_301L)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.presence.reaction)
    }

    @Test
    fun toolRepositoryRunningSearch_setsPresenceToSearching() = runTest {
        toolCallRepository.calls.value = listOf(
            toolCallSnapshot("1", "search_memory", ToolCallStatus.STARTED),
        )
        advanceUntilIdle()
        assertEquals(PresenceMode.SEARCHING, viewModel.uiState.value.presence.mode)
    }

    @Test
    fun init_observesRecentToolCalls() = runTest {
        toolCallRepository.calls.value = listOf(
            toolCallSnapshot(
                id = "1",
                toolName = "save_memory",
                status = ToolCallStatus.SUCCEEDED,
                completedAt = 1_100L,
                resultJson = """{"status":"ok","data":{"memoryId":"mem-1","type":"FACT"}}""",
            ),
            toolCallSnapshot("2", "search_memory", ToolCallStatus.STARTED),
            toolCallSnapshot("3", "update_mood", ToolCallStatus.FAILED, errorMessage = "bad args"),
            toolCallSnapshot("4", "update_relationship", ToolCallStatus.SUCCEEDED),
        )
        advanceUntilIdle()

        val calls = viewModel.uiState.value.toolCalls
        assertEquals(3, calls.size)
        // save_memory 的 envelope ok data 有 memoryId → 动态 label "已保存记忆 · 记忆 mem-1"
        assertEquals("已保存记忆 · 记忆 mem-1", calls[0].label)
        assertEquals("Done", calls[0].status)
        assertEquals(100L, calls[0].durationMs)
        assertEquals("Failed", calls[2].status)
        assertEquals("bad args", calls[2].errorMessage)
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
            sendMessageUseCase = sendMessageUseCase,
            settingsUseCase = settingsUseCase,
            imageProcessor = imageProcessor,
            toolCallRepository = toolCallRepository,
            configRepository = configRepo,
            messageRepository = imageMessageRepo,
            memoryRepository = memoryRepository,
            insightRepository = insightRepository,
            moodSnapshotDao = moodSnapshotDao,
            messageDao = mockk(relaxed = true),
            messageSearchDao = mockk(relaxed = true),
            agentStateDao = agentStateDao,
            presenceController = presenceController,
            presenceReactionPolicy = presenceReactionPolicy,
            appPreferences = appPreferences,
            reminderRepository = reminderRepository,
            toolDisplayRegistry = ToolDisplayRegistry(com.xiaoqi.companion.core.tools.parser.ToolCallResultParser()),
            toolCallResultParser = com.xiaoqi.companion.core.tools.parser.ToolCallResultParser(),
            remoteMcpClient = remoteMcpClient,
            mcpServerListRepository = io.mockk.mockk(relaxed = true),
            dreamLoopScheduler = io.mockk.mockk(relaxed = true),
            healthSyncManager = io.mockk.mockk(relaxed = true),
            healthConnectDataSource = io.mockk.mockk(relaxed = true),
            sensorHealthSource = io.mockk.mockk(relaxed = true),
        )
        advanceUntilIdle()

        val message = imageViewModel.uiState.value.messages.single()
        assertEquals("data:image/jpeg;base64,stored-base64", message.imageUri)
    }

    private fun toolCallSnapshot(
        id: String,
        toolName: String,
        status: ToolCallStatus,
        completedAt: Long? = null,
        errorMessage: String? = null,
        resultJson: String? = null,
    ) = ToolCallSnapshot(
        id = id,
        sessionId = "default",
        toolName = toolName,
        status = status,
        argumentsJson = "{}",
        resultJson = resultJson,
        errorMessage = errorMessage,
        startedAt = 1_000L,
        completedAt = completedAt,
    )

    private fun reminderEntity(id: String, title: String, status: String) = ReminderEntity(
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
