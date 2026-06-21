package com.xiaoqi.companion.core.tools

import com.xiaoqi.companion.data.datastore.AppPreferences
import com.xiaoqi.companion.data.db.converter.MessageRole
import com.xiaoqi.companion.data.db.dao.MessageDao
import com.xiaoqi.companion.data.db.dao.MessageSearchDao
import com.xiaoqi.companion.data.db.dao.MessageSearchHit
import com.xiaoqi.companion.data.db.entity.MessageEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRecordsToolTest {

    private val messageDao: MessageDao = mockk()
    private val messageSearchDao: MessageSearchDao = mockk()
    private val appPreferences: AppPreferences = mockk {
        every { currentSessionId } returns flowOf("default")
    }

    @Test
    fun execute_returnsRawRecordHitsWithContext() = runTest {
        val hit = message(id = "hit", content = "We should split raw records and summaries", timestamp = 2_000L)
        coEvery {
            messageSearchDao.searchRecordsFts("default", "summaries", null, null, null, null, any())
        } returns listOf(hit.toSearchHit())
        coEvery { messageDao.getMessagesBefore("default", 2_000L, 1) } returns listOf(
            message(id = "before", role = MessageRole.ASSISTANT, content = "Earlier context", timestamp = 1_000L)
        )
        coEvery { messageDao.getMessagesAfter("default", 2_000L, 1) } returns listOf(
            message(id = "after", role = MessageRole.ASSISTANT, content = "Later context", timestamp = 3_000L)
        )

        val result = SearchRecordsTool(messageDao, messageSearchDao, appPreferences).execute(SearchRecordsTool.Args(query = "summaries"))

        assertTrue(result.contains("\"count\":1"))
        assertTrue(result.contains("raw records and summaries"))
        assertTrue(result.contains("Earlier context"))
        assertTrue(result.contains("Later context"))
    }

    @Test
    fun execute_filtersRoleAndImageWithoutReturningBase64() = runTest {
        coEvery {
            messageSearchDao.searchRecordsFts(
                sessionId = "default",
                matchQuery = "photo",
                role = MessageRole.USER,
                after = null,
                before = null,
                hasImage = true,
                limit = any(),
            )
        } returns listOf(message(id = "image", content = "photo note", imageBase64 = "very-large-base64").toSearchHit())
        coEvery { messageDao.getMessagesBefore("default", any(), any()) } returns emptyList()
        coEvery { messageDao.getMessagesAfter("default", any(), any()) } returns emptyList()

        val result = SearchRecordsTool(messageDao, messageSearchDao, appPreferences).execute(
            SearchRecordsTool.Args(query = "photo", role = "USER", hasImage = true)
        )

        assertTrue(result.contains("\"hasImage\":true"))
        assertFalse(result.contains("very-large-base64"))
        coVerify {
            messageSearchDao.searchRecordsFts(
                sessionId = "default",
                matchQuery = "photo",
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
        val tool = SearchRecordsTool(mockk(), mockk(), appPreferences)

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

private fun MessageEntity.toSearchHit(ftsRank: Double = -1.0): MessageSearchHit =
    MessageSearchHit(
        id = id,
        sessionId = sessionId,
        role = role,
        content = content,
        imageBase64 = imageBase64,
        timestamp = timestamp,
        ftsRank = ftsRank,
    )

// --- envelope 行为 ---

class SearchRecordsToolEnvelopeTest {

    private val messageDao: MessageDao = mockk()
    private val messageSearchDao: MessageSearchDao = mockk()
    private val appPreferences: AppPreferences = mockk {
        every { currentSessionId } returns flowOf("default")
    }

    @Test
    fun execute_ftsFailure_returnsEnvelopeErrorInsteadOfEmptyList() = runTest {
        coEvery { messageSearchDao.searchRecordsFts(any(), any(), any(), any(), any(), any(), any()) } throws
            RuntimeException("no such column: rowid")

        val result = SearchRecordsTool(messageDao, messageSearchDao, appPreferences)
            .execute(SearchRecordsTool.Args(query = "jasmine"))

        // 之前:静默返空 list;现在:envelope error,告诉 LLM 检索失败 + 怎么兜底
        assertTrue("result should be envelope error but was: $result", isError(result))
        assertTrue(result.contains("\"reason\":\"fts_index_failure\""))
        assertTrue(result.contains("\"hint\""))
        // 不应再 fallback 到 LIKE 路径 —— context 调用不应该发生
        coVerify(exactly = 0) { messageDao.getMessagesBefore(any(), any(), any()) }
    }

    @Test
    fun execute_invalidRole_returnsEnvelopeErrorWithAllowedRoles() = runTest {
        val result = SearchRecordsTool(messageDao, messageSearchDao, appPreferences)
            .execute(SearchRecordsTool.Args(query = "x", role = "WIZARD"))

        assertTrue(isError(result))
        assertTrue(result.contains("\"reason\":\"invalid_message_role\""))
        assertTrue(result.contains("USER"))
        assertTrue(result.contains("ASSISTANT"))
    }
}
