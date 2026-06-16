package com.xiaoqi.companion.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingMarkdownChunkerTest {

    @Test
    fun append_commitsParagraphOnlyAfterBlankLine() {
        val chunker = StreamingMarkdownChunker()

        val partial = chunker.append("First paragraph")
        assertTrue(partial.committedBlocks.isEmpty())
        assertEquals("First paragraph", partial.draftText)

        val committed = chunker.append("\n\nSecond")
        assertEquals(listOf(MessageRenderBlock.Text("First paragraph")), committed.committedBlocks)
        assertEquals("Second", committed.draftText)
    }

    @Test
    fun append_keepsUnclosedCodeBlockAsDraft() {
        val chunker = StreamingMarkdownChunker()

        val state = chunker.append("Before\n\n```kotlin\nval aura = true")

        assertEquals(listOf(MessageRenderBlock.Text("Before")), state.committedBlocks)
        assertEquals("val aura = true", state.draftText)
        assertTrue(state.isDraftCode)
    }

    @Test
    fun append_commitsCodeBlockAfterClosingFence() {
        val chunker = StreamingMarkdownChunker()

        val state = chunker.append("```kotlin\nval aura = true\n```\n\nAfter")

        assertEquals(listOf(MessageRenderBlock.Code("val aura = true")), state.committedBlocks)
        assertEquals("After", state.draftText)
    }

    @Test
    fun complete_reconcilesPendingDraftIntoBlocks() {
        val chunker = StreamingMarkdownChunker()
        chunker.append("Intro\n\n```kotlin\nval aura = true")

        val blocks = chunker.complete("Intro\n\n```kotlin\nval aura = true\n```\n\nDone")

        assertEquals(
            listOf(
                MessageRenderBlock.Text("Intro"),
                MessageRenderBlock.Code("val aura = true"),
                MessageRenderBlock.Text("Done"),
            ),
            blocks,
        )
    }

    // P2: append-only tail 解析优化——state 实例缓存

    @Test
    fun append_empty_returnsCachedState() {
        // 空 delta 不应让 state 变化——返回同一 instance 让上游 ChatMessage.copy()
        // 引用等、跳过 Compose 重组。
        val chunker = StreamingMarkdownChunker()
        val first = chunker.append("hi")
        val second = chunker.append("")
        val third = chunker.append("")

        assertSame(first, second)
        assertSame(second, third)
    }

    @Test
    fun append_consecutiveQueriesBeforeAnyCommit_returnCachedState() {
        // 流式期间 rawText/draftText 持续变化 → cachedState 必然失效。
        // 但每次 commit 后,短时间内多次 state() 调用应复用 toList() 的 list 引用。
        val chunker = StreamingMarkdownChunker()
        chunker.append("a")
        chunker.append("b")
        val beforeCommit = chunker.stateForTest()

        // 触发 commit(双换行)
        chunker.append("\n\n")
        val afterCommit1 = chunker.stateForTest()
        val afterCommit2 = chunker.stateForTest()

        // 连续两次 state() 在 commit 之后、rawText/draftText 不变时,引用应稳定
        assertSame(afterCommit1, afterCommit2)
        // 引用稳定后 committedBlocks 列表也应引用稳定
        assertSame(afterCommit1.committedBlocks, afterCommit2.committedBlocks)
        // 不变 first state 是空 list,不应被引用到 commit 后的 state
        assertNotSame(beforeCommit, afterCommit1)
    }

    @Test
    fun append_afterTextAppendInvalidatesCache() {
        // 新字符 append 后 rawText/draftText 变化,state 应是不同 instance
        val chunker = StreamingMarkdownChunker()
        val first = chunker.append("a")
        val second = chunker.append("b")
        assertNotSame(first, second)
    }
}
