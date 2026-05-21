package com.xiaoqi.companion.core.companion

import com.xiaoqi.companion.data.db.converter.MessageRole
import com.xiaoqi.companion.data.db.entity.MessageEntity
import com.xiaoqi.companion.data.repository.MessageRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ConversationContext(
    val recentMessages: List<String>,
    val recentMessageIds: List<String>,
    val estimatedTokens: Int,
    val omittedOlderMessageCount: Int,
)

class ConversationContextBuilder @Inject constructor(
    private val messageRepository: MessageRepository,
) {
    private var rawTokenBudget: Int = DEFAULT_RAW_TOKEN_BUDGET
    private var candidateLimit: Int = DEFAULT_CANDIDATE_LIMIT

    internal constructor(
        messageRepository: MessageRepository,
        rawTokenBudget: Int,
        candidateLimit: Int,
    ) : this(messageRepository) {
        this.rawTokenBudget = rawTokenBudget
        this.candidateLimit = candidateLimit
    }

    suspend fun build(sessionId: String): ConversationContext =
        withContext(Dispatchers.IO) {
            val candidates = messageRepository.getRecentMessages(
                sessionId = sessionId,
                limit = candidateLimit,
            )
            selectRecentWindow(candidates)
        }

    private fun selectRecentWindow(recentFirst: List<MessageEntity>): ConversationContext {
        if (recentFirst.isEmpty()) {
            return ConversationContext(
                recentMessages = emptyList(),
                recentMessageIds = emptyList(),
                estimatedTokens = 0,
                omittedOlderMessageCount = 0,
            )
        }

        val selected = mutableListOf<PromptMessage>()
        var usedTokens = 0
        var omitted = 0

        for (message in recentFirst) {
            val promptMessage = message.toPromptMessage()
            val tokens = estimateTokens(promptMessage.text)
            if (usedTokens + tokens <= rawTokenBudget) {
                selected += promptMessage
                usedTokens += tokens
                continue
            }

            if (selected.isEmpty()) {
                val truncated = promptMessage.copy(text = promptMessage.text.truncateToTokenBudget(rawTokenBudget))
                selected += truncated
                usedTokens = estimateTokens(truncated.text)
                omitted = (recentFirst.size - 1).coerceAtLeast(0)
            } else {
                omitted = recentFirst.size - selected.size
            }
            break
        }

        val chronological = selected.asReversed()
        return ConversationContext(
            recentMessages = chronological.map { it.text },
            recentMessageIds = chronological.map { it.id },
            estimatedTokens = usedTokens,
            omittedOlderMessageCount = omitted,
        )
    }

    private data class PromptMessage(
        val id: String,
        val text: String,
    )

    private fun MessageEntity.toPromptMessage(): PromptMessage {
        val roleLabel = when (role) {
            MessageRole.USER -> "User"
            MessageRole.ASSISTANT -> "Aura"
            MessageRole.SYSTEM -> "System"
        }
        val imageNote = if (imageBase64 != null) " [image attached]" else ""
        val normalizedContent = content
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "(empty message)" }
        return PromptMessage(
            id = id,
            text = "$roleLabel$imageNote: $normalizedContent",
        )
    }

    private companion object {
        const val DEFAULT_RAW_TOKEN_BUDGET = 20_000
        const val DEFAULT_CANDIDATE_LIMIT = 120
        const val CHARS_PER_TOKEN_ESTIMATE = 3
    }
}

private fun estimateTokens(text: String): Int =
    ((text.length + 2) / 3).coerceAtLeast(1)

private fun String.truncateToTokenBudget(tokenBudget: Int): String {
    val maxChars = (tokenBudget * 3).coerceAtLeast(1)
    if (length <= maxChars) return this
    return takeLast(maxChars).trimStart().let { "...$it" }
}
