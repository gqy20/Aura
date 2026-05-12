package com.xiaoqi.companion.core.companion

import com.xiaoqi.companion.core.companion.model.AgentError
import com.xiaoqi.companion.core.companion.model.AgentEvent
import com.xiaoqi.companion.core.companion.model.UserInput
import com.xiaoqi.companion.core.prompt.BuiltPrompt
import com.xiaoqi.companion.core.prompt.PromptBuilder
import com.xiaoqi.companion.data.repository.ConfigRepository
import com.xiaoqi.companion.data.repository.LlmConfig
import com.xiaoqi.companion.data.repository.MessageRepository
import timber.log.Timber
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

open class CompanionRuntime @Inject constructor(
    private val configRepository: ConfigRepository,
    private val koogAgentFactory: KoogAgentFactory,
    private val promptBuilder: PromptBuilder,
    private val outputParser: OutputParser,
    private val messageRepository: MessageRepository,
    private val emotionMachine: EmotionStateMachine,
    private val relationshipModel: RelationshipModel,
) {
    companion object {
        private const val TAG = "Companion-Runtime"
    }

    open suspend fun send(input: UserInput): Flow<AgentEvent> = flow {
        try {
            Timber.tag(TAG).d("Pipeline start: input=%s", input.content.take(50))

            // 1. Build prompt with context
            val prompt = promptBuilder.build(
                input = input,
                emotionContext = emotionMachine.getContext(),
                relationshipContext = relationshipModel.contextModifier(),
            )
            Timber.tag(TAG).d("Prompt built, system length=%d", prompt.systemPrompt.length)

            // 2. Get LLM config and create agent
            val config = configRepository.getCurrentLlmConfig().first()
            Timber.tag(TAG).d("LLM config: provider=%s, model=%s", config.provider, config.modelName)
            val agent = koogAgentFactory.create(config)

            // 3. Store user message
            messageRepository.sendMessage(sessionId = "default", content = input.content)

            // 4. Call LLM
            val rawResponse = agent.run(prompt)
            Timber.tag(TAG).d("LLM raw response length=%d", rawResponse.length)

            // 5. Parse output
            val parsed = outputParser.parse(rawResponse)
            Timber.tag(TAG).d("Parsed: mood=%s, text length=%d", parsed.emotionSignal.mood, parsed.textReply.length)

            // 6. Update emotion
            emotionMachine.feed(parsed.emotionSignal)

            // 7. Update relationship
            relationshipModel.update(parsed.interactionSignal)

            // 8. Emit complete event
            Timber.tag(TAG).d("Pipeline complete")
            emit(AgentEvent.Complete(parsed))

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Pipeline failed for input: '%s'", input.content.take(50))
            val error = when (e) {
                is java.net.SocketTimeoutException -> AgentError.NetworkTimeout
                else -> AgentError.ApiError(e.message ?: "Unknown error")
            }
            emit(AgentEvent.Error(error))
        }
    }
}
