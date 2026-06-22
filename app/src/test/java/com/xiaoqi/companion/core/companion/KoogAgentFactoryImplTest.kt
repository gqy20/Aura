package com.xiaoqi.companion.core.companion

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.toStreamFrames
import com.xiaoqi.companion.core.companion.model.AgentToolCall
import com.xiaoqi.companion.core.companion.model.ToolCallStatus
import com.xiaoqi.companion.core.llm.KoogPromptExecutorFactory
import com.xiaoqi.companion.core.local.LocalQwenEngine
import com.xiaoqi.companion.core.local.LocalQwenRequest
import com.xiaoqi.companion.core.prompt.BuiltPrompt
import com.xiaoqi.companion.core.tools.AgentToolRegistry
import com.xiaoqi.companion.core.tools.ToolCallRecorder
import com.xiaoqi.companion.core.tools.ToolScope
import com.xiaoqi.companion.data.db.converter.LlmProvider
import com.xiaoqi.companion.data.db.dao.ToolCallDao
import com.xiaoqi.companion.data.db.entity.ToolCallEntity
import com.xiaoqi.companion.data.repository.LlmConfig
import com.xiaoqi.companion.testing.FakeLocalQwenEngine
import ai.koog.serialization.typeToken
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KoogAgentFactoryImplTest {

    private val toolCallDao: ToolCallDao = mockk(relaxed = true)
    private val noteTool = TestNoteTool()
    private val toolCallRecorder = ToolCallRecorder(toolCallDao)

    @Test
    fun run_usesKoogAgentToolLoopWithRegisteredTools() = runTest {
        val executor = ToolCallingPromptExecutor()
        val factory = KoogAgentFactoryImpl(
            executorFactory = object : KoogPromptExecutorFactory {
                override fun create(config: LlmConfig): PromptExecutor = executor
            },
            localQwenEngine = ErrorLocalQwenEngine,
            toolCallRecorder = toolCallRecorder,
            toolRegistry = object : AgentToolRegistry {
                override fun create(scope: ToolScope): ToolRegistry =
                    ToolRegistry.builder()
                        .tool(noteTool)
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
        assertTrue(executor.toolNamesPerCall.first().contains("test_note"))
        assertEquals(2, executor.toolNamesPerCall.size)
        coVerify {
            toolCallDao.insert(match<ToolCallEntity> {
                it.id == "call-1" &&
                    it.toolName == "test_note" &&
                    it.status == "RUNNING"
            })
            toolCallDao.updateResult(
                id = "call-1",
                status = "SUCCESS",
                resultJson = match { it.contains("noted") },
                errorMessage = null,
                completedAt = any(),
            )
        }
    }

    @Test
    fun runEvents_emitsKoogToolLifecycleEvents() = runTest {
        val executor = ToolCallingPromptExecutor()
        val factory = KoogAgentFactoryImpl(
            executorFactory = object : KoogPromptExecutorFactory {
                override fun create(config: LlmConfig): PromptExecutor = executor
            },
            localQwenEngine = ErrorLocalQwenEngine,
            toolCallRecorder = toolCallRecorder,
            toolRegistry = object : AgentToolRegistry {
                override fun create(scope: ToolScope): ToolRegistry =
                    ToolRegistry.builder()
                        .tool(noteTool)
                        .build()
            },
        )

        val events = factory.create(testConfig).runEvents(
            BuiltPrompt(
                systemPrompt = "You are a companion. Use tools when useful.",
                userMessage = "Please remember that I like jasmine tea.",
            )
        ).toList()

        assertTrue(events.any {
            val call = (it as? KoogAgentEvent.ToolCallUpdated)?.call
            call?.name == "test_note" &&
                call.status == ToolCallStatus.STARTED &&
                call.callId == "call-1" &&
                call.argumentsJson?.contains("jasmine tea") == true
        })
        assertTrue(events.any {
            val call = (it as? KoogAgentEvent.ToolCallUpdated)?.call
            call?.name == "test_note" &&
                call.status == ToolCallStatus.SUCCEEDED &&
                call.callId == "call-1" &&
                call.resultJson?.contains("noted") == true
        })
        assertTrue(events.contains(KoogAgentEvent.TextDelta("remembered")))
        coVerify {
            toolCallDao.insert(match<ToolCallEntity> {
                it.id == "call-1" &&
                    it.sessionId == "default" &&
                    it.toolName == "test_note" &&
                    it.argumentsJson.contains("jasmine tea") &&
                    it.status == "RUNNING"
            })
            toolCallDao.updateResult(
                id = "call-1",
                status = "SUCCESS",
                resultJson = match { it.contains("noted") },
                errorMessage = null,
                completedAt = any(),
            )
        }
    }

    @Test
    fun runEvents_whenStreamOnlyCompletes_emitsCompleteTextOnce() = runTest {
        val executor = CompleteOnlyPromptExecutor()
        val factory = KoogAgentFactoryImpl(
            executorFactory = object : KoogPromptExecutorFactory {
                override fun create(config: LlmConfig): PromptExecutor = executor
            },
            localQwenEngine = ErrorLocalQwenEngine,
            toolCallRecorder = toolCallRecorder,
            toolRegistry = object : AgentToolRegistry {
                override fun create(scope: ToolScope): ToolRegistry = ToolRegistry.EMPTY
            },
        )

        val events = factory.create(testConfig).runEvents(
            BuiltPrompt(systemPrompt = "system", userMessage = "hello")
        ).toList()

        assertEquals(listOf(KoogAgentEvent.TextDelta("complete only")), events)
    }

    @Test
    fun run_withVisionPromptDisablesTools() = runTest {
        val executor = VisionPromptExecutor()
        val factory = KoogAgentFactoryImpl(
            executorFactory = object : KoogPromptExecutorFactory {
                override fun create(config: LlmConfig): PromptExecutor = executor
            },
            localQwenEngine = ErrorLocalQwenEngine,
            toolCallRecorder = toolCallRecorder,
            toolRegistry = object : AgentToolRegistry {
                override fun create(scope: ToolScope): ToolRegistry =
                    ToolRegistry.builder()
                        .tool(noteTool)
                        .build()
            },
        )

        val response = factory.create(testConfig).run(
            BuiltPrompt(
                systemPrompt = "You are a companion.",
                userMessage = "看一下这张图",
                hasImage = true,
                imageBase64 = "base64-image",
                imageMediaType = "image/jpeg",
            )
        )

        assertEquals("这是一张图片。", response)
        assertEquals(listOf(emptyList<String>()), executor.toolNamesPerCall)
    }

    @Test
    fun run_whenPromptDisallowsTools_usesEmptyToolRegistry() = runTest {
        val executor = VisionPromptExecutor()
        val factory = KoogAgentFactoryImpl(
            executorFactory = object : KoogPromptExecutorFactory {
                override fun create(config: LlmConfig): PromptExecutor = executor
            },
            localQwenEngine = ErrorLocalQwenEngine,
            toolCallRecorder = toolCallRecorder,
            toolRegistry = object : AgentToolRegistry {
                override fun create(scope: ToolScope): ToolRegistry =
                    ToolRegistry.builder()
                        .tool(noteTool)
                        .build()
            },
        )

        val response = factory.create(testConfig).run(
            BuiltPrompt(
                systemPrompt = "You are a reflection module.",
                userMessage = "Return memory JSON.",
                allowTools = false,
            )
        )

        assertEquals("这是一张图片。", response)
        assertEquals(listOf(emptyList<String>()), executor.toolNamesPerCall)
    }

    @Test
    fun create_localQwenProvider_usesLocalEngine() = runTest {
        val localEngine = FakeLocalQwenEngine(listOf("local", " reply"))
        val factory = KoogAgentFactoryImpl(
            executorFactory = object : KoogPromptExecutorFactory {
                override fun create(config: LlmConfig): PromptExecutor = error("remote executor should not be used")
            },
            localQwenEngine = localEngine,
            toolCallRecorder = toolCallRecorder,
            toolRegistry = object : AgentToolRegistry {
                override fun create(scope: ToolScope): ToolRegistry = ToolRegistry.EMPTY
            },
        )

        val events = factory.create(testConfig.copy(provider = LlmProvider.LOCAL_QWEN)).runEvents(
            BuiltPrompt(
                systemPrompt = "system",
                userMessage = "hello",
            )
        ).toList()

        assertEquals(
            listOf(KoogAgentEvent.TextDelta("local"), KoogAgentEvent.TextDelta(" reply")),
            events,
        )
        assertEquals("system", localEngine.lastRequest?.systemPrompt)
        assertEquals("hello", localEngine.lastRequest?.userMessage)
        assertEquals(true, localEngine.lastRequest?.allowTools)
    }

    @Test
    fun create_localQwenProvider_withTools_defaultDisabled_skipsLocalToolLoop() = runTest {
        // 默认 allowLocalTools=false(与历史行为一致):即使 AgentToolRegistry 有工具,
        // ReactiveCompanion 仍拿 ToolRegistry.EMPTY,走纯文本路径,不进工具循环。
        // 用户需要在 Settings 里手动打开「本地工具调用」开关才会启用。
        val localEngine = SequencedLocalQwenEngine(
            listOf(
                listOf("plain reply without tool"),
            )
        )
        val factory = KoogAgentFactoryImpl(
            executorFactory = object : KoogPromptExecutorFactory {
                override fun create(config: LlmConfig): PromptExecutor = error("remote executor should not be used")
            },
            localQwenEngine = localEngine,
            toolCallRecorder = toolCallRecorder,
            toolRegistry = object : AgentToolRegistry {
                override fun create(scope: ToolScope): ToolRegistry =
                    ToolRegistry.builder()
                        .tool(noteTool)
                        .build()
            },
        )

        val events = factory.create(testConfig.copy(provider = LlmProvider.LOCAL_QWEN)).runEvents(
            BuiltPrompt(
                systemPrompt = "system",
                userMessage = "remember this",
                allowTools = true,
            )
        ).toList()

        // 没有工具调用事件,只有纯文本输出
        assertTrue(events.none {
            (it as? KoogAgentEvent.ToolCallUpdated)?.call != null
        })
        assertTrue(events.contains(KoogAgentEvent.TextDelta("plain reply without tool")))
        // 只调用了一次 engine(没有第二轮工具循环)
        assertEquals(1, localEngine.requests.size)
    }

    @Test
    fun create_localQwenProvider_allowLocalToolsTrue_injectsToolRegistry() = runTest {
        // 开关打开后:AgentToolRegistry.create() 返回的真实 registry 被注入到 ReactiveCompanion,
        // 软协议工兵循环起作用(模型输出 tool_calls JSON -> parseToolCalls -> execute)。
        val localEngine = SequencedLocalQwenEngine(
            listOf(
                // 第一轮:模型输出 tool_calls JSON
                listOf("{\"tool_calls\":[{\"name\":\"note\",\"arguments\":{\"text\":\"hello\"}}]}"),
                // 第二轮:工具执行完,模型输出纯文本收尾
                listOf("done"),
            )
        )
        val factory = KoogAgentFactoryImpl(
            executorFactory = object : KoogPromptExecutorFactory {
                override fun create(config: LlmConfig): PromptExecutor = error("remote executor should not be used")
            },
            localQwenEngine = localEngine,
            toolCallRecorder = toolCallRecorder,
            toolRegistry = object : AgentToolRegistry {
                override fun create(scope: ToolScope): ToolRegistry =
                    ToolRegistry.builder()
                        .tool(noteTool)
                        .build()
            },
        )

        val events = factory.create(
            config = testConfig.copy(provider = LlmProvider.LOCAL_QWEN),
            sessionId = "default",
            allowLocalTools = true,
        ).runEvents(
            BuiltPrompt(
                systemPrompt = "system",
                userMessage = "remember this",
                allowTools = true,
            )
        ).toList()

        // 应该看到 note 工具的 STARTED + SUCCEEDED 事件
        val toolEvents = events.filterIsInstance<KoogAgentEvent.ToolCallUpdated>()
        assertTrue("should have tool events", toolEvents.isNotEmpty())
        assertTrue("note tool should be called", toolEvents.any { it.call.name == "note" })
        // 应该调了两次 engine(第一轮 tool_calls + 第二轮总结)
        assertEquals(2, localEngine.requests.size)
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
                        tool = "test_note",
                        content = """{"content":"User likes jasmine tea"}""",
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
        ): Flow<StreamFrame> {
            toolNamesPerCall += tools.map { it.name }
            return if (toolNamesPerCall.size == 1) {
                listOf(
                    Message.Tool.Call(
                        id = "call-1",
                        tool = "test_note",
                        content = """{"content":"User likes jasmine tea"}""",
                        metaInfo = ResponseMetaInfo(Clock.System.now()),
                    )
                ).toStreamFrames().asFlow()
            } else {
                listOf(Message.Assistant("remembered", ResponseMetaInfo(Clock.System.now())))
                    .toStreamFrames()
                    .asFlow()
            }
        }

        override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
            ModerationResult(isHarmful = false, categories = emptyMap())

        override fun close() = Unit
    }

    private class SequencedLocalQwenEngine(
        private val responses: List<List<String>>,
    ) : LocalQwenEngine {
        val requests = mutableListOf<LocalQwenRequest>()

        override fun stream(request: LocalQwenRequest): Flow<String> = flow {
            requests += request
            responses[requests.lastIndex].forEach { emit(it) }
        }
    }

    private object ErrorLocalQwenEngine : LocalQwenEngine {
        override fun stream(request: LocalQwenRequest): Flow<String> = flow {
            error("Local engine should not be used for remote providers")
        }
    }

    private class CompleteOnlyPromptExecutor : PromptExecutor() {
        override suspend fun execute(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>,
        ): List<Message.Response> =
            listOf(Message.Assistant("complete only", ResponseMetaInfo(Clock.System.now())))

        override fun executeStreaming(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>,
        ): Flow<StreamFrame> =
            listOf(
                StreamFrame.TextComplete("complete only", null),
                StreamFrame.End(null, ResponseMetaInfo(Clock.System.now())),
            ).asFlow()

        override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
            ModerationResult(isHarmful = false, categories = emptyMap())

        override fun close() = Unit
    }

    private class VisionPromptExecutor : PromptExecutor() {
        val toolNamesPerCall = mutableListOf<List<String>>()

        override suspend fun execute(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>,
        ): List<Message.Response> {
            toolNamesPerCall += tools.map { it.name }
            return listOf(Message.Assistant("这是一张图片。", ResponseMetaInfo(Clock.System.now())))
        }

        override fun executeStreaming(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>,
        ): Flow<StreamFrame> {
            toolNamesPerCall += tools.map { it.name }
            return listOf(Message.Assistant("这是一张图片。", ResponseMetaInfo(Clock.System.now())))
                .toStreamFrames()
                .asFlow()
        }

        override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
            ModerationResult(isHarmful = false, categories = emptyMap())

        override fun close() = Unit
    }

    private class TestNoteTool : SimpleTool<TestNoteTool.Args>(
        typeToken<Args>(),
        name = "test_note",
        description = "Test-only note tool.",
    ) {
        @Serializable
        data class Args(val content: String = "")

        override suspend fun execute(args: Args): String =
            """{"status":"noted","content":"${args.content}"}"""
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
