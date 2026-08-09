package com.xiaoqi.companion.core.companion

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.annotation.InternalAgentsApi
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
import ai.koog.prompt.message.ResponseMetaInfo
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
import com.xiaoqi.companion.core.tools.CompositeTaskExecution
import com.xiaoqi.companion.core.tools.CompositeTaskPlanner
import com.xiaoqi.companion.core.tools.ToolCallRecorder
import com.xiaoqi.companion.core.tools.ToolResultPromptComposer
import com.xiaoqi.companion.core.tools.ToolScope
import com.xiaoqi.companion.core.tools.isError
import com.xiaoqi.companion.core.tools.isErrorResult
import com.xiaoqi.companion.core.tools.normalizeToolResultJson
import com.xiaoqi.companion.core.tools.withErrorResultKind
import com.xiaoqi.companion.core.tools.withoutToolProtocolArtifacts
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
import kotlin.time.Clock

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
            // 本地模型只注入系统内置工具,不注入 MCP(避免网络开销和复杂 JSON 调用)。
            val effectiveRegistry = if (allowLocalTools) {
                toolRegistry.create(ToolScope.SYSTEM_ONLY, com.xiaoqi.companion.core.tools.ToolPolicy.systemOnly)
            } else {
                ToolRegistry.EMPTY
            }
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

    override suspend fun run(prompt: BuiltPrompt): String {
        val taskExecution = CompositeTaskPlanner.create(prompt.userMessage)
        val result = createAgent(prompt, observer = null, taskExecution).run(prompt.userMessage)
        return if (taskExecution != null && !taskExecution.isComplete) {
            taskExecution.incompleteFallback()
        } else {
            result.withoutToolProtocolArtifacts()
        }
    }

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
        val streamingText = StringBuilder()
        var emittedTextLength = 0
        val taskExecution = CompositeTaskPlanner.create(prompt.userMessage)
        if (taskExecution != null) {
            AppLogger.info(
                LogTags.Llm,
                "compound_task_planned",
                "goal" to taskExecution.goal,
                "stepCount" to taskExecution.totalStepCount,
            )
        }
        fun emitSanitizedStreamingText(flush: Boolean) {
            val sanitized = streamingText.toString().withoutToolProtocolArtifacts()
            val targetLength = if (flush) {
                sanitized.length
            } else {
                (sanitized.length - STREAM_PROTOCOL_GUARD_LENGTH).coerceAtLeast(0)
            }
            if (targetLength <= emittedTextLength) return
            trySend(KoogAgentEvent.TextDelta(sanitized.substring(emittedTextLength, targetLength)))
            emittedTextLength = targetLength
            hasStreamingText = true
        }

        val observer = object : KoogAgentObserver {
            override fun onToolUpdated(call: AgentToolCall) {
                trySend(KoogAgentEvent.ToolCallUpdated(call))
            }

            override fun onTextDelta(text: String) {
                streamingText.append(text)
                emitSanitizedStreamingText(flush = false)
            }

            override fun onTextComplete(text: String) {
                if (streamingText.isEmpty() && text.isNotBlank()) {
                    streamingText.append(text)
                }
                emitSanitizedStreamingText(flush = true)
            }

            override fun onProgress(stage: String, message: String) {
                trySend(KoogAgentEvent.Progress(stage, message))
            }
        }

        taskExecution?.let {
            observer.onProgress(COMPOUND_TASK_STAGE, it.progressMessage())
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
                val result = createAgent(prompt, observer, taskExecution).run(prompt.userMessage)
                val safeResult = if (taskExecution != null && !taskExecution.isComplete) {
                    taskExecution.incompleteFallback()
                } else {
                    result.withoutToolProtocolArtifacts()
                }
                if (safeResult.isNotBlank()) {
                    observer.onTextComplete(safeResult)
                }
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

    private fun createAgent(
        prompt: BuiltPrompt,
        observer: KoogAgentObserver?,
        taskExecution: CompositeTaskExecution? = CompositeTaskPlanner.create(prompt.userMessage),
    ) =
        AIAgent.builder()
            .promptExecutor(executor)
            .llmModel(model)
            .prompt(
                if (taskExecution == null) {
                    prompt.toKoogAgentPrompt()
                } else {
                    prompt.copy(
                        systemPrompt = prompt.systemPrompt + "\n\n" + taskExecution.initialInstruction(),
                    ).toKoogAgentPrompt()
                }
            )
            .toolRegistry(
                if (prompt.hasImage || !prompt.allowTools) {
                    ToolRegistry.EMPTY
                } else {
                    toolRegistry.createForQuery(
                        query = prompt.userMessage,
                        requiredToolNames = taskExecution?.requiredToolNameHints.orEmpty(),
                        policy = if (taskExecution == null) {
                            prompt.toolPolicy
                        } else {
                            com.xiaoqi.companion.core.tools.ToolPolicy.readOnly.copy(
                                maxToolRoundsPerTurn = maxOf(
                                    prompt.toolPolicy.maxToolRoundsPerTurn,
                                    taskExecution.minimumToolRounds,
                                ),
                                maxToolCallsPerTurn = maxOf(
                                    prompt.toolPolicy.maxToolCallsPerTurn,
                                    taskExecution.minimumToolRounds,
                                ),
                            )
                        },
                    )
                }
            )
            .maxIterations(
                if (taskExecution == null) {
                    MAX_AGENT_ITERATIONS
                } else {
                    MAX_COMPOUND_AGENT_ITERATIONS
                }
            )
            .id("companion-agent-${config.provider.name.lowercase()}")
            .graphStrategy(streamingSingleRunStrategy(prompt, taskExecution, observer))
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
                        val resultJson = normalizeToolResultJson(context.toolResult.toString())
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
                            is StreamFrame.TextDelta -> if (taskExecution == null || taskExecution.isComplete) {
                                observer?.onTextDelta(frame.text)
                            }
                            is StreamFrame.TextComplete -> if (taskExecution == null || taskExecution.isComplete) {
                                observer?.onTextComplete(frame.text)
                            }
                            else -> Unit
                        }
                    }
                }
            }
            .build()

    @OptIn(InternalAgentsApi::class)
    private fun streamingSingleRunStrategy(
        prompt: BuiltPrompt,
        taskExecution: CompositeTaskExecution?,
        observer: KoogAgentObserver?,
    ) = strategy<String, String>("single_run_streaming_tools") {
        var toolResultRounds = 0
        var completionGateRetries = 0
        val maxToolRounds = maxOf(
            prompt.toolPolicy.maxToolRoundsPerTurn,
            taskExecution?.minimumToolRounds ?: 0,
        )
        val nodePrepareInitialRequest by node<String, String> { input ->
            if (taskExecution != null && !taskExecution.isComplete) {
                llm.writeSession {
                    val nextStepTools = toolRegistry.tools
                        .map { it.descriptor }
                        .filter { taskExecution.acceptsNextTool(it.name) }
                    if (nextStepTools.isNotEmpty()) {
                        tools = nextStepTools
                        if (nextStepTools.size == 1) {
                            setToolChoiceNamed(nextStepTools.single().name)
                        } else {
                            setToolChoiceRequired()
                        }
                    }
                }
            }
            input
        }
        val nodeCallLLM by nodeLLMRequestStreamingAndSendResults<String>()
        val nodeExecuteTool by nodeExecuteMultipleTools(parallelTools = taskExecution != null)
        val nodeSendToolResult by node<List<ReceivedToolResult>, List<Message.Response>> { results ->
            // 翻 envelope 失败的 resultKind,让 Koog 标准的 Message.Tool.Result.isError 也被点亮
            toolResultRounds += 1
            val patchedResults = results.withErrorResultKind()
            val hasErrors = results.any { it.isErrorResult() }
            taskExecution?.record(results)
            if (taskExecution != null) {
                observer?.onProgress(COMPOUND_TASK_STAGE, taskExecution.progressMessage())
                AppLogger.info(
                    LogTags.Llm,
                    "compound_task_progress",
                    "completedSteps" to taskExecution.completedStepCount,
                    "totalSteps" to taskExecution.totalStepCount,
                    "complete" to taskExecution.isComplete,
                )
            }
            val roundLimitReached = toolResultRounds >= maxToolRounds
            val compoundTaskIncomplete = taskExecution?.isComplete == false
            val finalWithoutTools = roundLimitReached || (hasErrors && taskExecution == null)
            results.forEach { result ->
                AppLogger.info(
                    LogTags.Llm,
                    "tool_execution_result_received",
                    "toolName" to result.tool,
                    "resultKind" to result.resultKind::class.simpleName.orEmpty(),
                    "contentLength" to result.content.length,
                    "contentClass" to result.content.diagnosticContentClass(),
                )
            }
            llm.writeSession {
                appendPrompt {
                    tool {
                        patchedResults.forEach { result(it) }
                    }
                }
                appendPrompt {
                    user(
                        if (finalWithoutTools) {
                            ToolResultPromptComposer.finalWithoutToolsInstruction(
                                hasErrors = hasErrors,
                                roundLimitReached = roundLimitReached,
                            )
                        } else if (taskExecution != null) {
                            taskExecution.nextStepInstruction()
                        } else {
                            ToolResultPromptComposer.followupInstruction(hasErrors = false)
                        }
                    )
                }

                if (taskExecution != null) {
                    if (taskExecution.isComplete) {
                        tools = emptyList()
                        setToolChoiceNone()
                    } else if (!finalWithoutTools) {
                        val nextStepTools = toolRegistry.tools
                            .map { it.descriptor }
                            .filter { taskExecution.acceptsNextTool(it.name) }
                        if (nextStepTools.isNotEmpty()) {
                            tools = nextStepTools
                            if (nextStepTools.size == 1) {
                                setToolChoiceNamed(nextStepTools.single().name)
                            } else {
                                setToolChoiceRequired()
                            }
                            AppLogger.info(
                                LogTags.Llm,
                                "compound_task_tools_constrained",
                                "toolCount" to nextStepTools.size,
                                "completedSteps" to taskExecution.completedStepCount,
                            )
                        }
                    } else {
                        tools = emptyList()
                        setToolChoiceNone()
                    }
                }

                AppLogger.info(
                    LogTags.Llm,
                    "tool_result_llm_request_started",
                    "resultCount" to results.size,
                    "toolResultRounds" to toolResultRounds,
                    "finalWithoutTools" to finalWithoutTools,
                )
                var responses = requestLLMStreaming()
                    .toList()
                    .toMessageResponses()
                while (
                    compoundTaskIncomplete &&
                    !finalWithoutTools &&
                    responses.none { it is Message.Tool.Call } &&
                    completionGateRetries < MAX_COMPLETION_GATE_RETRIES
                ) {
                    completionGateRetries += 1
                    AppLogger.warn(
                        LogTags.Llm,
                        "compound_task_completion_gate_retry",
                        "retry" to completionGateRetries,
                        "completedSteps" to taskExecution.completedStepCount,
                        "totalSteps" to taskExecution.totalStepCount,
                    )
                    appendPrompt { messages(responses) }
                    appendPrompt { user(taskExecution.nextStepInstruction()) }
                    responses = requestLLMStreaming().toList().toMessageResponses()
                }
                responses = when {
                    taskExecution != null && !taskExecution.isComplete &&
                        (finalWithoutTools || responses.none { it is Message.Tool.Call }) -> {
                        listOf(
                            Message.Assistant(
                                taskExecution.incompleteFallback(),
                                metaInfo = ResponseMetaInfo(Clock.System.now()),
                            )
                        )
                    }
                    finalWithoutTools && responses.any { it is Message.Tool.Call } -> {
                        listOf(
                            Message.Assistant(
                                ToolResultPromptComposer.toolLoopFallbackMessage(
                                    hasErrors = hasErrors,
                                    roundLimitReached = roundLimitReached,
                                ),
                                metaInfo = ResponseMetaInfo(Clock.System.now()),
                            )
                        )
                    }
                    else -> responses
                }
                AppLogger.info(
                    LogTags.Llm,
                    "tool_result_llm_request_completed",
                    "resultCount" to results.size,
                    "responseCount" to responses.size,
                    "assistantTextLength" to responses.sumOf { it.content.length },
                    "toolCallCount" to responses.count { it is Message.Tool.Call },
                )
                appendPrompt { messages(responses) }
                responses
            }
        }

        edge(nodeStart forwardTo nodePrepareInitialRequest)
        edge(nodePrepareInitialRequest forwardTo nodeCallLLM)
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
        const val MAX_COMPOUND_AGENT_ITERATIONS = 20
        const val MAX_COMPLETION_GATE_RETRIES = 2
        const val DEFAULT_SESSION_ID = "default"
        const val COMPOUND_TASK_STAGE = "compound_task"
        const val STREAM_PROTOCOL_GUARD_LENGTH = 24
    }
}

private fun String.diagnosticContentClass(): String = when {
    isBlank() -> "blank"
    contains("not found", ignoreCase = true) -> "tool_not_found"
    contains("validation", ignoreCase = true) -> "validation_error"
    contains("required", ignoreCase = true) -> "missing_required_argument"
    contains("error", ignoreCase = true) -> "error_payload"
    else -> "payload"
}

private interface KoogAgentObserver {
    fun onToolUpdated(call: AgentToolCall)
    fun onTextDelta(text: String)
    fun onTextComplete(text: String)
    fun onProgress(stage: String, message: String)
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
