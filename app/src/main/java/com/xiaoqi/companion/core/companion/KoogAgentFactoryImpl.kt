package com.xiaoqi.companion.core.companion

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.ToolCalls
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import ai.koog.agents.ext.agent.HistoryCompressionConfig
import ai.koog.agents.ext.agent.singleRunStrategyWithHistoryCompression
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart
import com.xiaoqi.companion.core.llm.KoogPromptExecutorFactory
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.core.prompt.BuiltPrompt
import com.xiaoqi.companion.core.tools.AgentToolRegistry
import com.xiaoqi.companion.data.repository.LlmConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

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
        createAgent(prompt).run(prompt.userMessage)

    override fun runStreaming(prompt: BuiltPrompt): Flow<String> =
        flow {
            emit(run(prompt))
        }

    private fun createAgent(prompt: BuiltPrompt) =
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
            .build()

    private companion object {
        const val MAX_AGENT_ITERATIONS = 6
    }
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
