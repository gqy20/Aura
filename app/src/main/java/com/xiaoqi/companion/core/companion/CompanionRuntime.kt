package com.xiaoqi.companion.core.companion

import com.xiaoqi.companion.core.companion.model.AgentError
import com.xiaoqi.companion.core.companion.model.AgentEvent
import com.xiaoqi.companion.core.companion.model.UserInput
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.core.prompt.PromptBuilder
import com.xiaoqi.companion.data.repository.ConfigRepository
import com.xiaoqi.companion.data.repository.MessageRepository
import java.net.SocketTimeoutException
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
        val startedAt = System.currentTimeMillis()
        try {
            AppLogger.info(
                LogTags.Runtime,
                "pipeline_started",
                "inputType" to input::class.simpleName,
                "inputLength" to input.content.length,
                "hasImage" to (input is UserInput.Vision),
            )

            val prompt = promptBuilder.build(
                input = input,
                emotionContext = emotionMachine.getContext(),
                relationshipContext = relationshipModel.contextModifier(),
            )
            AppLogger.debug(
                LogTags.Runtime,
                "prompt_built",
                "systemLength" to prompt.systemPrompt.length,
                "userMessageLength" to prompt.userMessage.length,
                "hasImage" to prompt.hasImage,
            )

            val config = configRepository.getCurrentLlmConfig().first()
            AppLogger.debug(
                LogTags.Runtime,
                "llm_config_loaded",
                "provider" to config.provider,
                "model" to config.modelName,
                "hasApiKey" to config.apiKey.isNotBlank(),
            )
            val agent = koogAgentFactory.create(config)

            messageRepository.sendMessage(sessionId = DEFAULT_SESSION_ID, content = input.content)

            var rawResponse = ""
            agent.runStreaming(prompt).collect { delta ->
                rawResponse += delta
                emit(AgentEvent.Streaming(delta))
            }
            if (rawResponse.isEmpty()) {
                rawResponse = agent.run(prompt)
            }
            AppLogger.debug(
                LogTags.Llm,
                "response_received",
                "responseLength" to rawResponse.length,
            )

            val parsed = outputParser.parse(rawResponse)
            AppLogger.debug(
                LogTags.Runtime,
                "response_parsed",
                "mood" to parsed.emotionSignal.mood,
                "replyLength" to parsed.textReply.length,
                "actionCount" to parsed.actions.size,
            )

            emotionMachine.feed(parsed.emotionSignal)
            relationshipModel.update(parsed.interactionSignal)

            AppLogger.info(
                LogTags.Runtime,
                "pipeline_completed",
                "durationMs" to (System.currentTimeMillis() - startedAt),
                "replyLength" to parsed.textReply.length,
            )
            emit(AgentEvent.Complete(parsed))
        } catch (e: Exception) {
            AppLogger.error(
                LogTags.Runtime,
                e,
                "pipeline_failed",
                "durationMs" to (System.currentTimeMillis() - startedAt),
                "inputType" to input::class.simpleName,
                "inputLength" to input.content.length,
            )
            val error = when (e) {
                is SocketTimeoutException -> AgentError.NetworkTimeout
                else -> AgentError.ApiError(e.message ?: "Unknown error")
            }
            emit(AgentEvent.Error(error))
        }
    }

    private companion object {
        const val DEFAULT_SESSION_ID = "default"
    }
}
