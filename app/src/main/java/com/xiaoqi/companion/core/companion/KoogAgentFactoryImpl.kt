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
import com.xiaoqi.companion.core.prompt.BuiltPrompt
import com.xiaoqi.companion.core.tools.AgentToolRegistry
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
        )
    }
}

private class KoogPromptExecutorWrapper(
    private val config: LlmConfig,
    private val executor: PromptExecutor,
    private val toolRegistry: AgentToolRegistry,
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
            override fun onToolStarted(name: String) {
                trySend(KoogAgentEvent.ToolStarted(name))
            }

            override fun onToolFinished(name: String) {
                trySend(KoogAgentEvent.ToolFinished(name))
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
                observer ?: return@install
                install(EventHandler.Feature) {
                    onToolCallStarting { context ->
                        observer.onToolStarted(context.toolName)
                    }
                    onToolCallCompleted { context ->
                        observer.onToolFinished(context.toolName)
                    }
                    onLLMStreamingFrameReceived { context ->
                        when (val frame = context.streamFrame) {
                            is StreamFrame.TextDelta -> observer.onTextDelta(frame.text)
                            else -> Unit
                        }
                    }
                }
            }
            .build()

    private companion object {
        const val MAX_AGENT_ITERATIONS = 6
    }
}

private interface KoogAgentObserver {
    fun onToolStarted(name: String)
    fun onToolFinished(name: String)
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
