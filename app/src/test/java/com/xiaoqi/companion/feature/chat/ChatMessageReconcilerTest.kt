package com.xiaoqi.companion.feature.chat

import com.xiaoqi.companion.core.companion.model.ToolCallStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        // 落库行接管内容,但保留 streamingTail 的 UI id 作 LazyColumn key,行 id 存 persistedId
        assertEquals("streaming", assistants.single().id)
        assertEquals("persisted", assistants.single().persistedId)
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

    @Test
    fun stabilize_preservesUiIdAndTransientFieldsByPersistedIdOrContent() {
        val previous = listOf(
            ChatMessage("t-user", "USER", "问题"),
            ChatMessage(
                id = "t-1",
                role = "ASSISTANT",
                content = "回答",
                persistedId = "db-1",
                intentText = "我先想想",
                toolSteps = listOf(
                    ChatToolStep(
                        id = "s1",
                        name = "search_memory",
                        callId = "c1",
                        label = "搜索记忆",
                        status = ToolCallStatus.SUCCEEDED,
                        startedAtMs = 0L,
                    )
                ),
                performanceInfo = PerformanceInfo(durationMs = 1_000L, estimatedTokens = 10),
            ),
        )
        val db = listOf(
            ChatMessage("db-user", "USER", "问题"),
            ChatMessage("db-1", "ASSISTANT", "回答"),
        )

        val stabilized = stabilizePersistedMessages(db, previous)

        // USER 靠 content 对齐,ASSISTANT 靠 persistedId 对齐;UI id 均延续
        assertEquals(listOf("t-user", "t-1"), stabilized.map { it.id })
        assertEquals("db-user", stabilized[0].persistedId)
        assertEquals("db-1", stabilized[1].persistedId)
        assertEquals("我先想想", stabilized[1].intentText)
        assertEquals(previous[1].toolSteps, stabilized[1].toolSteps)
        assertNotNull(stabilized[1].performanceInfo)
    }

    @Test
    fun stabilize_duplicateContentMessagesDoNotShareUiId() {
        // 内容相同的两条 DB 消息只能有一条继承旧 id,候选消费后第二条保持 DB id
        val previous = listOf(ChatMessage("t-1", "USER", "嗯"))
        val db = listOf(
            ChatMessage("db-1", "USER", "嗯"),
            ChatMessage("db-2", "USER", "嗯"),
        )

        val stabilized = stabilizePersistedMessages(db, previous)

        assertEquals(listOf("t-1", "db-2"), stabilized.map { it.id })
    }

    @Test
    fun stabilize_unknownMessagesKeepDbIdWithoutTransientFields() {
        val stabilized = stabilizePersistedMessages(
            dbMessages = listOf(ChatMessage("db-9", "ASSISTANT", "新消息")),
            previous = emptyList(),
        )

        assertEquals("db-9", stabilized.single().id)
        assertNull(stabilized.single().performanceInfo)
        assertNull(stabilized.single().persistedId)
    }
}
