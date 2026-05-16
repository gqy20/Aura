package com.xiaoqi.companion.feature.chat

import org.junit.Assert.assertEquals
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
}
