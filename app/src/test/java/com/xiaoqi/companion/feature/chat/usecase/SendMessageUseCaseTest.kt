package com.xiaoqi.companion.feature.chat.usecase

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
import com.xiaoqi.companion.core.presence.PresenceController
import com.xiaoqi.companion.core.tools.ToolDisplayRegistry
import com.xiaoqi.companion.data.db.converter.LlmProvider
import com.xiaoqi.companion.data.db.converter.MessageRole
import com.xiaoqi.companion.data.db.entity.AgentStateEntity
import com.xiaoqi.companion.data.db.entity.MessageEntity
import com.xiaoqi.companion.data.repository.ConfigRepository
import com.xiaoqi.companion.data.repository.DefaultLlmValues
import com.xiaoqi.companion.data.repository.LlmConfig
import com.xiaoqi.companion.data.repository.LlmConfigStatus
import com.xiaoqi.companion.data.repository.MemoryRepository
import com.xiaoqi.companion.data.repository.MessageRepository
import com.xiaoqi.companion.data.repository.PromptMemoryContext
import com.xiaoqi.companion.data.datastore.AppPreferences
import com.xiaoqi.companion.feature.chat.ChatConfigStatus
import com.xiaoqi.companion.feature.chat.ChatImageAttachment
import com.xiaoqi.companion.feature.chat.ChatMessage
import com.xiaoqi.companion.feature.chat.ChatPermissionType
import com.xiaoqi.companion.feature.chat.ChatUiState
import com.xiaoqi.companion.feature.chat.PreparedChatImage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
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

@OptIn(ExperimentalCoroutinesApi::class)
class SendMessageUseCaseTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val state = MutableStateFlow(ChatUiState())
    private val update: (ChatUiState.() -> ChatUiState) -> Unit = { reducer -> state.update(reducer) }
    private lateinit var fakeRuntime: FakeCompanionRuntime
    private lateinit var imageProcessor: FakeChatImageProcessor
    private lateinit var sendMessageUseCase: SendMessageUseCase
    private val toolDisplayRegistry = ToolDisplayRegistry()
    private val presenceController = PresenceController()
    private val agentStateDao = mockk<com.xiaoqi.companion.data.db.dao.AgentStateDao>(relaxed = true) {
        coEvery { getByCompanionId("default") } returns null
    }
    private val configRepository: ConfigRepository = mockk(relaxed = true) {
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
    private val memoryRepo: MemoryRepository = mockk(relaxed = true) {
        coEvery { selectPromptContext(any()) } returns PromptMemoryContext(
            memorySnippets = emptyList(),
            memoryIds = emptyList(),
            summarySnippets = emptyList(),
            summaryIds = emptyList(),
        )
    }

    private class FakeCompanionRuntime(
        configRepo: ConfigRepository,
        messageRepo: MessageRepository,
        memoryRepo: MemoryRepository,
    ) : CompanionRuntime(
        configRepository = configRepo,
        koogAgentFactory = mockk(),
        promptBuilder = mockk(),
        outputParser = OutputParser(),
        messageRepository = messageRepo,
        memoryRepository = memoryRepo,
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
                toolEvents.forEach { call -> emit(AgentEvent.ToolCallUpdated(call)) }
                if (memorySavedCount > 0) {
                    emit(AgentEvent.MemorySaved(memorySavedCount))
                }
                if (emitStreaming) emit(AgentEvent.Streaming("hello"))
                streamingDeltas.forEach { delta -> emit(AgentEvent.Streaming(delta)) }
                if (failAfterStreaming) {
                    emit(AgentEvent.Error(AgentError.ApiError("stream interrupted")))
                    return@flow
                }
                if (completeDelayMs > 0L) delay(completeDelayMs)
                val parsed = OutputParser().parse(rawResponse)
                emit(AgentEvent.Complete(parsed))
            }
        }
    }

    private class FakeChatImageProcessor : com.xiaoqi.companion.feature.chat.ChatImageProcessor {
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

    private fun readyConfig(
        provider: LlmProvider = LlmProvider.GLM,
        modelName: String = "glm-5v-turbo",
        hasApiKey: Boolean = true,
    ) = ChatConfigStatus(
        label = "$provider · $modelName",
        isReady = true,
        detail = "Ready",
        provider = provider,
        modelName = modelName,
        baseUrl = "https://example",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRuntime = FakeCompanionRuntime(configRepository, messageRepo, memoryRepo)
        imageProcessor = FakeChatImageProcessor()
        sendMessageUseCase = SendMessageUseCase(
            runtime = fakeRuntime,
            imageProcessor = imageProcessor,
            toolDisplayRegistry = toolDisplayRegistry,
            presenceController = presenceController,
            agentStateDao = agentStateDao,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun sendMessage_appendsUserMessageAndShowsReply() = runTest {
        sendMessageUseCase("hello", null, readyConfig(), this, update)
        advanceUntilIdle()

        assertTrue(state.value.messages.any { it.content == "hello" })
        assertFalse(state.value.isLoading)
        assertTrue(state.value.messages.any { it.role == "ASSISTANT" })
    }

    @Test
    fun sendMessage_clearsInputText() = runTest {
        // 输入清除由 VM 处理;此测试在 ChatViewModelTest 中
        sendMessageUseCase("hello", null, readyConfig(), this, update)
        advanceUntilIdle()
        assertNotNull(state.value.messages.firstOrNull { it.content == "hello" })
    }

    @Test
    fun sendMessage_ignoresEmptyInput() = runTest {
        sendMessageUseCase("", null, readyConfig(), this, update)
        sendMessageUseCase("   ", null, readyConfig(), this, update)
        assertFalse(fakeRuntime.sendCalled)
    }

    @Test
    fun sendMessage_visionInput_withPendingImage() = runTest {
        val pending = ChatImageAttachment(
            uriString = "content://image/1",
            imageBase64 = "prepared-base64",
            mediaType = "image/jpeg",
        )
        sendMessageUseCase("看这个", pending, readyConfig(), this, update)
        advanceUntilIdle()

        val input = fakeRuntime.lastInput
        assertTrue(input is UserInput.Vision)
        input as UserInput.Vision
        assertEquals("看这个", input.text)
        assertEquals("prepared-base64", input.imageBase64)
        assertTrue(state.value.messages.any { it.imageUri == "content://image/1" })
    }

    @Test
    fun sendMessage_imageOnly_usesDefaultVisionPrompt() = runTest {
        val pending = ChatImageAttachment(
            uriString = "content://image/2",
            imageBase64 = "prepared-base64",
            mediaType = "image/jpeg",
        )
        sendMessageUseCase("", pending, readyConfig(), this, update)
        advanceUntilIdle()

        val input = fakeRuntime.lastInput
        assertTrue(input is UserInput.Vision)
        input as UserInput.Vision
        assertTrue(input.text.contains("给你看这张图片"))
        assertEquals("Shared a picture", input.content)
        assertTrue(state.value.messages.any { it.content == "Shared a picture" })
    }

    @Test
    fun sendMessage_blocksWhenConfigIsNotReady() = runTest {
        val blockedConfig = ChatConfigStatus(
            label = "GLM",
            isReady = false,
            detail = "缺少 API Key",
            provider = LlmProvider.GLM,
            modelName = "glm-5v-turbo",
            baseUrl = "https://example",
        )
        sendMessageUseCase("hello", null, blockedConfig, this, update)
        advanceUntilIdle()

        assertFalse(fakeRuntime.sendCalled)
        assertEquals("缺少 API Key", state.value.error)
    }

    @Test
    fun sendMessage_blocksLocalQwenWhenModelIsNotInstalled() = runTest {
        val localConfig = ChatConfigStatus(
            label = "LOCAL_QWEN",
            isReady = false,
            detail = "请先下载本地模型",
            provider = LlmProvider.LOCAL_QWEN,
            modelName = DefaultLlmValues.LOCAL_QWEN_MODEL,
            baseUrl = DefaultLlmValues.LOCAL_QWEN_BASE_URL,
        )
        sendMessageUseCase("hello", null, localConfig, this, update)
        advanceUntilIdle()

        assertFalse(fakeRuntime.sendCalled)
        assertEquals("请先下载本地模型", state.value.error)
    }

    @Test
    fun sendMessage_allowsLocalQwenWhenModelIsInstalled() = runTest {
        val localConfig = ChatConfigStatus(
            label = "LOCAL_QWEN",
            isReady = true,
            detail = "本地模型已安装",
            provider = LlmProvider.LOCAL_QWEN,
            modelName = DefaultLlmValues.LOCAL_QWEN_MODEL,
            baseUrl = DefaultLlmValues.LOCAL_QWEN_BASE_URL,
        )
        sendMessageUseCase("hello", null, localConfig, this, update)
        advanceUntilIdle()

        assertTrue(fakeRuntime.sendCalled)
    }

    @Test
    fun sendMessage_onError_setsErrorAndStopsLoading() = runTest {
        fakeRuntime.shouldFail = true
        sendMessageUseCase("hi", null, readyConfig(), this, update)
        advanceUntilIdle()

        assertNotNull(state.value.error)
        assertFalse(state.value.isLoading)
    }

    @Test
    fun sendMessage_errorAfterStreaming_keepsPartialAssistantMessage() = runTest {
        fakeRuntime.streamingDeltas = listOf("partial reply")
        fakeRuntime.failAfterStreaming = true
        sendMessageUseCase("hi", null, readyConfig(), this, update)
        advanceUntilIdle()

        val assistant = state.value.messages.first { it.role == "ASSISTANT" }
        assertEquals("partial reply", assistant.content)
        assertFalse(assistant.isStreaming)
        assertEquals("回复未完整完成", assistant.toolStatus)
        assertNotNull(state.value.error)
        assertFalse(state.value.isLoading)
    }

    @Test
    fun sendMessage_afterError_canRetry() = runTest {
        fakeRuntime.shouldFail = true
        sendMessageUseCase("fail", null, readyConfig(), this, update)
        advanceUntilIdle()
        assertNotNull(state.value.error)

        fakeRuntime.shouldFail = false
        sendMessageUseCase("retry", null, readyConfig(), this, update)
        advanceUntilIdle()

        assertTrue(state.value.messages.any { it.role == "ASSISTANT" })
    }

    @Test
    fun sendMessage_toolEvents_showStatusOnAssistantBubble() = runTest {
        fakeRuntime.emitToolEvents = true
        sendMessageUseCase("remember tea", null, readyConfig(), this, update)
        advanceUntilIdle()

        val assistant = state.value.messages.first { it.role == "ASSISTANT" }
        assertEquals("已保存记忆", assistant.toolStatus)
    }

    @Test
    fun sendMessage_memorySavedEvent_showsPostResponseMemoryStatus() = runTest {
        fakeRuntime.memorySavedCount = 1
        sendMessageUseCase("remember tea", null, readyConfig(), this, update)
        advanceUntilIdle()

        val assistant = state.value.messages.first { it.role == "ASSISTANT" }
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
        sendMessageUseCase("remind me exactly in 10 minutes", null, readyConfig(), this, update)
        advanceUntilIdle()

        val prompt = state.value.permissionPrompt
        assertEquals(ChatPermissionType.EXACT_ALARM, prompt?.type)
        assertEquals("Open settings", prompt?.primaryActionLabel)
    }

    @Test
    fun sendMessage_completeEvent_persistsCompanionStatus() = runTest {
        fakeRuntime.rawResponse = "[mood:happy][intensity:0.7][affinity:+0.2] 你好呀！"
        sendMessageUseCase("hello", null, readyConfig(), this, update)
        advanceUntilIdle()

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
    fun sendMessage_batchesSmallStreamingDeltas() = runTest {
        fakeRuntime.rawResponse = "abc"
        fakeRuntime.streamingDeltas = listOf("a", "b", "c")
        fakeRuntime.completeDelayMs = 200L

        sendMessageUseCase("hello", null, readyConfig(), this, update)
        advanceTimeBy(91L)
        advanceUntilIdle()

        // 3 个小 delta 在 batch 之后会按到达顺序 flush,最终内容等于完整字符串
        assertEquals("abc", state.value.messages.last { it.role == "ASSISTANT" }.content)
    }
}
