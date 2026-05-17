package com.xiaoqi.companion.core.companion

import app.cash.turbine.test
import com.xiaoqi.companion.core.companion.model.AgentEvent
import com.xiaoqi.companion.core.companion.model.AgentError
import com.xiaoqi.companion.core.companion.model.AgentToolCall
import com.xiaoqi.companion.core.companion.model.EmotionSignal
import com.xiaoqi.companion.core.companion.model.InteractionSignal
import com.xiaoqi.companion.core.companion.model.ParsedOutput
import com.xiaoqi.companion.core.companion.model.ToolCallStatus
import com.xiaoqi.companion.core.companion.model.UserInput
import com.xiaoqi.companion.core.prompt.BuiltPrompt
import com.xiaoqi.companion.core.prompt.PromptBuilder
import com.xiaoqi.companion.data.db.converter.MemoryType
import com.xiaoqi.companion.data.db.dao.MemoryDao
import com.xiaoqi.companion.data.db.entity.MemoryEntity
import com.xiaoqi.companion.data.repository.ConfigRepository
import com.xiaoqi.companion.data.repository.MessageRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
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
        every { build(any(), any(), any(), any()) } returns BuiltPrompt(
            systemPrompt = "system", userMessage = "hello",
        )
    }

    private val outputParser: OutputParser = mockk {
        every { parse(any<String>()) } returns ParsedOutput(textReply = "你好！")
    }

    private val messageRepo: MessageRepository = mockk(relaxed = true)
    private val memoryDao: MemoryDao = mockk(relaxed = true) {
        coEvery { getPromptMemories(any()) } returns emptyList()
    }
    private val emotionMachine: EmotionStateMachine = mockk(relaxed = true)
    private val relationshipModel: RelationshipModel = mockk(relaxed = true)

    private class FakeKoogAgentFactory : KoogAgentFactory {
        var lastConfig: com.xiaoqi.companion.data.repository.LlmConfig? = null
        var responseText = "[mood:happy][intensity:0.7] 你好呀！"
        var shouldFail = false
        var emitToolEvents = false
        var emitTextEvents = true
        var runCallCount = 0
        var runEventsCallCount = 0

        override fun create(config: com.xiaoqi.companion.data.repository.LlmConfig): KoogAgentWrapper {
            lastConfig = config
            return object : KoogAgentWrapper {
                override suspend fun run(prompt: BuiltPrompt): String {
                    runCallCount++
                    if (shouldFail) throw RuntimeException("API error")
                    return responseText
                }

                override fun runStreaming(prompt: BuiltPrompt) = flow {
                    if (shouldFail) throw RuntimeException("API error")
                    emit(responseText)
                }

                override fun runEvents(prompt: BuiltPrompt) = flow {
                    runEventsCallCount++
                    if (shouldFail) throw RuntimeException("API error")
                    if (emitToolEvents) {
                        emit(KoogAgentEvent.ToolCallUpdated(AgentToolCall("save_memory", ToolCallStatus.STARTED)))
                        emit(KoogAgentEvent.ToolCallUpdated(AgentToolCall("save_memory", ToolCallStatus.SUCCEEDED)))
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
        outputParser = outputParser,
        messageRepository = messageRepo,
        memoryDao = memoryDao,
        emotionMachine = emotionMachine,
        relationshipModel = relationshipModel,
    )

    @Test
    fun send_textInput_emitsCompleteEvent() = runTest {
        val factory = FakeKoogAgentFactory()
        makeRuntime(factory).send(UserInput.Text("hello")).test {
            assertTrue(awaitItem() is AgentEvent.Streaming)
            val event = awaitItem()
            assertTrue(event is AgentEvent.Complete)
            assertEquals("你好！", (event as AgentEvent.Complete).parsed.textReply)
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
        coVerify { promptBuilder.build(match<UserInput> { it.content == "你好世界" }, any(), any(), any()) }
    }

    @Test
    fun send_feedsEmotionMachine() = runTest {
        val factory = FakeKoogAgentFactory()
        every { outputParser.parse(any()) } returns ParsedOutput(
            textReply = "回复",
            emotionSignal = EmotionSignal(mood = "excited", intensity = 0.9f),
        )
        makeRuntime(factory).send(UserInput.Text("test")).test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { emotionMachine.feed(EmotionSignal(mood = "excited", intensity = 0.9f)) }
    }

    @Test
    fun send_updatesRelationship() = runTest {
        val factory = FakeKoogAgentFactory()
        every { outputParser.parse(any()) } returns ParsedOutput(
            textReply = "回复",
            interactionSignal = InteractionSignal(affinityDelta = 0.05f),
        )
        makeRuntime(factory).send(UserInput.Text("test")).test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { relationshipModel.update(InteractionSignal(affinityDelta = 0.05f)) }
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
    fun send_apiError_emitsErrorEvent() = runTest {
        val factory = FakeKoogAgentFactory().apply { shouldFail = true }
        makeRuntime(factory).send(UserInput.Text("hi")).test {
            val event = awaitItem()
            assertTrue(event is AgentEvent.Error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun send_whenRunEventsHasNoText_doesNotFallbackToSecondRun() = runTest {
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
        coVerify { promptBuilder.build(match<UserInput> { it is UserInput.Vision }, any(), any(), any()) }
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
                AgentEvent.ToolCallUpdated(AgentToolCall("save_memory", ToolCallStatus.STARTED)),
                awaitItem(),
            )
            assertEquals(
                AgentEvent.ToolCallUpdated(AgentToolCall("save_memory", ToolCallStatus.SUCCEEDED)),
                awaitItem(),
            )
            assertTrue(awaitItem() is AgentEvent.Streaming)
            assertTrue(awaitItem() is AgentEvent.Complete)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun send_injectsPromptMemoriesAndMarksThemAccessed() = runTest {
        coEvery { memoryDao.getPromptMemories(any()) } returns listOf(
            MemoryEntity(
                id = "memory-1",
                type = MemoryType.FACT,
                content = "User likes jasmine tea",
                timestamp = 1_000L,
                lastAccessed = 1_000L,
            )
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
                match { it == listOf("User likes jasmine tea") },
            )
            memoryDao.updateLastAccessed("memory-1", any())
        }
    }
}
