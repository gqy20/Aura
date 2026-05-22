package com.xiaoqi.companion.core.companion

import com.xiaoqi.companion.core.companion.model.UserInput
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.core.prompt.BuiltPrompt
import com.xiaoqi.companion.data.db.converter.MemoryType
import com.xiaoqi.companion.data.repository.LlmConfig
import com.xiaoqi.companion.data.repository.MemoryRepository
import com.xiaoqi.companion.data.repository.SaveMemoryRequest
import javax.inject.Inject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

    private fun reflectionSystemPrompt(nowMillis: Long): String =
        """
        You are Aura's private memory reflection module.
        Analyze the just-finished conversation turn and decide what should be saved for future conversations.
        Return only the requested structured output. Do not include markdown, explanations, or free text.

        Save memories only when they are useful later:
        - explicit user requests to remember something
        - durable user facts, preferences, relationships, routines, plans, or important episodes
        - short-term tasks/plans can be saved if the user clearly asked to remember them

        Do not save:
        - assistant claims that something was saved
        - generic questions, chit-chat, temporary wording, or model speculation
        - facts about other people unless useful to the user's future context
        - content the user says not to remember

        Use the requested structured output shape. Return an empty memories array when nothing should be saved.

        Current epoch millis: $nowMillis
        """.trimIndent()

    private fun reflectionUserMessage(input: ConversationReflectionInput): String =
        buildString {
            appendLine("User input type: ${input.userInput::class.simpleName}")
            appendLine("User message:")
            appendLine(input.userInput.content)
            appendLine()
            appendLine("Assistant reply:")
            appendLine(input.assistantReply)
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
        val emptyReflectionExample = ReflectionResponse(memories = emptyList())
        val saveReflectionExample = ReflectionResponse(
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

private fun String.normalizeSensitivity(): String =
    when (lowercase()) {
        "private" -> "private"
        "sensitive" -> "sensitive"
        else -> "normal"
    }
