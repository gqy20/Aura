package com.xiaoqi.companion.core.llm

import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import com.xiaoqi.companion.data.repository.LlmConfig
import javax.inject.Inject
import javax.inject.Singleton

interface KoogPromptExecutorFactory {
    fun create(config: LlmConfig): PromptExecutor
}

@Singleton
class DefaultKoogPromptExecutorFactory @Inject constructor(
    private val clientFactory: AnthropicMessagesLLMClientFactory,
) : KoogPromptExecutorFactory {

    override fun create(config: LlmConfig): PromptExecutor =
        MultiLLMPromptExecutor(
            clientFactory.create(
                apiKey = config.apiKey,
                baseUrl = config.baseUrl,
            )
        )
}
