package com.xiaoqi.companion.core.companion

import com.xiaoqi.companion.core.companion.model.UserInput
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.core.prompt.BuiltPrompt
import com.xiaoqi.companion.core.prompt.templates.SystemPersona
import com.xiaoqi.companion.data.db.converter.MemoryType
import com.xiaoqi.companion.data.repository.LlmConfig
import com.xiaoqi.companion.data.repository.MemoryRepository
import com.xiaoqi.companion.data.repository.SaveMemoryRequest
import javax.inject.Inject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class ConversationReflectionInput(
    val userInput: UserInput,
    val assistantReply: String,
    val sourceMessageIds: List<String>,
    val nowMillis: Long = System.currentTimeMillis(),
)

data class ConversationReflectionResult(
    val savedMemoryCount: Int = 0,
)

interface ConversationReflection {
    suspend fun reflectAndSave(
        input: ConversationReflectionInput,
        config: LlmConfig,
        agent: KoogAgentWrapper,
    ): ConversationReflectionResult
}

class LlmConversationReflection @Inject constructor(
    private val memoryRepository: MemoryRepository,
) : ConversationReflection {

    private val emptyReflectionExample: ReflectionResponse by lazy {
        parseExampleOrFallback("reflection_empty") {
            ReflectionResponse(memories = emptyList())
        }
    }

    private val saveReflectionExample: ReflectionResponse by lazy {
        parseExampleOrFallback("reflection_save") {
            ReflectionResponse(
                memories = listOf(
                    ReflectionMemory(
                        shouldSave = true,
                        content = "User likes jasmine tea.",
                        type = "FACT",
                        importance = 0.8f,
                        confidence = 0.9f,
                        sensitivity = "normal",
                        reason = "Durable user preference.",
                    )
                )
            )
        }
    }

    private fun parseExampleOrFallback(
        key: String,
        fallback: () -> ReflectionResponse,
    ): ReflectionResponse {
        val raw = SystemPersona.reflectionExamples[key]?.takeIf { it.isNotBlank() }
            ?: return fallback().also {
                AppLogger.warn(
                    LogTags.Runtime,
                    "reflection_example_missing",
                    "key" to key,
                )
            }
        return runCatching { reflectionJson.decodeFromString(ReflectionResponse.serializer(), raw) }
            .onFailure { error ->
                AppLogger.warn(
                    LogTags.Runtime,
                    "reflection_example_parse_failed",
                    "key" to key,
                    "error" to (error.message ?: error::class.simpleName.orEmpty()),
                )
            }
            .getOrElse { fallback() }
    }

    override suspend fun reflectAndSave(
        input: ConversationReflectionInput,
        config: LlmConfig,
        agent: KoogAgentWrapper,
    ): ConversationReflectionResult {
        val prompt = BuiltPrompt(
            systemPrompt = reflectionSystemPrompt(input.nowMillis),
            userMessage = reflectionUserMessage(input),
            allowTools = false,
        )
        val response = agent.runStructured(
            prompt = prompt,
            serializer = ReflectionResponse.serializer(),
            examples = listOf(emptyReflectionExample, saveReflectionExample),
        )
        var saved = 0
        response.memories
            .filter { it.shouldSave }
            .take(MAX_MEMORIES_PER_TURN)
            .forEach { memory ->
                val content = memory.content.trim()
                if (content.isBlank()) return@forEach
                val type = MemoryType.entries.firstOrNull { it.name == memory.type.uppercase() }
                    ?: MemoryType.EPISODE
                memoryRepository.saveMemory(
                    SaveMemoryRequest(
                        content = content,
                        type = type,
                        importance = memory.importance.coerceIn(0f, 1f),
                        confidence = memory.confidence.coerceIn(0f, 1f),
                        source = "reflection:${config.provider.name.lowercase()}",
                        sourceMessageIds = input.sourceMessageIds,
                        sensitivity = memory.sensitivity.normalizeSensitivity(),
                    )
                )
                saved += 1
            }

        AppLogger.info(
            LogTags.Runtime,
            "reflection_completed",
            "candidateCount" to response.memories.size,
            "savedMemoryCount" to saved,
        )
        return ConversationReflectionResult(savedMemoryCount = saved)
    }

    private fun reflectionSystemPrompt(nowMillis: Long): String {
        val template = SystemPersona.reflectionSystemPrompt.ifBlank { FALLBACK_REFLECTION_SYSTEM_PROMPT }
        return template.replace("{{now_millis}}", nowMillis.toString())
    }

    private fun reflectionUserMessage(input: ConversationReflectionInput): String {
        val template = SystemPersona.reflectionUserTemplate.ifBlank { FALLBACK_REFLECTION_USER_TEMPLATE }
        return template
            .replace("{{input_type}}", input.userInput::class.simpleName.orEmpty())
            .replace("{{user_message}}", input.userInput.content)
            .replace("{{assistant_reply}}", input.assistantReply)
    }

    @Serializable
    private data class ReflectionResponse(
        val memories: List<ReflectionMemory> = emptyList(),
    )

    @Serializable
    private data class ReflectionMemory(
        val shouldSave: Boolean = false,
        val content: String = "",
        val type: String = "EPISODE",
        val importance: Float = 0.5f,
        val confidence: Float = 0.7f,
        val sensitivity: String = "normal",
        @SerialName("reason")
        val reason: String = "",
    )

    private companion object {
        const val MAX_MEMORIES_PER_TURN = 3
        val reflectionJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        const val FALLBACK_REFLECTION_SYSTEM_PROMPT = """
            You are Aura's private memory reflection module.
            Return only the requested structured output.
            Save durable user facts; do not save generic chit-chat.
            Current epoch millis: {{now_millis}}
        """

        const val FALLBACK_REFLECTION_USER_TEMPLATE = """
            User input type: {{input_type}}

            User message:
            {{user_message}}

            Assistant reply:
            {{assistant_reply}}
        """
    }
}

private fun String.normalizeSensitivity(): String =
    when (lowercase()) {
        "private" -> "private"
        "sensitive" -> "sensitive"
        else -> "normal"
    }
