package com.xiaoqi.companion.feature.chat

import com.xiaoqi.companion.core.companion.model.ToolCallStatus
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

    private fun toolCall(id: String) = ChatToolCall(
        id = id,
        toolName = "search_memory",
        toolStatus = ToolCallStatus.SUCCEEDED,
        label = "搜索记忆",
        status = "已完成",
    )
}
