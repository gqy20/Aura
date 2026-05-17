package com.xiaoqi.companion.core.companion

import com.xiaoqi.companion.core.companion.model.AgentError
import com.xiaoqi.companion.core.companion.model.AgentEvent
import com.xiaoqi.companion.core.companion.model.UserInput
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.core.prompt.PromptBuilder
import com.xiaoqi.companion.data.repository.ConfigRepository
import com.xiaoqi.companion.data.repository.MemoryRepository
import com.xiaoqi.companion.data.repository.MessageRepository
import java.net.SocketTimeoutException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class CompanionRuntime @Inject constructor(
    private val configRepository: ConfigRepository,
    private val koogAgentFactory: KoogAgentFactory,
    private val promptBuilder: PromptBuilder,
    private val outputParser: OutputParser,
    private val messageRepository: MessageRepository,
    private val memoryRepository: MemoryRepository,
    private val visionMemoryExtractor: VisionMemoryExtractor,
    private val emotionMachine: EmotionStateMachine,
    private val relationshipModel: RelationshipModel,
) {
    open suspend fun send(input: UserInput): Flow<AgentEvent> = callbackFlow {
        val startedAt = System.currentTimeMillis()
        try {
            AppLogger.info(
                LogTags.Runtime,
                "pipeline_started",
                "inputType" to input::class.simpleName,
                "inputLength" to input.content.length,
                "hasImage" to (input is UserInput.Vision),
            )

            val memoryContext = memoryRepository.selectPromptContext(input.content)
            val prompt = promptBuilder.build(
                input = input,
                emotionContext = emotionMachine.getContext(),
                relationshipContext = relationshipModel.contextModifier(),
                memories = memoryContext.memorySnippets + memoryContext.summarySnippets,
            )
            AppLogger.debug(
                LogTags.Runtime,
                "prompt_built",
                "systemLength" to prompt.systemPrompt.length,
                "userMessageLength" to prompt.userMessage.length,
                "hasImage" to prompt.hasImage,
                "memoryCount" to memoryContext.memorySnippets.size,
                "summaryCount" to memoryContext.summarySnippets.size,
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

            val userMessageId = messageRepository.sendMessage(
                sessionId = DEFAULT_SESSION_ID,
                content = input.content,
                imageBase64 = (input as? UserInput.Vision)?.imageBase64,
            )

            var rawResponse = ""
            val job = launch(Dispatchers.IO) {
                agent.runEvents(prompt).collect { event ->
                    when (event) {
                        is KoogAgentEvent.TextDelta -> {
                            rawResponse += event.text
                            trySend(AgentEvent.Streaming(event.text))
                        }
                        is KoogAgentEvent.ToolCallUpdated -> trySend(AgentEvent.ToolCallUpdated(event.call))
                        is KoogAgentEvent.ToolStarted -> trySend(AgentEvent.ToolStarted(event.name))
                        is KoogAgentEvent.ToolFinished -> trySend(AgentEvent.ToolFinished(event.name))
                    }
                }
            }
            job.join()

            AppLogger.debug(
                LogTags.Llm,
                "response_received",
                "responseLength" to rawResponse.length,
            )

            if (rawResponse.isBlank()) {
                AppLogger.warn(
                    LogTags.Runtime,
                    "empty_model_response",
                    "durationMs" to (System.currentTimeMillis() - startedAt),
                )
                trySend(AgentEvent.Error(AgentError.ParseError("Empty model response")))
            } else {
                val parsed = outputParser.parse(rawResponse)
                val assistantMessageId = messageRepository.saveAssistantMessage(
                    sessionId = DEFAULT_SESSION_ID,
                    content = parsed.textReply,
                )
                AppLogger.debug(
                    LogTags.Runtime,
                    "response_parsed",
                    "mood" to parsed.emotionSignal.mood,
                    "replyLength" to parsed.textReply.length,
                    "actionCount" to parsed.actions.size,
                )

                emotionMachine.feed(parsed.emotionSignal)
                relationshipModel.update(parsed.interactionSignal)
                (input as? UserInput.Vision)?.let { visionInput ->
                    runCatching {
                        visionMemoryExtractor.extractAndSave(
                            input = visionInput,
                            assistantReply = parsed.textReply,
                            sourceMessageIds = listOfNotNull(userMessageId, assistantMessageId),
                        )
                    }.onFailure { error ->
                        AppLogger.warn(
                            LogTags.Runtime,
                            "vision_memory_extract_failed",
                            "message" to (error.message ?: error::class.simpleName.orEmpty()),
                        )
                    }
                }

                AppLogger.info(
                    LogTags.Runtime,
                    "pipeline_completed",
                    "durationMs" to (System.currentTimeMillis() - startedAt),
                    "replyLength" to parsed.textReply.length,
                )
                trySend(AgentEvent.Complete(parsed))
            }
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
            trySend(AgentEvent.Error(error))
        }
        awaitClose {  }
    }

    private companion object {
        const val DEFAULT_SESSION_ID = "default"
    }
}
