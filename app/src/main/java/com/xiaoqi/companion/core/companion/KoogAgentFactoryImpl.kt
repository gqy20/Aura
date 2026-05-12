package com.xiaoqi.companion.core.companion

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.streaming.StreamFrame
import com.xiaoqi.companion.core.llm.AnthropicMessagesLLMClientFactory
import com.xiaoqi.companion.core.prompt.BuiltPrompt
import com.xiaoqi.companion.data.repository.LlmConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import timber.log.Timber

private const val TAG = "Companion-Agent"

@Singleton
class KoogAgentFactoryImpl @Inject constructor(
    private val clientFactory: AnthropicMessagesLLMClientFactory,
) : KoogAgentFactory {

    override fun create(config: LlmConfig): KoogAgentWrapper {
        Timber.tag(TAG).d("Creating Anthropic Messages agent: provider=%s, model=%s", config.provider, config.modelName)
        return KoogPromptExecutorWrapper(
            config = config,
            executor = MultiLLMPromptExecutor(
                clientFactory.create(
                    apiKey = config.apiKey,
                    baseUrl = config.baseUrl,
                )
            ),
        )
    }
}

private class KoogPromptExecutorWrapper(
    private val config: LlmConfig,
    private val executor: PromptExecutor,
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
        executor.execute(prompt.toKoogPrompt(), model)
            .joinToString("") { it.content }

    override fun runStreaming(prompt: BuiltPrompt): Flow<String> =
        executor.executeStreaming(prompt.toKoogPrompt(), model).mapNotNull { frame ->
            when (frame) {
                is StreamFrame.TextDelta -> frame.text
                else -> null
            }
        }
}

private fun BuiltPrompt.toKoogPrompt() = prompt("companion-chat") {
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
