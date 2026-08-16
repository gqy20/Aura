package com.xiaoqi.companion.feature.chat.usecase

import app.cash.turbine.test
import com.xiaoqi.companion.core.companion.CompanionRuntime
import com.xiaoqi.companion.core.companion.ConversationContextBuilder
import com.xiaoqi.companion.core.companion.EmotionStateMachine
import com.xiaoqi.companion.core.companion.AgentTurnPolicy
import com.xiaoqi.companion.core.companion.RelationshipModel
import com.xiaoqi.companion.core.companion.model.AgentError
import com.xiaoqi.companion.core.companion.model.AgentEvent
import com.xiaoqi.companion.core.companion.model.AgentToolCall
import com.xiaoqi.companion.core.companion.model.ToolCallStatus
import com.xiaoqi.companion.core.companion.model.UserInput
import com.xiaoqi.companion.core.context.CurrentLocationProvider
import com.xiaoqi.companion.core.presence.PresenceController
import com.xiaoqi.companion.core.tools.ToolDisplayRegistry
import com.xiaoqi.companion.data.db.converter.LlmProvider
import com.xiaoqi.companion.data.db.converter.MessageRole
import com.xiaoqi.companion.data.db.entity.AgentStateEntity
import com.xiaoqi.companion.data.db.entity.MessageEntity
import com.xiaoqi.companion.data.repository.ConfigRepository
import com.xiaoqi.companion.data.repository.ConversationRepository
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
import com.xiaoqi.companion.feature.chat.ChatMessageCompletionState
import com.xiaoqi.companion.feature.chat.ChatPermissionType
import com.xiaoqi.companion.feature.chat.ChatUiState
import com.xiaoqi.companion.feature.chat.PreparedChatImage
import com.xiaoqi.companion.testing.FakeChatImageProcessor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
    private val toolDisplayRegistry = ToolDisplayRegistry(com.xiaoqi.companion.core.tools.parser.ToolCallResultParser())
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

    private val emotionMachine: EmotionStateMachine = mockk(relaxed = true) {
        every { currentMood } returns "calm"
        every { latestIntensity } returns 0.5f
    }
    private val relationshipModel: RelationshipModel = mockk(relaxed = true) {
        every { currentLevel } returns 0.5f
    }

    private class FakeCompanionRuntime(
        configRepo: ConfigRepository,
        messageRepo: MessageRepository,
        memoryRepo: MemoryRepository,
        emotionMachine: EmotionStateMachine,
        relationshipModel: RelationshipModel,
    ) : CompanionRuntime(
        configRepository = configRepo,
        koogAgentFactory = mockk(),
        promptBuilder = mockk(),
        messageRepository = messageRepo,
        memoryRepository = memoryRepo,
        conversationContextBuilder = ConversationContextBuilder(messageRepo),
        emotionMachine = emotionMachine,
        relationshipModel = relationshipModel,
        locationProvider = mockk<CurrentLocationProvider>(relaxed = true),
        appPreferences = mockk<AppPreferences>(relaxed = true),
        conversationRepository = mockk<ConversationRepository>(relaxed = true),
        agentTurnPolicy = AgentTurnPolicy(),
    ) {
        var rawResponse = "你好呀！"
        var shouldFail = false
        var emitToolEvents = false
        var toolEvents: List<AgentToolCall> = emptyList()
        var memorySavedCount = 0
        var emitStreaming = false
        var streamingDeltas: List<String> = emptyList()
        var progressEvents: List<AgentEvent.Progress> = emptyList()
        var failAfterStreaming = false
        var completeDelayMs = 0L
        var persistedMessageId: String? = null
        var beforeComplete: (() -> Unit)? = null
        var scriptedEvents: List<AgentEvent> = emptyList()
        var sendCalled = false
        var lastInput: UserInput? = null

        override suspend fun send(input: UserInput) = flow<AgentEvent> {
            sendCalled = true
            lastInput = input
            if (shouldFail) {
                emit(AgentEvent.Error(AgentError.ApiError("API error")))
            } else {
                if (scriptedEvents.isNotEmpty()) {
                    scriptedEvents.forEach { emit(it) }
                } else {
                    if (emitToolEvents) {
                        emit(AgentEvent.ToolCallUpdated(AgentToolCall("save_memory", ToolCallStatus.STARTED)))
                        emit(AgentEvent.ToolCallUpdated(AgentToolCall("save_memory", ToolCallStatus.SUCCEEDED)))
                    }
                    toolEvents.forEach { call -> emit(AgentEvent.ToolCallUpdated(call)) }
                    progressEvents.forEach { progress -> emit(progress) }
                    if (memorySavedCount > 0) {
                        emit(AgentEvent.MemorySaved(memorySavedCount))
                    }
                    if (emitStreaming) emit(AgentEvent.Streaming("hello"))
                    streamingDeltas.forEach { delta -> emit(AgentEvent.Streaming(delta)) }
                }
                if (failAfterStreaming) {
                    emit(AgentEvent.Error(AgentError.ApiError("stream interrupted")))
                    return@flow
                }
                if (completeDelayMs > 0L) delay(completeDelayMs)
                beforeComplete?.invoke()
                emit(AgentEvent.Complete(rawResponse, persistedMessageId))
            }
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
        fakeRuntime = FakeCompanionRuntime(configRepository, messageRepo, memoryRepo, emotionMachine, relationshipModel)
        imageProcessor = FakeChatImageProcessor()
        sendMessageUseCase = SendMessageUseCase(
            runtime = fakeRuntime,
            imageProcessor = imageProcessor,
            toolDisplayRegistry = toolDisplayRegistry,
            presenceController = presenceController,
            agentStateDao = agentStateDao,
            memoryRepository = memoryRepo,
            emotionMachine = emotionMachine,
            relationshipModel = relationshipModel,
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
    fun sendMessage_visionInput_persistsVisionMemoryFireAndForget() = runTest {
        val pending = ChatImageAttachment(
            uriString = "content://image/1",
            imageBase64 = "prepared-base64",
            mediaType = "image/png",
        )
        sendMessageUseCase("看夕阳", pending, readyConfig(), this, update)
        advanceUntilIdle()

        coVerify {
            memoryRepo.saveVisionMemory(
                summary = "看夕阳",
                imageBase64 = "prepared-base64",
                imageMediaType = "image/png",
                importance = any(),
                confidence = any(),
                sourceMessageId = any(),
            )
        }
    }

    @Test
    fun sendMessage_visionInput_visionMemoryFailureDoesNotBreakFlow() = runTest {
        coEvery {
            memoryRepo.saveVisionMemory(
                summary = any(),
                imageBase64 = any(),
                imageMediaType = any(),
                importance = any(),
                confidence = any(),
                sourceMessageId = any(),
            )
        } throws RuntimeException("db down")
        val pending = ChatImageAttachment(
            uriString = "content://image/1",
            imageBase64 = "prepared-base64",
            mediaType = "image/jpeg",
        )
        sendMessageUseCase("看这个", pending, readyConfig(), this, update)
        advanceUntilIdle()

        // 主消息流照常完成 — assistant 仍在,无 error
        assertTrue(fakeRuntime.sendCalled)
        assertTrue(state.value.messages.any { it.role == "ASSISTANT" })
        assertNull(state.value.error)
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
        assertEquals("分享了一张图片", input.content)
        assertTrue(state.value.messages.any { it.content == "分享了一张图片" })
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
        assertEquals("服务暂时没有响应，请重试。", state.value.error)
        assertFalse(state.value.isLoading)
    }

    @Test
    fun sendMessage_onError_restoresInputWhenNothingWasProduced() = runTest {
        // 一无所获的失败回填输入,长文本不再因一次网络失败整段丢失
        fakeRuntime.shouldFail = true
        sendMessageUseCase("很长的一段心里话", null, readyConfig(), this, update)
        advanceUntilIdle()

        assertEquals("很长的一段心里话", state.value.inputText)
    }

    @Test
    fun sendMessage_onError_keepsInputEmptyWhenPartialReplyExists() = runTest {
        // 有部分回复时输入不回填,避免与保留的半截回复重复
        fakeRuntime.streamingDeltas = listOf("partial")
        fakeRuntime.failAfterStreaming = true
        sendMessageUseCase("hello", null, readyConfig(), this, update)
        advanceUntilIdle()

        assertEquals("", state.value.inputText)
        assertEquals("partial", state.value.messages.first { it.role == "ASSISTANT" }.content)
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
        assertEquals(ChatMessageCompletionState.FAILED, assistant.completionState)
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
    fun sendMessage_toolEvents_showStepsOnAssistantBubble() = runTest {
        fakeRuntime.emitToolEvents = true
        sendMessageUseCase("remember tea", null, readyConfig(), this, update)
        advanceUntilIdle()

        val assistant = state.value.messages.first { it.role == "ASSISTANT" }
        assertEquals(1, assistant.toolSteps.size)
        assertEquals(ToolCallStatus.SUCCEEDED, assistant.toolSteps.single().status)
        assertEquals("已保存", assistant.toolSteps.single().label)
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
    fun sendMessage_createLocalReminder_success_showsScheduledWithSubject() = runTest {
        // P0 修复:ToolCallUpdated 分支切到 resolveLabel 动态路径,
        // create_local_reminder 带 resultJson 时显示"已创建提醒 · {title}"。
        // CreateLocalReminderTool 真实输出格式:{status:scheduled, title, triggerAtEpochMillis, ...}
        fakeRuntime.toolEvents = listOf(
            AgentToolCall(
                name = "create_local_reminder",
                status = ToolCallStatus.SUCCEEDED,
                resultJson = """{"status":"scheduled","reminderId":"r-1","title":"吃药","triggerAtEpochMillis":1234}""",
            )
        )
        sendMessageUseCase("remind me to take medicine", null, readyConfig(), this, update)
        advanceUntilIdle()

        val assistant = state.value.messages.first { it.role == "ASSISTANT" }
        val step = assistant.toolSteps.single()
        assertEquals(ToolCallStatus.SUCCEEDED, step.status)
        assertEquals("已创建提醒 · 吃药", step.label)
    }

    @Test
    fun sendMessage_memorySaved_afterToolCall_doesNotOverwriteChip() = runTest {
        // P0 修复:MemorySaved 是 conversation 级别的后置 reflection 提示,
        // 不应覆盖最近一次 tool call 的 chip(否则"已创建提醒"会被改成"已记住")。
        fakeRuntime.toolEvents = listOf(
            AgentToolCall(
                name = "create_local_reminder",
                status = ToolCallStatus.SUCCEEDED,
                resultJson = """{"status":"scheduled","title":"吃药"}""",
            )
        )
        fakeRuntime.memorySavedCount = 2
        sendMessageUseCase("remind me to take medicine", null, readyConfig(), this, update)
        advanceUntilIdle()

        val assistant = state.value.messages.first { it.role == "ASSISTANT" }
        // 关键断言:时间线仍是 tool call 的"已创建提醒 · 吃药",
        // MemorySaved 的"已记住 2 条"不挤占步骤/状态。
        assertEquals("已创建提醒 · 吃药", assistant.toolSteps.single().label)
        assertNull(assistant.toolStatus)
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
        assertEquals("去设置", prompt?.primaryActionLabel)
    }

    @Test
    fun sendMessage_completeEvent_persistsCompanionStatus() = runTest {
        // update_state tool 已在上游写入情绪/关系;UseCase 从单例读取并持久化
        every { emotionMachine.currentMood } returns "happy"
        every { emotionMachine.latestIntensity } returns 0.7f
        every { relationshipModel.currentLevel } returns 0.7f
        fakeRuntime.rawResponse = "你好呀！"
        sendMessageUseCase("hello", null, readyConfig(), this, update)
        advanceUntilIdle()

        coVerify {
            agentStateDao.insert(match {
                it.companionId == "default" &&
                    it.mood == "happy" &&
                    it.relationshipLevel == 0.7f &&
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

    @Test
    fun sendMessage_firstTextDeltaReplacesProgressStatus() = runTest {
        fakeRuntime.progressEvents = listOf(
            AgentEvent.Progress("compound_task", "信息已齐，正在整理回答")
        )
        fakeRuntime.streamingDeltas = listOf("最终回答")
        fakeRuntime.rawResponse = "最终回答"

        sendMessageUseCase("hello", null, readyConfig(), this, update)
        advanceUntilIdle()

        val assistant = state.value.messages.last { it.role == "ASSISTANT" }
        assertEquals("最终回答", assistant.content)
        assertNull(assistant.toolStatus)
        assertNull(assistant.toolStatusType)
    }

    @Test
    fun sendMessage_streamReset_preservesPreToolTextAsIntentSegment() = runTest {
        fakeRuntime.rawResponse = "查到一家距离很近的咖啡店。"
        fakeRuntime.scriptedEvents = listOf(
            AgentEvent.Streaming("我先帮你查一下。"),
            AgentEvent.StreamingReset,
            AgentEvent.ToolCallUpdated(
                AgentToolCall("maps_around_search", ToolCallStatus.STARTED)
            ),
            AgentEvent.Streaming("查到一家距离很近的咖啡店。"),
        )

        sendMessageUseCase("附近的咖啡店", null, readyConfig(), this, update)
        advanceUntilIdle()

        val assistant = state.value.messages.single { it.role == "ASSISTANT" }
        assertEquals("查到一家距离很近的咖啡店。", assistant.content)
        // 工具前流出的过渡文本不清屏,降级为"意图段"保留在正文上方
        assertEquals("我先帮你查一下。", assistant.intentText)
        assertFalse(assistant.content.contains("我先帮你查一下"))
    }

    @Test
    fun sendMessage_complete_reconcilesPersistedAndStreamingCopiesById() = runTest {
        val persistedId = "persisted-assistant"
        val finalReply = "这是最终回答。"
        fakeRuntime.rawResponse = finalReply
        fakeRuntime.streamingDeltas = listOf("这是最终")
        fakeRuntime.persistedMessageId = persistedId
        fakeRuntime.beforeComplete = {
            state.update { current ->
                current.copy(
                    messages = current.messages + ChatMessage(
                        id = persistedId,
                        role = "ASSISTANT",
                        content = finalReply,
                    )
                )
            }
        }

        sendMessageUseCase("hello", null, readyConfig(), this, update)
        advanceUntilIdle()

        val assistants = state.value.messages.filter { it.role == "ASSISTANT" }
        assertEquals(1, assistants.size)
        // 保持列表里的 UI id 作 LazyColumn key(完成瞬间不重淡入);DB 行 id 记录到 persistedId
        assertEquals(persistedId, assistants.single().persistedId)
        assertEquals(finalReply, assistants.single().content)
        assertFalse(assistants.single().isStreaming)
    }

    @Test
    fun sendMessage_leadingFlushOnFirstDelta() = runTest {
        // P0: leading flush 路径——首字符到达后不依赖 90ms trailing 计时器
        // 立即 flush 到 state。不调用 advanceTimeBy 也能观察到首字符。
        fakeRuntime.rawResponse = "a"
        fakeRuntime.streamingDeltas = listOf("a")
        fakeRuntime.completeDelayMs = 200L

        sendMessageUseCase("hello", null, readyConfig(), this, update)
        // 注意：故意不 advanceTimeBy —— leading flush 路径应让首个 delta 立即可见

        val assistant = state.value.messages.lastOrNull { it.role == "ASSISTANT" }
        assertEquals("a", assistant?.content)
    }

    @Test
    fun sendMessage_smallDeltasPaceOutViaTicker() = runTest {
        // 首包立即上屏,后续 delta 进队列由 33ms ticker 按节奏出字。
        // 直接调用 suspend useCase 会被 runTest 推进到完成,须用 launch 挂在半途观察。
        fakeRuntime.rawResponse = "abc"
        fakeRuntime.streamingDeltas = listOf("a", "b", "c")
        fakeRuntime.completeDelayMs = 200L

        val sendJob = launch { sendMessageUseCase("hello", null, readyConfig(), this, update) }
        runCurrent()
        val assistant = { state.value.messages.lastOrNull { it.role == "ASSISTANT" } }
        assertEquals("a", assistant()?.content)

        advanceTimeBy(33L)
        runCurrent()
        assertEquals("abc", assistant()?.content)
        advanceUntilIdle()
        sendJob.join()
        assertEquals("abc", assistant()?.content)
    }

    @Test
    fun sendMessage_bigChunkDrainsProgressivelyNotAllAtOnce() = runTest {
        // 网络一次倒出的大 chunk 不整段上屏,由 ticker 分批摊平
        val longText = "字".repeat(100)
        fakeRuntime.rawResponse = longText
        fakeRuntime.streamingDeltas = listOf(longText)
        fakeRuntime.completeDelayMs = 1_000L

        val sendJob = launch { sendMessageUseCase("hello", null, readyConfig(), this, update) }
        runCurrent()
        val contentLength = {
            state.value.messages.lastOrNull { it.role == "ASSISTANT" }?.content?.length ?: 0
        }
        // 首包只上屏前 16 字
        assertEquals(16, contentLength())
        // backlog 84 ≥ 48 → 每 tick 12 字
        advanceTimeBy(33L)
        runCurrent()
        assertEquals(28, contentLength())
        advanceTimeBy(33L)
        runCurrent()
        assertEquals(40, contentLength())
        // 完成时排空队列,内容与最终回复一致
        advanceUntilIdle()
        sendJob.join()
        assertEquals(100, contentLength())
    }

    @Test
    fun sendMessage_compoundToolCalls_accumulateOrderedSteps() = runTest {
        // 复合工具调用按序累积成时间线;ToolStarted(无 callId) 与
        // ToolCallUpdated(有 callId) 两种事件对同一次调用只占一步。
        fakeRuntime.rawResponse = "都办好了。"
        fakeRuntime.scriptedEvents = listOf(
            AgentEvent.Streaming("我先看看。"),
            AgentEvent.ToolStarted("search_memory"),
            AgentEvent.ToolCallUpdated(
                AgentToolCall("search_memory", ToolCallStatus.STARTED, callId = "c1")
            ),
            AgentEvent.ToolCallUpdated(
                AgentToolCall("search_memory", ToolCallStatus.SUCCEEDED, callId = "c1")
            ),
            AgentEvent.StreamingReset,
            AgentEvent.ToolStarted("maps_around_search"),
            AgentEvent.ToolCallUpdated(
                AgentToolCall("maps_around_search", ToolCallStatus.STARTED, callId = "c2")
            ),
            AgentEvent.ToolCallUpdated(
                AgentToolCall(
                    "maps_around_search",
                    ToolCallStatus.SUCCEEDED,
                    callId = "c2",
                    resultJson = """{"status":"ok","count":1}""",
                )
            ),
            AgentEvent.Streaming("都办好了。"),
        )

        sendMessageUseCase("帮我查", null, readyConfig(), this, update)
        advanceUntilIdle()

        val assistant = state.value.messages.single { it.role == "ASSISTANT" }
        assertEquals(2, assistant.toolSteps.size)
        assertEquals(listOf("c1", "c2"), assistant.toolSteps.map { it.callId })
        assertTrue(assistant.toolSteps.all { it.status == ToolCallStatus.SUCCEEDED })
        assertEquals(listOf("c1", "c2"), assistant.toolCallIds)
        assertEquals("我先看看。", assistant.intentText)
        assertEquals("都办好了。", assistant.content)
    }

    @Test
    fun sendMessage_whenCancelled_preservesPartialReplyWithoutError() = runTest {
        fakeRuntime.emitStreaming = true
        fakeRuntime.completeDelayMs = 60_000L

        val sendJob = launch {
            sendMessageUseCase("hello", null, readyConfig(), this, update)
        }
        runCurrent()
        sendJob.cancelAndJoin()

        val assistant = state.value.messages.last { it.role == "ASSISTANT" }
        assertEquals("hello", assistant.content)
        assertEquals(ChatMessageCompletionState.STOPPED, assistant.completionState)
        assertFalse(assistant.isStreaming)
        assertFalse(state.value.isLoading)
        assertNull(state.value.error)
    }

}
