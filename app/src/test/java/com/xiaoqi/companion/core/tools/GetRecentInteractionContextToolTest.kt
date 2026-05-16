package com.xiaoqi.companion.core.tools

import com.xiaoqi.companion.data.db.converter.MessageRole
import com.xiaoqi.companion.data.db.dao.MessageDao
import com.xiaoqi.companion.data.db.entity.MessageEntity
import io.mockk.coEvery
import io.mockk.mockk
import java.time.ZoneId
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetRecentInteractionContextToolTest {

    private val messageDao: MessageDao = mockk()

    @Test
    fun execute_returnsRecentInteractionSummary() = runTest {
        coEvery { messageDao.observeBySession("default") } returns flowOf(
            listOf(
                message(id = "m1", role = MessageRole.USER, timestamp = 1_699_999_400_000L),
                message(id = "m2", role = MessageRole.ASSISTANT, timestamp = 1_699_999_700_000L),
            )
        )
        val tool = GetRecentInteractionContextTool(
            messageDao = messageDao,
            nowProvider = { 1_700_000_000_000L },
            zoneProvider = { ZoneId.of("Asia/Shanghai") },
        )

        val result = tool.execute(GetRecentInteractionContextTool.Args())

        assertTrue(result.contains(""""messageCount":2"""))
        assertTrue(result.contains(""""userMessageCount":1"""))
        assertTrue(result.contains(""""assistantMessageCount":1"""))
        assertTrue(result.contains(""""hasPreviousInteraction":true"""))
        assertTrue(result.contains(""""lastMessageRole":"ASSISTANT""""))
        assertTrue(result.contains(""""minutesSinceLastMessage":5"""))
        assertTrue(result.contains(""""minutesSinceLastUserMessage":10"""))
    }

    @Test
    fun execute_handlesEmptySession() = runTest {
        coEvery { messageDao.observeBySession("default") } returns flowOf(emptyList())
        val tool = GetRecentInteractionContextTool(
            messageDao = messageDao,
            nowProvider = { 1_700_000_000_000L },
            zoneProvider = { ZoneId.of("Asia/Shanghai") },
        )

        val result = tool.execute(GetRecentInteractionContextTool.Args())

        assertTrue(result.contains(""""messageCount":0"""))
        assertTrue(result.contains(""""hasPreviousInteraction":false"""))
        assertTrue(result.contains(""""minutesSinceLastMessage":-1"""))
        assertTrue(result.contains(""""minutesSinceLastUserMessage":-1"""))
    }

    @Test
    fun descriptor_exposesKoogToolMetadata() {
        val tool = GetRecentInteractionContextTool(mockk())

        assertEquals("get_recent_interaction_context", tool.name)
        assertTrue(tool.descriptor.description.contains("recent", ignoreCase = true))
    }

    private fun message(
        id: String,
        role: MessageRole,
        timestamp: Long,
    ) = MessageEntity(
        id = id,
        sessionId = "default",
        role = role,
        content = "hello",
        timestamp = timestamp,
    )
}
