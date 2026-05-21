package com.xiaoqi.companion.core.companion

import com.xiaoqi.companion.data.db.converter.MessageRole
import com.xiaoqi.companion.data.db.entity.MessageEntity
import com.xiaoqi.companion.data.repository.MessageRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationContextBuilderTest {

    private val messageRepository: MessageRepository = mockk(relaxed = true)

    @Test
    fun build_keepsRecentMessagesWithinTokenBudgetInChronologicalOrder() = runTest {
        coEvery { messageRepository.getRecentMessages("default", 10) } returns listOf(
            message("m3", MessageRole.USER, "latest short message", timestamp = 3_000L),
            message("m2", MessageRole.ASSISTANT, "middle short reply", timestamp = 2_000L),
            message("m1", MessageRole.USER, "older message that should be omitted", timestamp = 1_000L),
        )
        val builder = ConversationContextBuilder(
            messageRepository = messageRepository,
            rawTokenBudget = 18,
            candidateLimit = 10,
        )

        val context = builder.build("default")

        assertEquals(
            listOf(
                "Aura: middle short reply",
                "User: latest short message",
            ),
            context.recentMessages,
        )
        assertEquals(listOf("m2", "m3"), context.recentMessageIds)
        assertEquals(1, context.omittedOlderMessageCount)
        assertTrue(context.estimatedTokens <= 18)
    }

    @Test
    fun build_truncatesSingleHugeLatestMessageToBudget() = runTest {
        coEvery { messageRepository.getRecentMessages("default", 5) } returns listOf(
            message("m2", MessageRole.USER, "x".repeat(90), timestamp = 2_000L),
            message("m1", MessageRole.ASSISTANT, "older", timestamp = 1_000L),
        )
        val builder = ConversationContextBuilder(
            messageRepository = messageRepository,
            rawTokenBudget = 10,
            candidateLimit = 5,
        )

        val context = builder.build("default")

        assertEquals(1, context.recentMessages.size)
        assertTrue(context.recentMessages.single().startsWith("..."))
        assertEquals(1, context.omittedOlderMessageCount)
        assertTrue(context.estimatedTokens <= 11)
    }

    private fun message(
        id: String,
        role: MessageRole,
        content: String,
        timestamp: Long,
    ): MessageEntity =
        MessageEntity(
            id = id,
            sessionId = "default",
            role = role,
            content = content,
            timestamp = timestamp,
        )
}
