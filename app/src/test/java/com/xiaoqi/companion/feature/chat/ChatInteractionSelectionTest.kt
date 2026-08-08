package com.xiaoqi.companion.feature.chat

import com.xiaoqi.companion.core.companion.model.ToolCallStatus
import com.xiaoqi.companion.feature.chat.map.MapCoordinate
import com.xiaoqi.companion.feature.chat.map.MapToolInteraction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatInteractionSelectionTest {

    @Test
    fun findToolCallForMessage_selectsCallOwnedByMessage() {
        val unrelated = toolCall(id = "tool-unrelated")
        val owned = toolCall(id = "tool-owned")
        val message = ChatMessage(
            id = "assistant-1",
            role = "ASSISTANT",
            content = "done",
            toolStatus = "已完成",
            toolCallIds = listOf(owned.id),
        )

        assertEquals(owned, findToolCallForMessage(message, listOf(unrelated, owned)))
    }

    @Test
    fun findToolCallForMessage_withoutToolReference_returnsNull() {
        val message = ChatMessage(
            id = "assistant-1",
            role = "ASSISTANT",
            content = "done",
        )

        assertNull(findToolCallForMessage(message, listOf(toolCall(id = "tool-1"))))
    }

    @Test
    fun findToolCallForMessage_restoresRecentMapCallForPersistedMessage() {
        val completedAt = 1_000_000L
        val mapCall = toolCall(id = "map-tool").copy(
            completedAt = completedAt,
            mapInteraction = MapToolInteraction.Place(
                name = "West Lake",
                address = "Hangzhou",
                coordinate = MapCoordinate(120.13, 30.25),
            ),
        )
        val message = ChatMessage(
            id = "assistant-map",
            role = "ASSISTANT",
            content = "route ready",
            timestamp = completedAt + 10_000L,
        )

        assertEquals(mapCall, findToolCallForMessage(message, listOf(mapCall)))
    }

    private fun toolCall(id: String) = ChatToolCall(
        id = id,
        toolName = "search_memory",
        toolStatus = ToolCallStatus.SUCCEEDED,
        label = "搜索记忆",
        status = "已完成",
    )
}
