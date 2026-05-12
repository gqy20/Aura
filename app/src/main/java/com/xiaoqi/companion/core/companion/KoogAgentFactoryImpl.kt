package com.xiaoqi.companion.core.companion

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.ToolCalls
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import ai.koog.agents.ext.agent.HistoryCompressionConfig
import ai.koog.agents.ext.agent.singleRunStrategyWithHistoryCompression
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.streaming.StreamFrame
import com.xiaoqi.companion.core.llm.KoogPromptExecutorFactory
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.core.companion.model.AgentToolCall
import com.xiaoqi.companion.core.companion.model.ToolCallStatus
import com.xiaoqi.companion.core.prompt.BuiltPrompt
import com.xiaoqi.companion.core.tools.AgentToolRegistry
import com.xiaoqi.companion.core.tools.ToolCallRecorder
import com.xiaoqi.companion.data.repository.LlmConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.awaitClose

@Singleton
class KoogAgentFactoryImpl @Inject constructor(
    private val executorFactory: KoogPromptExecutorFactory,
    private val toolRegistry: AgentToolRegistry,
    private val toolCallRecorder: ToolCallRecorder,
) : KoogAgentFactory {

    override fun create(config: LlmConfig): KoogAgentWrapper {
        AppLogger.debug(
            LogTags.Llm,
            "agent_created",
            "provider" to config.provider,
            "model" to config.modelName,
            "hasApiKey" to config.apiKey.isNotBlank(),
        )
        return KoogPromptExecutorWrapper(
            config = config,
            executor = executorFactory.create(config),
            toolRegistry = toolRegistry,
            toolCallRecorder = toolCallRecorder,
        )
    }
}

private class KoogPromptExecutorWrapper(
    private val config: LlmConfig,
    private val executor: PromptExecutor,
    private val toolRegistry: AgentToolRegistry,
    private val toolCallRecorder: ToolCallRecorder,
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
        }

        val job = launch {
            try {
                val response = createAgent(prompt, observer).run(prompt.userMessage)
                if (!hasStreamingText) {
                    trySend(KoogAgentEvent.TextDelta(response))
                }
                close()
            } catch (e: Throwable) {
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
            .toolRegistry(toolRegistry.create())
            .maxIterations(MAX_AGENT_ITERATIONS)
            .id("companion-agent-${config.provider.name.lowercase()}")
            .graphStrategy(
                singleRunStrategyWithHistoryCompression(
                    HistoryCompressionConfig(
                        isHistoryTooBig = { false },
                        compressionStrategy = HistoryCompressionStrategy.NoCompression,
                        retrievalModel = model,
                    ),
                    ToolCalls.SEQUENTIAL,
                )
            )
            .install {
                install(EventHandler.Feature) {
                    onToolCallStarting { context ->
                        val callId = context.toolCallId?.ifBlank { context.eventId } ?: context.eventId
                        val argumentsJson = context.toolArgs.toString()
                        toolCallRecorder.start(
                            sessionId = DEFAULT_SESSION_ID,
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
                            else -> Unit
                        }
                    }
                }
            }
            .build()

    private companion object {
        const val MAX_AGENT_ITERATIONS = 6
        const val DEFAULT_SESSION_ID = "default"
    }
}

private interface KoogAgentObserver {
    fun onToolUpdated(call: AgentToolCall)
    fun onTextDelta(text: String)
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
