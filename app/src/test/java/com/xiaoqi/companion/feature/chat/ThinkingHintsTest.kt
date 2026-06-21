package com.xiaoqi.companion.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkingHintsTest {

    @Test
    fun stageIndex_0_atStart() {
        assertEquals(0, ThinkingHints.stageIndexFor(0L))
        assertEquals(0, ThinkingHints.stageIndexFor(1_999L))
    }

    @Test
    fun stageIndex_advancesAtEachThreshold() {
        assertEquals(1, ThinkingHints.stageIndexFor(2_000L))
        assertEquals(1, ThinkingHints.stageIndexFor(4_999L))
        assertEquals(2, ThinkingHints.stageIndexFor(5_000L))
        assertEquals(2, ThinkingHints.stageIndexFor(11_999L))
        assertEquals(3, ThinkingHints.stageIndexFor(12_000L))
        assertEquals(3, ThinkingHints.stageIndexFor(24_999L))
        assertEquals(4, ThinkingHints.stageIndexFor(25_000L))
        assertEquals(4, ThinkingHints.stageIndexFor(49_999L))
        assertEquals(5, ThinkingHints.stageIndexFor(50_000L))
        assertEquals(5, ThinkingHints.stageIndexFor(999_999L))
    }

    @Test
    fun hintFor_returnsStage0Hint_immediately() {
        val hint = ThinkingHints.hintFor(elapsedMs = 0L, indexInStage = 0)
        assertTrue("档0 应返回开场文案，实际: $hint", hint.isNotBlank())
        assertTrue(
            "档0 文案应来自开场池",
            hint in listOf("嗯，我在听…", "Aura 看到啦…", "让我想想…"),
        )
    }

    @Test
    fun hintFor_advancesToneAfter12s() {
        // >12s 后每条都应包含"还在 / 一直在 / 不敷衍 / 琢磨"等安抚信号
        val hint = ThinkingHints.hintFor(elapsedMs = 15_000L, indexInStage = 0)
        assertTrue(">12s 文案应承认在场，实际: $hint", hint.contains("还在") || hint.contains("一直在") || hint.contains("敷衍") || hint.contains("琢磨"))
    }

    @Test
    fun hintFor_errorStageStaysAfter50s() {
        val hint = ThinkingHints.hintFor(elapsedMs = 60_000L, indexInStage = 0)
        assertTrue(
            ">50s 应返回异常兜底文案，实际: $hint",
            hint in listOf("好像有点卡住了…", "再等等，或者重试也行…"),
        )
    }

    @Test
    fun hintFor_cyclesWithinStageWithoutImmediateRepeat() {
        // 同档内连续 index 不应立即重复（档0 有 3 条，前 3 个 index 应互不相同）
        val h0 = ThinkingHints.hintFor(elapsedMs = 1_000L, indexInStage = 0)
        val h1 = ThinkingHints.hintFor(elapsedMs = 1_000L, indexInStage = 1)
        val h2 = ThinkingHints.hintFor(elapsedMs = 1_000L, indexInStage = 2)
        assertNotEquals("index 0 vs 1 不应相同", h0, h1)
        assertNotEquals("index 1 vs 2 不应相同", h1, h2)
        assertNotEquals("index 0 vs 2 不应相同", h0, h2)
    }

    @Test
    fun hintFor_wrapsAroundAfterPoolSize() {
        // 档0 池长 3，index 3 应回到 index 0 的文案
        val h0 = ThinkingHints.hintFor(elapsedMs = 1_000L, indexInStage = 0)
        val h3 = ThinkingHints.hintFor(elapsedMs = 1_000L, indexInStage = 3)
        assertEquals("超出池长应取模回绕", h0, h3)
    }

    @Test
    fun hintFor_negativeIndexWrapsSafely() {
        // 防御性：负 index 也不应崩溃，取模后落到合法区间
        val hint = ThinkingHints.hintFor(elapsedMs = 1_000L, indexInStage = -1)
        assertTrue(hint.isNotBlank())
    }
}
