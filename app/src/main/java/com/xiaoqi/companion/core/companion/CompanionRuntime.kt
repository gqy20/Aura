package com.xiaoqi.companion.core.companion

import com.xiaoqi.companion.core.companion.model.AgentError
import com.xiaoqi.companion.core.companion.model.AgentEvent
import com.xiaoqi.companion.core.companion.model.ToolCallStatus
import com.xiaoqi.companion.core.companion.model.UserInput
import com.xiaoqi.companion.core.context.CurrentLocationProvider
import com.xiaoqi.companion.core.context.toPromptContext
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.core.prompt.PromptBuilder
import com.xiaoqi.companion.core.tools.parseOrNull
import com.xiaoqi.companion.data.datastore.AppPreferences
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
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull

open class CompanionRuntime @Inject constructor(
    private val configRepository: ConfigRepository,
    private val koogAgentFactory: KoogAgentFactory,
    private val promptBuilder: PromptBuilder,
    private val messageRepository: MessageRepository,
    private val memoryRepository: MemoryRepository,
    private val conversationContextBuilder: ConversationContextBuilder,
    private val emotionMachine: EmotionStateMachine,
    private val relationshipModel: RelationshipModel,
    private val locationProvider: CurrentLocationProvider,
    private val appPreferences: AppPreferences,
    private val conversationRepository: com.xiaoqi.companion.data.repository.ConversationRepository,
) {
    open suspend fun send(input: UserInput): Flow<AgentEvent> = callbackFlow {
        val startedAt = System.currentTimeMillis()
        val sessionId = appPreferences.currentSessionId.first()
        try {
            AppLogger.info(
                LogTags.Runtime,
                "pipeline_started",
                "inputType" to input::class.simpleName,
                "inputLength" to input.content.length,
                "hasImage" to (input is UserInput.Vision),
                "sessionId" to sessionId,
            )

            val memoryContext = memoryRepository.selectPromptContext(input.content)
            val conversationContext = conversationContextBuilder.build(sessionId)
            val locationContext = if (appPreferences.locationContextEnabled.first()) {
                withContext(Dispatchers.IO) { locationProvider.getLastKnownLocation() }?.let { loc ->
                    "用户当前设备位置:${loc.toPromptContext()}。调用地图、周边搜索、路径规划等需要坐标的工具时," +
                        "请用此坐标作为 location 中心点,不要自行编造坐标。"
                }
            } else null
            val prompt = promptBuilder.build(
                input = input,
                emotionContext = emotionMachine.getContext(),
                relationshipContext = relationshipModel.contextModifier(),
                recentConversation = conversationContext.recentMessages,
                memories = memoryContext.memorySnippets,
                summaries = memoryContext.summarySnippets,
                locationContext = locationContext,
            )
            AppLogger.debug(
                LogTags.Runtime,
                "prompt_built",
                "systemLength" to prompt.systemPrompt.length,
                "userMessageLength" to prompt.userMessage.length,
                "hasImage" to prompt.hasImage,
                "memoryCount" to memoryContext.memorySnippets.size,
                "summaryCount" to memoryContext.summarySnippets.size,
                "recentConversationCount" to conversationContext.recentMessages.size,
                "recentConversationTokens" to conversationContext.estimatedTokens,
                "omittedOlderMessages" to conversationContext.omittedOlderMessageCount,
            )

            val config = configRepository.getCurrentLlmConfig().first()
            AppLogger.debug(
                LogTags.Runtime,
                "llm_config_loaded",
                "provider" to config.provider,
                "model" to config.modelName,
                "hasApiKey" to config.apiKey.isNotBlank(),
            )
            val agent = koogAgentFactory.create(config, sessionId)

            val userMessageId = messageRepository.sendMessage(
                sessionId = sessionId,
                content = input.content,
                imageBase64 = (input as? UserInput.Vision)?.imageBase64,
            )
            conversationRepository.onMessageSent(sessionId, input.content)

            var rawResponse = ""
            val job = launch(Dispatchers.IO) {
                agent.runEvents(prompt).collect { event ->
                    when (event) {
                        is KoogAgentEvent.TextDelta -> {
                            rawResponse += event.text
                            trySend(AgentEvent.Streaming(event.text))
                        }
                        is KoogAgentEvent.ToolCallUpdated -> {
                            trySend(AgentEvent.ToolCallUpdated(event.call))
                            // update_state 完成时，如果有记忆保存，触发 MemorySaved 事件
                            if (event.call.name == "update_state" &&
                                event.call.status == ToolCallStatus.SUCCEEDED
                            ) {
                                val resultJson = event.call.resultJson
                                val envelope = parseOrNull(resultJson)
                                if (envelope is com.xiaoqi.companion.core.tools.ToolEnvelope.Ok) {
                                    val memorySaved = envelope.data["memorySaved"]
                                        ?.jsonPrimitive?.intOrNull ?: 0
                                    if (memorySaved > 0) {
                                        trySend(AgentEvent.MemorySaved(memorySaved))
                                    }
                                }
                            }
                        }
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
                val assistantMessageId = messageRepository.saveAssistantMessage(
                    sessionId = sessionId,
                    content = rawResponse,
                )
                AppLogger.debug(
                    LogTags.Runtime,
                    "response_saved",
                    "replyLength" to rawResponse.length,
                )

                AppLogger.info(
                    LogTags.Runtime,
                    "pipeline_completed",
                    "durationMs" to (System.currentTimeMillis() - startedAt),
                    "replyLength" to rawResponse.length,
                )
                trySend(AgentEvent.Complete(rawResponse))
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
