package com.xiaoqi.companion.core.prompt

import com.xiaoqi.companion.core.companion.model.UserInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {

    private val builder = PromptBuilder()

    // --- System prompt includes persona ---

    @Test
    fun build_containsSystemPersona() {
        val result = builder.build(UserInput.Text("hello"))
        assertTrue("Should contain companion name", result.systemPrompt.contains("Companion"))
    }

    @Test
    fun build_containsOutputFormatInstructions() {
        val result = builder.build(UserInput.Text("hi"))
        assertTrue(result.systemPrompt.contains("[mood:") || result.systemPrompt.contains("format"))
    }

    // --- Emotion context injection ---

    @Test
    fun build_withEmotionContext_injectsMood() {
        val result = builder.build(
            input = UserInput.Text("test"),
            emotionContext = "当前情绪：开心，强度0.8",
        )
        assertTrue(result.systemPrompt.contains("开心"))
    }

    @Test
    fun build_withoutEmotion_omitsSection() {
        val result = builder.build(
            input = UserInput.Text("test"),
            emotionContext = null,
        )
        assertFalse(result.systemPrompt.contains("## 当前情绪状态"))
    }

    // --- Relationship context injection ---

    @Test
    fun build_withRelationship_injectsLevel() {
        val result = builder.build(
            input = UserInput.Text("test"),
            relationshipContext = "关系等级：亲密(0.85)，可以称呼昵称",
        )
        assertTrue(result.systemPrompt.contains("0.85") || result.systemPrompt.contains("亲密"))
    }

    // --- Memory injection ---

    @Test
    fun build_withMemories_injectsMemoryBlock() {
        val memories = listOf(
            "- 用户喜欢猫，养了一只叫咪咪的橘猫 (重要性: 0.9)",
            "- 上周用户通过了重要考试 (重要性: 0.7)",
        )
        val result = builder.build(
            input = UserInput.Text("test"),
            memories = memories,
        )
        assertTrue(result.systemPrompt.contains("咪咪"))
        assertTrue(result.systemPrompt.contains("考试"))
    }

    @Test
    fun build_emptyMemories_omitsSection() {
        val result = builder.build(
            input = UserInput.Text("test"),
            memories = emptyList(),
        )
        assertFalse(result.systemPrompt.contains("相关记忆"))
    }

    // --- User message handling ---

    @Test
    fun build_textInput_setsUserMessage() {
        val result = builder.build(UserInput.Text("你好呀"))
        assertEquals("你好呀", result.userMessage)
    }

    @Test
    fun build_visionInput_includesImageNote() {
        val result = builder.build(
            UserInput.Vision("看我的穿搭", "base64data", "image/jpeg")
        )
        assertTrue(result.userMessage.contains("看我的穿搭"))
        assertTrue(result.hasImage)
    }

    @Test
    fun build_speechInput_setsTranscriptAsMessage() {
        val result = builder.build(UserInput.Speech("今天天气真好"))
        assertEquals("今天天气真好", result.userMessage)
    }

    // --- Full assembly ---

    @Test
    fun build_fullAssembly_allSectionsPresent() {
        val result = builder.build(
            input = UserInput.Text("我想你了"),
            emotionContext = "情绪：思念(intensity 0.7)",
            relationshipContext = "关系：好友(0.6)",
            memories = listOf("- 用户昨天说想念我"),
        )
        assertTrue(result.systemPrompt.contains("Companion"))
        assertTrue(result.systemPrompt.contains("思念"))
        assertTrue(result.systemPrompt.contains("好友"))
        assertTrue(result.systemPrompt.contains("用户昨天说想念我"))
        assertEquals("我想你了", result.userMessage)
    }
}
