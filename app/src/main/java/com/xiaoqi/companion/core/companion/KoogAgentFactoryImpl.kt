package com.xiaoqi.companion.core.companion

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.dsl.extension.nodeExecuteMultipleTools
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStreamingAndSendResults
import ai.koog.agents.core.dsl.extension.onMultipleAssistantMessages
import ai.koog.agents.core.dsl.extension.onMultipleToolCalls
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.result
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.executeStructured
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.toMessageResponses
import com.xiaoqi.companion.core.llm.KoogPromptExecutorFactory
import com.xiaoqi.companion.core.local.LocalQwenEngine
import com.xiaoqi.companion.core.local.ReactiveCompanion
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.core.companion.model.AgentToolCall
import com.xiaoqi.companion.core.companion.model.ToolCallStatus
import com.xiaoqi.companion.core.prompt.BuiltPrompt
import com.xiaoqi.companion.core.tools.AgentToolRegistry
import com.xiaoqi.companion.core.tools.ToolCallRecorder
import com.xiaoqi.companion.core.tools.ToolResultPromptComposer
import com.xiaoqi.companion.core.tools.isError
import com.xiaoqi.companion.core.tools.withErrorResultKind
import com.xiaoqi.companion.data.db.converter.LlmProvider
import com.xiaoqi.companion.data.repository.LlmConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.serialization.KSerializer

@Singleton
class KoogAgentFactoryImpl @Inject constructor(
    private val executorFactory: KoogPromptExecutorFactory,
    private val localQwenEngine: LocalQwenEngine,
    private val toolRegistry: AgentToolRegistry,
    private val toolCallRecorder: ToolCallRecorder,
) : KoogAgentFactory {

    override fun create(config: LlmConfig, sessionId: String, allowLocalTools: Boolean): KoogAgentWrapper {
        AppLogger.debug(
            LogTags.Llm,
            "agent_created",
            "provider" to config.provider,
            "model" to config.modelName,
            "hasApiKey" to config.apiKey.isNotBlank(),
            "allowLocalTools" to allowLocalTools,
        )
        if (config.provider == LlmProvider.LOCAL_QWEN) {
            // 0.8B/4B 量化模型在端侧产出 JSON 质量不稳定(0.8B 成功率 < 70%),
            // 且每轮工具调用 = 一次完整 LLM 推理,多轮惩罚严重(最坏 5x)。
            // 默认走纯文本陪伴对话;用户可在 Settings 手动开启工具调用(allowLocalTools=true),
            // 走 LocalToolProtocol 软协议。见 docs/roadmap.md §dual-mind 分工。
            val effectiveRegistry = if (allowLocalTools) toolRegistry.create() else ToolRegistry.EMPTY
            @Suppress("DEPRECATION_RENAMED_TO_REACTIVE_COMPANION")
            return ReactiveCompanion(
                engine = localQwenEngine,
                modelName = config.modelName,
                toolRegistry = effectiveRegistry,
                toolCallRecorder = toolCallRecorder,
                sessionId = sessionId,
            )
        }
        return KoogPromptExecutorWrapper(
            config = config,
            executor = executorFactory.create(config),
            toolRegistry = toolRegistry,
            toolCallRecorder = toolCallRecorder,
            sessionId = sessionId,
        )
    }
}

private class KoogPromptExecutorWrapper(
    private val config: LlmConfig,
    private val executor: PromptExecutor,
    private val toolRegistry: AgentToolRegistry,
    private val toolCallRecorder: ToolCallRecorder,
    private val sessionId: String = DEFAULT_SESSION_ID,
) : KoogAgentWrapper {

    private val model = LLModel(
        provider = LLMProvider.Anthropic,
        id = config.modelName,
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Vision.Image,
        ),
    )

    override suspend fun run(prompt: BuiltPrompt): String =
        createAgent(prompt, observer = null).run(prompt.userMessage)

    override suspend fun <T> runStructured(
        prompt: BuiltPrompt,
        serializer: KSerializer<T>,
        examples: List<T>,
    ): T {
        val structuredPrompt = prompt.copy(allowTools = false)
        return executor.executeStructured(
            prompt = structuredPrompt.toKoogAgentPrompt(),
            model = model,
            serializer = serializer,
            examples = examples,
        ).getOrThrow().data
    }

    override fun runStreaming(prompt: BuiltPrompt): Flow<String> =
        runEvents(prompt).mapNotNull { event ->
            (event as? KoogAgentEvent.TextDelta)?.text
        }

    override fun runEvents(prompt: BuiltPrompt): Flow<KoogAgentEvent> = callbackFlow {
        var hasStreamingText = false
        val observer = object : KoogAgentObserver {
            override fun onToolUpdated(call: AgentToolCall) {
                trySend(KoogAgentEvent.ToolCallUpdated(call))
            }

            override fun onTextDelta(text: String) {
                hasStreamingText = true
                trySend(KoogAgentEvent.TextDelta(text))
            }

            override fun onTextComplete(text: String) {
                if (!hasStreamingText && text.isNotBlank()) {
                    hasStreamingText = true
                    trySend(KoogAgentEvent.TextDelta(text))
                }
            }
        }

        val job = launch {
            try {
                AppLogger.info(
                    LogTags.Llm,
                    "agent_run_started",
                    "model" to model.id,
                    "allowTools" to prompt.allowTools,
                    "hasImage" to prompt.hasImage,
                    "userMessageLength" to prompt.userMessage.length,
                )
                createAgent(prompt, observer).run(prompt.userMessage)
                AppLogger.info(
                    LogTags.Llm,
                    "agent_run_completed",
                    "model" to model.id,
                    "hasStreamingText" to hasStreamingText,
                )
                close()
            } catch (e: Throwable) {
                AppLogger.error(
                    LogTags.Llm,
                    e,
                    "agent_run_failed",
                    "model" to model.id,
                    "hasStreamingText" to hasStreamingText,
                )
                close(e)
            }
        }

        awaitClose { job.cancel() }
    }

    private fun createAgent(prompt: BuiltPrompt, observer: KoogAgentObserver?) =
        AIAgent.builder()
            .promptExecutor(executor)
            .llmModel(model)
            .prompt(prompt.toKoogAgentPrompt())
            .toolRegistry(if (prompt.hasImage || !prompt.allowTools) ToolRegistry.EMPTY else toolRegistry.create())
            .maxIterations(MAX_AGENT_ITERATIONS)
            .id("companion-agent-${config.provider.name.lowercase()}")
            .graphStrategy(streamingSingleRunStrategy())
            .install {
                install(EventHandler.Feature) {
                    onToolCallStarting { context ->
                        val callId = context.toolCallId?.ifBlank { context.eventId } ?: context.eventId
                        val argumentsJson = context.toolArgs.toString()
                        toolCallRecorder.start(
                            sessionId = sessionId,
                            callId = callId,
                            toolName = context.toolName,
                            argumentsJson = argumentsJson,
                        )
                        observer?.onToolUpdated(
                            AgentToolCall(
                                name = context.toolName,
                                status = ToolCallStatus.STARTED,
                                callId = callId,
                                argumentsJson = argumentsJson,
                            )
                        )
                    }
                    onToolCallCompleted { context ->
                        val callId = context.toolCallId?.ifBlank { context.eventId } ?: context.eventId
                        val argumentsJson = context.toolArgs.toString()
                        val resultJson = context.toolResult.toString()
                        toolCallRecorder.succeed(callId = callId, resultJson = resultJson)
                        observer?.onToolUpdated(
                            AgentToolCall(
                                name = context.toolName,
                                status = ToolCallStatus.SUCCEEDED,
                                callId = callId,
                                argumentsJson = argumentsJson,
                                resultJson = resultJson,
                            )
                        )
                    }
                    onToolCallFailed { context ->
                        val callId = context.toolCallId?.ifBlank { context.eventId } ?: context.eventId
                        val argumentsJson = context.toolArgs.toString()
                        toolCallRecorder.fail(callId = callId, errorMessage = context.message)
                        observer?.onToolUpdated(
                            AgentToolCall(
                                name = context.toolName,
                                status = ToolCallStatus.FAILED,
                                callId = callId,
                                argumentsJson = argumentsJson,
                                errorMessage = context.message,
                            )
                        )
                    }
                    onToolValidationFailed { context ->
                        val callId = context.toolCallId?.ifBlank { context.eventId } ?: context.eventId
                        val argumentsJson = context.toolArgs.toString()
                        toolCallRecorder.fail(callId = callId, errorMessage = context.message)
                        observer?.onToolUpdated(
                            AgentToolCall(
                                name = context.toolName,
                                status = ToolCallStatus.FAILED,
                                callId = callId,
                                argumentsJson = argumentsJson,
                                errorMessage = context.message,
                            )
                        )
                    }
                    onLLMStreamingFrameReceived { context ->
                        when (val frame = context.streamFrame) {
                            is StreamFrame.TextDelta -> observer?.onTextDelta(frame.text)
                            is StreamFrame.TextComplete -> observer?.onTextComplete(frame.text)
                            else -> Unit
                        }
                    }
                }
            }
            .build()

    private fun streamingSingleRunStrategy() = strategy<String, String>("single_run_streaming_tools") {
        val nodeCallLLM by nodeLLMRequestStreamingAndSendResults<String>()
        val nodeExecuteTool by nodeExecuteMultipleTools(parallelTools = false)
        val nodeSendToolResult by node<List<ReceivedToolResult>, List<Message.Response>> { results ->
            // 翻 envelope 失败的 resultKind,让 Koog 标准的 Message.Tool.Result.isError 也被点亮
            val patchedResults = results.withErrorResultKind()
            val hasErrors = results.any { isError(it.content) }
            llm.writeSession {
                appendPrompt {
                    tool {
                        patchedResults.forEach { result(it) }
                    }
                }
                appendPrompt {
                    tool {
                        results.forEach { result(it) }
                    }
                }
                appendPrompt {
                    user(ToolResultPromptComposer.followupInstruction(hasErrors))
                }

                AppLogger.info(
                    LogTags.Llm,
                    "tool_result_llm_request_started",
                    "resultCount" to results.size,
                )
                requestLLMStreaming()
                    .toList()
                    .toMessageResponses()
                    .also { responses ->
                        AppLogger.info(
                            LogTags.Llm,
                            "tool_result_llm_request_completed",
                            "resultCount" to results.size,
                            "responseCount" to responses.size,
                            "assistantTextLength" to responses.sumOf { it.content.length },
                            "toolCallCount" to responses.count { it is Message.Tool.Call },
                        )
                        appendPrompt { messages(responses) }
                    }
            }
        }

        edge(nodeStart forwardTo nodeCallLLM)
        edge(nodeCallLLM forwardTo nodeExecuteTool onMultipleToolCalls { true })
        edge(
            nodeCallLLM forwardTo nodeFinish
                onMultipleAssistantMessages { true }
                transformed { it.joinToString("\n") { message -> message.content } }
        )

        edge(nodeExecuteTool forwardTo nodeSendToolResult)
        edge(nodeSendToolResult forwardTo nodeExecuteTool onMultipleToolCalls { true })
        edge(
            nodeSendToolResult forwardTo nodeFinish
                onMultipleAssistantMessages { true }
                transformed { it.joinToString("\n") { message -> message.content } }
        )
    }

    private companion object {
        const val MAX_AGENT_ITERATIONS = 12
        const val DEFAULT_SESSION_ID = "default"
    }
}

private interface KoogAgentObserver {
    fun onToolUpdated(call: AgentToolCall)
    fun onTextDelta(text: String)
    fun onTextComplete(text: String)
}

private fun BuiltPrompt.toKoogAgentPrompt() = prompt("companion-chat") {
    system(systemPrompt)
    if (hasImage && imageBase64 != null) {
        user {
            text(userMessage)
            image(
                ContentPart.Image(
                    content = AttachmentContent.Binary.Base64(imageBase64),
                    format = "base64",
                    mimeType = imageMediaType ?: "image/jpeg",
                )
            )
        }
    } else {
        user(userMessage)
    }
}
