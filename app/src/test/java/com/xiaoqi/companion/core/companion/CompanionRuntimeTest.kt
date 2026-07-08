package com.xiaoqi.companion.core.companion

import app.cash.turbine.test
import com.xiaoqi.companion.core.companion.model.AgentEvent
import com.xiaoqi.companion.core.companion.model.AgentError
import com.xiaoqi.companion.core.companion.model.AgentToolCall
import com.xiaoqi.companion.core.companion.model.ToolCallStatus
import com.xiaoqi.companion.core.companion.model.UserInput
import com.xiaoqi.companion.core.context.CurrentLocationProvider
import com.xiaoqi.companion.core.prompt.BuiltPrompt
import com.xiaoqi.companion.core.prompt.PromptBuilder
import com.xiaoqi.companion.data.datastore.AppPreferences
import com.xiaoqi.companion.data.db.converter.MessageRole
import com.xiaoqi.companion.data.db.converter.MemoryType
import com.xiaoqi.companion.data.db.entity.MemoryEntity
import com.xiaoqi.companion.data.db.entity.MessageEntity
import com.xiaoqi.companion.data.repository.ConfigRepository
import com.xiaoqi.companion.data.repository.ConversationRepository
import com.xiaoqi.companion.data.repository.MemorySources
import com.xiaoqi.companion.data.repository.MemoryRepository
import com.xiaoqi.companion.data.repository.PromptMemoryContext
import com.xiaoqi.companion.data.repository.MessageRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionRuntimeTest {

    private val configRepo: ConfigRepository = mockk {
        every { apiKey } returns flowOf("test-api-key")
        every { llmProvider } returns flowOf(com.xiaoqi.companion.data.db.converter.LlmProvider.GLM)
        every { modelName } returns flowOf("glm-5v-turbo")
        every { getCurrentLlmConfig() } returns flowOf(
            com.xiaoqi.companion.data.repository.LlmConfig(
                provider = com.xiaoqi.companion.data.db.converter.LlmProvider.GLM,
                baseUrl = "https://open.bigmodel.cn/api/paas/v1",
                apiKey = "test-api-key",
                modelName = "glm-5v-turbo",
            )
        )
    }

    private val promptBuilder: PromptBuilder = mockk {
        every { build(any(), any(), any(), any(), any(), any(), any(), any()) } returns BuiltPrompt(
            systemPrompt = "system", userMessage = "hello",
        )
    }

    private val messageRepo: MessageRepository = mockk(relaxed = true) {
        coEvery { getRecentMessages(any(), any()) } returns emptyList()
    }
    private val memoryRepository: MemoryRepository = mockk(relaxed = true) {
        coEvery { selectPromptContext(any()) } returns PromptMemoryContext(
            memorySnippets = emptyList(),
            memoryIds = emptyList(),
            summarySnippets = emptyList(),
            summaryIds = emptyList(),
        )
    }
    private val emotionMachine: EmotionStateMachine = mockk(relaxed = true)
    private val relationshipModel: RelationshipModel = mockk(relaxed = true)
    private val locationProvider: CurrentLocationProvider = mockk {
        every { getLastKnownLocation() } returns null
    }
    private val appPreferences: AppPreferences = mockk {
        every { locationContextEnabled } returns flowOf(true)
        every { currentSessionId } returns flowOf("default")
        every { localToolsEnabled } returns flowOf(false)
        every { systemToolsEnabled } returns flowOf(true)
        every { mcpEnabled } returns flowOf(true)
    }

    private val conversationRepository: ConversationRepository = mockk(relaxed = true)

    private class FakeKoogAgentFactory : KoogAgentFactory {
        var lastConfig: com.xiaoqi.companion.data.repository.LlmConfig? = null
        var responseText = "你好呀！"
        var shouldFail = false
        var emitToolEvents = false
        var emitTextEvents = true
        var runCallCount = 0
        var runEventsCallCount = 0

        override fun create(config: com.xiaoqi.companion.data.repository.LlmConfig, sessionId: String, allowLocalTools: Boolean): KoogAgentWrapper {
            lastConfig = config
            return object : KoogAgentWrapper {
                override suspend fun run(prompt: BuiltPrompt): String {
                    runCallCount++
                    if (shouldFail) throw RuntimeException("API error")
                    return responseText
                }

                override suspend fun <T> runStructured(
                    prompt: BuiltPrompt,
                    serializer: KSerializer<T>,
                    examples: List<T>,
                ): T = error("structured output is not used by CompanionRuntimeTest")

                override fun runStreaming(prompt: BuiltPrompt) = flow {
                    if (shouldFail) throw RuntimeException("API error")
                    emit(responseText)
                }

                override fun runEvents(prompt: BuiltPrompt) = flow {
                    runEventsCallCount++
                    if (shouldFail) throw RuntimeException("API error")
                    if (emitToolEvents) {
                        emit(KoogAgentEvent.ToolCallUpdated(AgentToolCall("update_state", ToolCallStatus.STARTED)))
                        emit(KoogAgentEvent.ToolCallUpdated(AgentToolCall("update_state", ToolCallStatus.SUCCEEDED)))
                    }
                    if (emitTextEvents) {
                        emit(KoogAgentEvent.TextDelta(responseText))
                    }
                }
            }
        }
    }

    private fun makeRuntime(factory: FakeKoogAgentFactory) = CompanionRuntime(
        configRepository = configRepo,
        koogAgentFactory = factory,
        promptBuilder = promptBuilder,
        messageRepository = messageRepo,
        memoryRepository = memoryRepository,
        conversationContextBuilder = ConversationContextBuilder(messageRepo),
        emotionMachine = emotionMachine,
        relationshipModel = relationshipModel,
        locationProvider = locationProvider,
        appPreferences = appPreferences,
        conversationRepository = conversationRepository,
        agentTurnPolicy = AgentTurnPolicy(),
    )

    @Test
    fun send_textInput_emitsCompleteEvent() = runTest {
        val factory = FakeKoogAgentFactory()
        makeRuntime(factory).send(UserInput.Text("hello")).test {
            assertTrue(awaitItem() is AgentEvent.Streaming)
            val event = awaitItem()
            assertTrue(event is AgentEvent.Complete)
            assertEquals("你好呀！", (event as AgentEvent.Complete).textReply)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun send_passesCorrectLlmConfigToFactory() = runTest {
        val factory = FakeKoogAgentFactory().apply { emitToolEvents = true }
        makeRuntime(factory).send(UserInput.Text("hi")).test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("glm-5v-turbo", factory.lastConfig?.modelName)
        assertEquals("test-api-key", factory.lastConfig?.apiKey)
    }

    @Test
    fun send_callsPromptBuilderWithInput() = runTest {
        val factory = FakeKoogAgentFactory()
        makeRuntime(factory).send(UserInput.Text("你好世界")).test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { promptBuilder.build(match<UserInput> { it.content == "你好世界" }, any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun send_storesUserMessage() = runTest {
        val factory = FakeKoogAgentFactory()
        makeRuntime(factory).send(UserInput.Text("用户消息")).test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { messageRepo.sendMessage("default", "用户消息") }
    }

    @Test
    fun send_savesRawResponseAsAssistantMessage() = runTest {
        val factory = FakeKoogAgentFactory()
        coEvery { messageRepo.sendMessage(any(), any(), any()) } returns "user-message"
        coEvery { messageRepo.saveAssistantMessage(any(), any()) } returns "assistant-message"

        makeRuntime(factory).send(UserInput.Text("hello")).test {
            assertTrue(awaitItem() is AgentEvent.Streaming)
            assertTrue(awaitItem() is AgentEvent.Complete)
            cancelAndIgnoreRemainingEvents()
        }

        coVerify { messageRepo.saveAssistantMessage("default", factory.responseText) }
    }

    @Test
    fun send_apiError_emitsErrorEvent() = runTest {
        val factory = FakeKoogAgentFactory().apply { shouldFail = true }
        makeRuntime(factory).send(UserInput.Text("hi")).test {
            val event = awaitItem()
            assertTrue(event is AgentEvent.Error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun send_whenRunEventsHasNoText_emitsError() = runTest {
        val factory = FakeKoogAgentFactory().apply { emitTextEvents = false }

        makeRuntime(factory).send(UserInput.Text("hi")).test {
            val event = awaitItem()
            assertTrue(event is AgentEvent.Error)
            assertTrue((event as AgentEvent.Error).error is AgentError.ParseError)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, factory.runEventsCallCount)
        assertEquals(0, factory.runCallCount)
    }

    @Test
    fun send_visionInput_passesToPromptBuilder() = runTest {
        val factory = FakeKoogAgentFactory()
        makeRuntime(factory).send(UserInput.Vision("看这个", "base64img", "image/jpeg")).test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { promptBuilder.build(match<UserInput> { it is UserInput.Vision }, any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun send_visionInput_injectsRecentImageMemoriesAsReadOnlyContext() = runTest {
        coEvery { memoryRepository.getRecentImages(any()) } returns listOf(
            MemoryEntity(
                id = "vision-memory-1",
                type = MemoryType.FACT,
                content = "[图片] 书桌上有一杯茶",
                source = "reflection:vision",
                importance = 0.6f,
                confidence = 0.8f,
                timestamp = 1L,
                updatedAt = 1L,
                lastAccessed = 1L,
                imageBase64 = "base64",
            )
        )
        val factory = FakeKoogAgentFactory()

        makeRuntime(factory).send(UserInput.Vision("看这个", "base64img", "image/jpeg")).test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify {
            promptBuilder.build(
                match<UserInput> { it is UserInput.Vision },
                any(),
                any(),
                any(),
                match { it.contains("Image memory: [图片] 书桌上有一杯茶") },
                any(),
                any(),
            )
        }
    }

    @Test
    fun send_visionInput_storesImageWithUserMessage() = runTest {
        val factory = FakeKoogAgentFactory()
        makeRuntime(factory).send(
            UserInput.Vision(
                text = "Describe this",
                imageBase64 = "base64img",
                mediaType = "image/jpeg",
                displayText = "Shared a picture",
            )
        ).test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify {
            messageRepo.sendMessage(
                sessionId = "default",
                content = "Shared a picture",
                imageBase64 = "base64img",
            )
        }
    }

    @Test
    fun send_agentToolEvents_emitsObservableToolEvents() = runTest {
        val factory = FakeKoogAgentFactory().apply { emitToolEvents = true }
        makeRuntime(factory).send(UserInput.Text("remember this")).test {
            assertEquals(
                AgentEvent.ToolCallUpdated(AgentToolCall("update_state", ToolCallStatus.STARTED)),
                awaitItem(),
            )
            assertEquals(
                AgentEvent.ToolCallUpdated(AgentToolCall("update_state", ToolCallStatus.SUCCEEDED)),
                awaitItem(),
            )
            assertTrue(awaitItem() is AgentEvent.Streaming)
            assertTrue(awaitItem() is AgentEvent.Complete)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun send_whenStateToolDidNotRun_savesLowConfidenceFallbackMemoryForExplicitPreference() = runTest {
        val factory = FakeKoogAgentFactory()
        coEvery { messageRepo.sendMessage(any(), any(), any()) } returns "user-message"
        coEvery { messageRepo.saveAssistantMessage(any(), any()) } returns "assistant-message"

        makeRuntime(factory).send(UserInput.Text("我喜欢茉莉花茶")).test {
            assertTrue(awaitItem() is AgentEvent.Streaming)
            assertTrue(awaitItem() is AgentEvent.Complete)
            cancelAndIgnoreRemainingEvents()
        }

        coVerify {
            memoryRepository.saveMemory(match {
                it.content == "我喜欢茉莉花茶" &&
                    it.source == MemorySources.POST_TURN_FALLBACK &&
                    it.sourceMessageIds == listOf("user-message") &&
                    it.confidence == 0.45f
            })
        }
    }

    @Test
    fun send_injectsPromptMemoriesAndMarksThemAccessed() = runTest {
        coEvery { memoryRepository.selectPromptContext(any()) } returns PromptMemoryContext(
            memorySnippets = listOf("User likes jasmine tea"),
            memoryIds = listOf("memory-1"),
            summarySnippets = listOf("Tea preferences: User enjoys jasmine tea."),
            summaryIds = listOf("summary-1"),
        )
        val factory = FakeKoogAgentFactory()

        makeRuntime(factory).send(UserInput.Text("what do I like?")).test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify {
            promptBuilder.build(
                any(),
                any(),
                any(),
                any(),
                match { it == listOf("User likes jasmine tea") },
                match { it == listOf("Tea preferences: User enjoys jasmine tea.") },
                any(),
            )
            memoryRepository.selectPromptContext("what do I like?")
        }
    }

    @Test
    fun send_injectsRecentConversationWindowSeparatelyFromMemories() = runTest {
        coEvery { messageRepo.getRecentMessages("default", any()) } returns listOf(
            MessageEntity(
                id = "m2",
                sessionId = "default",
                role = MessageRole.ASSISTANT,
                content = "We discussed adding a short-term context window.",
                timestamp = 2_000L,
            ),
            MessageEntity(
                id = "m1",
                sessionId = "default",
                role = MessageRole.USER,
                content = "How is memory designed?",
                timestamp = 1_000L,
            ),
        )
        val factory = FakeKoogAgentFactory()

        makeRuntime(factory).send(UserInput.Text("continue")).test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify {
            promptBuilder.build(
                any(),
                any(),
                any(),
                match {
                    it == listOf(
                        "User: How is memory designed?",
                        "Aura: We discussed adding a short-term context window.",
                    )
                },
                any(),
                any(),
                any(),
            )
        }
    }
}
