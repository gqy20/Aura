package com.xiaoqi.companion.core.tools

import com.xiaoqi.companion.data.db.converter.MessageRole
import com.xiaoqi.companion.data.db.dao.MessageDao
import com.xiaoqi.companion.data.db.entity.MessageEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRecordsToolTest {

    private val messageDao: MessageDao = mockk()

    @Test
    fun execute_returnsRawRecordHitsWithContext() = runTest {
        val hit = message(id = "hit", content = "We should split raw records and summaries", timestamp = 2_000L)
        coEvery {
            messageDao.searchRecords("default", "%summaries%", null, null, null, null, any())
        } returns listOf(hit)
        coEvery { messageDao.getMessagesBefore("default", 2_000L, 1) } returns listOf(
            message(id = "before", role = MessageRole.ASSISTANT, content = "Earlier context", timestamp = 1_000L)
        )
        coEvery { messageDao.getMessagesAfter("default", 2_000L, 1) } returns listOf(
            message(id = "after", role = MessageRole.ASSISTANT, content = "Later context", timestamp = 3_000L)
        )

        val result = SearchRecordsTool(messageDao).execute(SearchRecordsTool.Args(query = "summaries"))

        assertTrue(result.contains("\"count\":1"))
        assertTrue(result.contains("raw records and summaries"))
        assertTrue(result.contains("Earlier context"))
        assertTrue(result.contains("Later context"))
    }

    @Test
    fun execute_filtersRoleAndImageWithoutReturningBase64() = runTest {
        coEvery {
            messageDao.searchRecords(
                sessionId = "default",
                pattern = "%photo%",
                role = MessageRole.USER,
                after = null,
                before = null,
                hasImage = true,
                limit = any(),
            )
        } returns listOf(message(id = "image", content = "photo note", imageBase64 = "very-large-base64"))
        coEvery { messageDao.getMessagesBefore("default", any(), any()) } returns emptyList()
        coEvery { messageDao.getMessagesAfter("default", any(), any()) } returns emptyList()

        val result = SearchRecordsTool(messageDao).execute(
            SearchRecordsTool.Args(query = "photo", role = "USER", hasImage = true)
        )

        assertTrue(result.contains("\"hasImage\":true"))
        assertFalse(result.contains("very-large-base64"))
        coVerify {
            messageDao.searchRecords(
                sessionId = "default",
                pattern = "%photo%",
                role = MessageRole.USER,
                after = null,
                before = null,
                hasImage = true,
                limit = any(),
            )
        }
    }

    @Test
    fun descriptor_exposesKoogToolMetadata() {
        val tool = SearchRecordsTool(mockk())

        assertEquals("search_records", tool.name)
        assertTrue(tool.descriptor.description.contains("raw", ignoreCase = true))
    }

    private fun message(
        id: String,
        sessionId: String = "default",
        role: MessageRole = MessageRole.USER,
        content: String,
        imageBase64: String? = null,
        timestamp: Long = 1_000L,
    ): MessageEntity =
        MessageEntity(
            id = id,
            sessionId = sessionId,
            role = role,
            content = content,
            imageBase64 = imageBase64,
            timestamp = timestamp,
            metadata = null,
        )
}
