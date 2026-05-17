package com.xiaoqi.companion.core.tools

import com.xiaoqi.companion.data.db.dao.MessageDao
import com.xiaoqi.companion.data.db.dao.MessageInteractionSummary
import io.mockk.coEvery
import io.mockk.mockk
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetRecentInteractionContextToolTest {

    private val messageDao: MessageDao = mockk()

    @Test
    fun execute_returnsRecentInteractionSummary() = runTest {
        coEvery { messageDao.getInteractionSummary("default", any()) } returns MessageInteractionSummary(
            messageCount = 2,
            userMessageCount = 1,
            assistantMessageCount = 1,
            messagesToday = 2,
            userMessagesToday = 1,
            assistantMessagesToday = 1,
            lastMessageRole = "ASSISTANT",
            lastMessageAt = 1_699_999_700_000L,
            lastUserMessageAt = 1_699_999_400_000L,
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
        coEvery { messageDao.getInteractionSummary("default", any()) } returns MessageInteractionSummary(
            messageCount = 0,
            userMessageCount = 0,
            assistantMessageCount = 0,
            messagesToday = 0,
            userMessagesToday = 0,
            assistantMessagesToday = 0,
            lastMessageRole = null,
            lastMessageAt = 0L,
            lastUserMessageAt = 0L,
        )
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
}
