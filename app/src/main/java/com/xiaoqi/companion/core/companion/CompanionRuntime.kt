package com.xiaoqi.companion.core.companion

import com.xiaoqi.companion.core.companion.model.AgentError
import com.xiaoqi.companion.core.companion.model.AgentEvent
import com.xiaoqi.companion.core.companion.model.UserInput
import com.xiaoqi.companion.core.prompt.BuiltPrompt
import com.xiaoqi.companion.core.prompt.PromptBuilder
import com.xiaoqi.companion.data.repository.ConfigRepository
import com.xiaoqi.companion.data.repository.LlmConfig
import com.xiaoqi.companion.data.repository.MessageRepository
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

    open suspend fun send(input: UserInput): Flow<AgentEvent> = flow {
        try {
            // 1. Build prompt with context
            val prompt = promptBuilder.build(
                input = input,
                emotionContext = emotionMachine.getContext(),
                relationshipContext = relationshipModel.contextModifier(),
            )

            // 2. Get LLM config and create agent
            val config = configRepository.getCurrentLlmConfig().first()
            val agent = koogAgentFactory.create(config)

            // 3. Store user message
            messageRepository.sendMessage(sessionId = "default", content = input.content)

            // 4. Call LLM
            val rawResponse = agent.run(prompt)

            // 5. Parse output
            val parsed = outputParser.parse(rawResponse)

            // 6. Update emotion
            emotionMachine.feed(parsed.emotionSignal)

            // 7. Update relationship
            relationshipModel.update(parsed.interactionSignal)

            // 8. Emit complete event
            emit(AgentEvent.Complete(parsed))

        } catch (e: Exception) {
            val error = when (e) {
                is java.net.SocketTimeoutException -> AgentError.NetworkTimeout
                else -> AgentError.ApiError(e.message ?: "Unknown error")
            }
            emit(AgentEvent.Error(error))
        }
    }
}
