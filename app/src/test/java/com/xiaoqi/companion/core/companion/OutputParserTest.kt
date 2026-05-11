package com.xiaoqi.companion.core.companion

import com.xiaoqi.companion.core.companion.model.AgentError
import com.xiaoqi.companion.core.companion.model.AgentEvent
import com.xiaoqi.companion.core.companion.model.EmotionSignal
import com.xiaoqi.companion.core.companion.model.InteractionSignal
import com.xiaoqi.companion.core.companion.model.ParsedOutput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputParserTest {

    private val parser = OutputParser()

    // --- Plain text reply ---

    @Test
    fun parse_plainText_returnsTextReply() {
        val result = parser.parse("你好呀！今天天气真不错。")
        assertEquals("你好呀！今天天气真不错。", result.textReply)
    }

    @Test
    fun parse_emptyText_returnsEmptyOutput() {
        val result = parser.parse("")
        assertEquals("", result.textReply)
    }

    // --- Emotion extraction ---

    @Test
    fun parse_extractsMoodFromContent() {
        val result = parser.parse("[mood:happy][intensity:0.8] 哈哈太好玩了！")
        assertEquals("哈哈太好玩了！", result.textReply)
        assertEquals("happy", result.emotionSignal.mood)
        assertEquals(0.8f, result.emotionSignal.intensity, 0.001f)
    }

    @Test
    fun parse_noEmotionTags_returnsDefaultEmotion() {
        val result = parser.parse("普通回复")
        assertEquals("neutral", result.emotionSignal.mood)
        assertEquals(0.5f, result.emotionSignal.intensity, 0.001f)
    }

    // --- Interaction signal ---

    @Test
    fun parse_extractsAffinityDelta() {
        val result = parser.parse("[affinity:+0.05] 谢谢你的分享！")
        assertEquals(0.05f, result.interactionSignal.affinityDelta, 0.001f)
    }

    @Test
    fun parse_negativeAffinity() {
        val result = parser.parse("[affinity:-0.02] 嗯...这个我不太确定。")
        assertEquals(-0.02f, result.interactionSignal.affinityDelta, 0.001f)
    }

    // --- Topic tags ---

    @Test
    fun parse_extractsTopicTags() {
        val result = parser.parse("[topics:greeting,casual] 嘿，好久不见！")
        assertEquals(listOf("greeting", "casual"), result.interactionSignal.topicTags)
    }

    // --- Combined tags ---

    @Test
    fun parse_combinedTags_allExtracted() {
        val input = "[mood:excited][intensity:0.9][affinity:+0.03][topics:celebration,joy] 太棒了恭喜你！"
        val result = parser.parse(input)

        assertEquals("太棒了恭喜你！", result.textReply)
        assertEquals("excited", result.emotionSignal.mood)
        assertEquals(0.9f, result.emotionSignal.intensity, 0.001f)
        assertEquals(0.03f, result.interactionSignal.affinityDelta, 0.001f)
        assertEquals(listOf("celebration", "joy"), result.interactionSignal.topicTags)
    }

    // --- Action parsing ---

    @Test
    fun parse_actionTag_createsAction() {
        val result = parser.parse("[action:send_notification][text:想你了] 我也想你！")
        assertEquals(1, result.actions.size)
        assertEquals("send_notification", result.actions[0].type)
        assertEquals("想你了", result.actions[0].params["text"])
    }

    // --- Error handling ---

    @Test
    fun parse_nullInput_returnsEmpty() {
        val result = parser.parse(null)
        assertEquals("", result.textReply)
    }
}
