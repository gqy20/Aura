package com.xiaoqi.companion.feature.chat

import com.xiaoqi.companion.core.companion.model.ToolCallStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageReconcilerTest {
    @Test
    fun newPersistedReplyTakesOverStreamingTailWithoutCreatingSecondBubble() {
        val streaming = ChatMessage(
            id = "streaming",
            role = "ASSISTANT",
            content = "这是尚未刷完的回",
            timestamp = 1_000L,
            isStreaming = true,
            toolStatus = "已查询地图",
            toolStatusType = ToolCallStatus.SUCCEEDED,
            toolCallIds = listOf("call-1"),
        )
        val persisted = ChatMessage(
            id = "persisted",
            role = "ASSISTANT",
            content = "这是尚未刷完的回答。",
            timestamp = 2_000L,
        )

        val merged = mergePersistedMessagesDuringStreaming(
            dbMessages = listOf(
                ChatMessage("user", "USER", "问题", timestamp = 900L),
                persisted,
            ),
            streamingTail = streaming,
        )

        val assistants = merged.filter { it.role == "ASSISTANT" }
        assertEquals(1, assistants.size)
        assertEquals("persisted", assistants.single().id)
        assertEquals("这是尚未刷完的回答。", assistants.single().content)
        assertTrue(assistants.single().isStreaming)
        assertEquals(listOf("call-1"), assistants.single().toolCallIds)
    }

    @Test
    fun olderIdenticalReplyDoesNotReplaceCurrentStreamingTail() {
        val oldReply = ChatMessage(
            id = "old",
            role = "ASSISTANT",
            content = "相同回答",
            timestamp = 500L,
        )
        val streaming = ChatMessage(
            id = "streaming",
            role = "ASSISTANT",
            content = "相同回答",
            timestamp = 1_000L,
            isStreaming = true,
        )

        val merged = mergePersistedMessagesDuringStreaming(
            dbMessages = listOf(oldReply),
            streamingTail = streaming,
        )

        assertEquals(listOf("old", "streaming"), merged.map { it.id })
    }
}
