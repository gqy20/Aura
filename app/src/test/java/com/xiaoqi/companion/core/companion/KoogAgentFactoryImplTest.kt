package com.xiaoqi.companion.core.companion

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import com.xiaoqi.companion.core.llm.KoogPromptExecutorFactory
import com.xiaoqi.companion.core.prompt.BuiltPrompt
import com.xiaoqi.companion.core.tools.AgentToolRegistry
import com.xiaoqi.companion.core.tools.SaveMemoryTool
import com.xiaoqi.companion.core.tools.ToolCallRecorder
import com.xiaoqi.companion.data.db.converter.LlmProvider
import com.xiaoqi.companion.data.db.dao.MemoryDao
import com.xiaoqi.companion.data.db.dao.ToolCallDao
import com.xiaoqi.companion.data.db.entity.MemoryEntity
import com.xiaoqi.companion.data.repository.LlmConfig
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KoogAgentFactoryImplTest {

    private val memoryDao: MemoryDao = mockk(relaxed = true)
    private val toolCallDao: ToolCallDao = mockk(relaxed = true)
    private val saveMemoryTool = SaveMemoryTool(
        memoryDao = memoryDao,
        recorder = ToolCallRecorder(toolCallDao),
        sessionIdProvider = { "default" },
    )

    @Test
    fun run_usesKoogAgentToolLoopWithRegisteredTools() = runTest {
        val executor = ToolCallingPromptExecutor()
        val factory = KoogAgentFactoryImpl(
            executorFactory = object : KoogPromptExecutorFactory {
                override fun create(config: LlmConfig): PromptExecutor = executor
            },
            toolRegistry = object : AgentToolRegistry {
                override fun create(): ToolRegistry =
                    ToolRegistry.builder()
                        .tool(saveMemoryTool)
                        .build()
            },
        )

        val response = factory.create(testConfig).run(
            BuiltPrompt(
                systemPrompt = "You are a companion. Use tools when useful.",
                userMessage = "Please remember that I like jasmine tea.",
            )
        )

        assertEquals("remembered", response)
        assertTrue(executor.toolNamesPerCall.first().contains("save_memory"))
        assertEquals(2, executor.toolNamesPerCall.size)
        coVerify {
            memoryDao.insert(match<MemoryEntity> {
                it.content == "User likes jasmine tea" &&
                    it.source == "tool:save_memory"
            })
        }
    }

    @Test
    fun runEvents_emitsKoogToolLifecycleEvents() = runTest {
        val executor = ToolCallingPromptExecutor()
        val factory = KoogAgentFactoryImpl(
            executorFactory = object : KoogPromptExecutorFactory {
                override fun create(config: LlmConfig): PromptExecutor = executor
            },
            toolRegistry = object : AgentToolRegistry {
                override fun create(): ToolRegistry =
                    ToolRegistry.builder()
                        .tool(saveMemoryTool)
                        .build()
            },
        )

        val events = factory.create(testConfig).runEvents(
            BuiltPrompt(
                systemPrompt = "You are a companion. Use tools when useful.",
                userMessage = "Please remember that I like jasmine tea.",
            )
        ).toList()

        assertTrue(events.contains(KoogAgentEvent.ToolStarted("save_memory")))
        assertTrue(events.contains(KoogAgentEvent.ToolFinished("save_memory")))
        assertTrue(events.contains(KoogAgentEvent.TextDelta("remembered")))
    }

    private class ToolCallingPromptExecutor : PromptExecutor() {
        val toolNamesPerCall = mutableListOf<List<String>>()

        override suspend fun execute(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>,
        ): List<Message.Response> {
            toolNamesPerCall += tools.map { it.name }
            return if (toolNamesPerCall.size == 1) {
                listOf(
                    Message.Tool.Call(
                        id = "call-1",
                        tool = "save_memory",
                        content = """{"content":"User likes jasmine tea","type":"FACT","importance":0.9}""",
                        metaInfo = ResponseMetaInfo(Clock.System.now()),
                    )
                )
            } else {
                listOf(Message.Assistant("remembered", ResponseMetaInfo(Clock.System.now())))
            }
        }

        override fun executeStreaming(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>,
        ): Flow<StreamFrame> = emptyFlow()

        override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
            ModerationResult(isHarmful = false, categories = emptyMap())

        override fun close() = Unit
    }

    private companion object {
        val testConfig = LlmConfig(
            provider = LlmProvider.GLM,
            baseUrl = "https://example.test",
            apiKey = "test-key",
            modelName = "test-model",
        )
    }
}
