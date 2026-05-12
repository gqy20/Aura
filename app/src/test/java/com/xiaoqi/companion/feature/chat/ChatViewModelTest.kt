package com.xiaoqi.companion.feature.chat

import app.cash.turbine.test
import com.xiaoqi.companion.core.companion.CompanionRuntime
import com.xiaoqi.companion.core.companion.OutputParser
import com.xiaoqi.companion.core.companion.model.AgentError
import com.xiaoqi.companion.core.companion.model.AgentEvent
import com.xiaoqi.companion.core.companion.model.AgentToolCall
import com.xiaoqi.companion.core.companion.model.ToolCallStatus
import com.xiaoqi.companion.core.companion.model.UserInput
import com.xiaoqi.companion.core.tools.ToolDisplayRegistry
import com.xiaoqi.companion.data.db.dao.MemoryDao
import com.xiaoqi.companion.data.repository.ConfigRepository
import com.xiaoqi.companion.data.repository.LlmConfig
import com.xiaoqi.companion.data.repository.MessageRepository
import com.xiaoqi.companion.data.repository.ToolCallRepository
import com.xiaoqi.companion.data.repository.ToolCallSnapshot
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
    private val testDispatcher = UnconfinedTestDispatcher()

    private val configRepo: ConfigRepository = mockk {
        every { getCurrentLlmConfig() } returns flowOf(
            LlmConfig(
                provider = com.xiaoqi.companion.data.db.converter.LlmProvider.GLM,
                baseUrl = "https://open.bigmodel.cn/api/paas/v1",
                apiKey = "test-key",
                modelName = "glm-5v-turbo",
            )
        )
    }

    private val messageRepo: MessageRepository = mockk(relaxed = true)

    private class FakeToolCallRepository : ToolCallRepository {
        val calls = MutableStateFlow<List<ToolCallSnapshot>>(emptyList())

        override fun observeBySession(sessionId: String) = calls
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
        memoryDao = mockk<MemoryDao>(relaxed = true),
        emotionMachine = mockk(relaxed = true),
        relationshipModel = mockk(relaxed = true),
    ) {
        var rawResponse = "[mood:happy][intensity:0.7] 你好呀！"
        var shouldFail = false
        var emitToolEvents = false
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
                val parsed = OutputParser().parse(rawResponse)
                emit(AgentEvent.Complete(parsed))
            }
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRuntime = FakeCompanionRuntime(configRepo, messageRepo)
        toolCallRepository = FakeToolCallRepository()
        viewModel = ChatViewModel(fakeRuntime, ToolDisplayRegistry(), toolCallRepository)
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
    fun sendMessage_onError_setsErrorAndStopsLoading() = runTest {
        fakeRuntime.shouldFail = true
        viewModel.sendMessage("hi")

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
        assertEquals("Memory saved", assistant.toolStatus)
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
        assertEquals("Memory saved", calls[0].label)
        assertEquals("Done", calls[0].status)
        assertEquals(100L, calls[0].durationMs)
        assertEquals("Searching memory", calls[1].label)
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
}
