package com.xiaoqi.companion.core.companion

import com.xiaoqi.companion.core.companion.model.UserInput
import com.xiaoqi.companion.core.prompt.PromptBuilder
import com.xiaoqi.companion.core.prompt.PromptConfig
import com.xiaoqi.companion.core.prompt.templates.SystemPersona
import com.xiaoqi.companion.data.db.converter.MessageRole
import com.xiaoqi.companion.data.db.entity.MessageEntity
import com.xiaoqi.companion.data.repository.MessageRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConversationContextManualScenariosTest {

    private val messageRepository: MessageRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        SystemPersona.initForTesting(
            PromptConfig(
                name = "Aura",
                base = "You are {{name}}.\n",
                sections = mapOf(
                    "emotion" to PromptConfig.SectionTemplate("Emotion", "{{emotion_context}}"),
                    "relationship" to PromptConfig.SectionTemplate("Relationship", "{{relationship_context}}"),
                    "memory" to PromptConfig.SectionTemplate("Memory", "{{memories}}"),
                    "tools" to PromptConfig.SectionTemplate("Tools", "Use tools when helpful."),
                ),
            )
        )
    }

    @Test
    fun followUpQuestionKeepsRecentConversationForReference() = runTest {
        coEvery { messageRepository.getRecentMessages("default", 20) } returns listOf(
            message("m4", MessageRole.USER, "所以这个 20K 是按 token 预算，不是消息数量，对吗？", 4_000L),
            message("m3", MessageRole.ASSISTANT, "对，最近原文窗口应该按预算裁剪。", 3_000L),
            message("m2", MessageRole.USER, "最近消息是否可以内容达到一定程度再压缩？", 2_000L),
            message("m1", MessageRole.ASSISTANT, "可以，按 token/字符预算更合理。", 1_000L),
        )

        val context = ConversationContextBuilder(messageRepository, rawTokenBudget = 200, candidateLimit = 20)
            .build("default")
        val prompt = PromptBuilder().build(
            input = UserInput.Text("那摘要器现在是怎么做的？"),
            recentConversation = context.recentMessages,
            summaries = listOf("Session: We are discussing memory context design."),
            memories = listOf("User is building Aura, an Android AI companion app."),
        )

        assertEquals(
            listOf(
                "Aura: 可以，按 token/字符预算更合理。",
                "User: 最近消息是否可以内容达到一定程度再压缩？",
                "Aura: 对，最近原文窗口应该按预算裁剪。",
                "User: 所以这个 20K 是按 token 预算，不是消息数量，对吗？",
            ),
            context.recentMessages,
        )
        assertTrue(prompt.systemPrompt.contains("## 会话摘要"))
        assertTrue(prompt.systemPrompt.contains("## 最近对话"))
        assertTrue(prompt.systemPrompt.contains("## Memory"))
    }

    @Test
    fun longEarlierContentIsDroppedBeforeLatestMessages() = runTest {
        coEvery { messageRepository.getRecentMessages("default", 20) } returns listOf(
            message("m4", MessageRole.USER, "继续刚才的实现。", 4_000L),
            message("m3", MessageRole.ASSISTANT, "我会把短期上下文接入 runtime。", 3_000L),
            message("m2", MessageRole.USER, "older ".repeat(300), 2_000L),
            message("m1", MessageRole.ASSISTANT, "最早的一条应该被省略。", 1_000L),
        )

        val context = ConversationContextBuilder(messageRepository, rawTokenBudget = 35, candidateLimit = 20)
            .build("default")

        assertEquals(
            listOf(
                "Aura: 我会把短期上下文接入 runtime。",
                "User: 继续刚才的实现。",
            ),
            context.recentMessages,
        )
        assertEquals(2, context.omittedOlderMessageCount)
        assertFalse(context.recentMessages.joinToString("\n").contains("older older"))
    }

    @Test
    fun imageMessagesAreRepresentedWithoutEmbeddingImagePayloadAgain() = runTest {
        coEvery { messageRepository.getRecentMessages("default", 10) } returns listOf(
            message(
                id = "m2",
                role = MessageRole.USER,
                content = "Shared a picture",
                timestamp = 2_000L,
                imageBase64 = "base64-payload",
            ),
            message("m1", MessageRole.ASSISTANT, "我看到了一张桌面照片。", 1_000L),
        )

        val context = ConversationContextBuilder(messageRepository, rawTokenBudget = 100, candidateLimit = 10)
            .build("default")

        assertEquals(
            listOf(
                "Aura: 我看到了一张桌面照片。",
                "User [image attached]: Shared a picture",
            ),
            context.recentMessages,
        )
        assertFalse(context.recentMessages.joinToString("\n").contains("base64-payload"))
    }

    private fun message(
        id: String,
        role: MessageRole,
        content: String,
        timestamp: Long,
        imageBase64: String? = null,
    ): MessageEntity =
        MessageEntity(
            id = id,
            sessionId = "default",
            role = role,
            content = content,
            imageBase64 = imageBase64,
            timestamp = timestamp,
        )

}
