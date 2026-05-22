package com.xiaoqi.companion.core.tools

import com.xiaoqi.companion.data.db.converter.SummaryType
import com.xiaoqi.companion.data.db.dao.MemorySummaryDao
import com.xiaoqi.companion.data.db.entity.MemorySummaryEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryToolsTest {

    private val summaryDao: MemorySummaryDao = mockk(relaxed = true)

    @Test
    fun searchSummaries_returnsMatchingSummaries() = runTest {
        coEvery {
            summaryDao.searchByText("%records%", null, any())
        } returns listOf(
            MemorySummaryEntity(
                id = "s1",
                type = SummaryType.TOPIC,
                title = "Record and summary split",
                summary = "Use raw records for evidence and summaries for condensed understanding.",
                keywords = """["records","summaries"]""",
                sourceMessageIds = """["m1"]""",
                importance = 0.9f,
                createdAt = 1_000L,
                updatedAt = 1_000L,
                lastAccessed = 1_000L,
            )
        )

        val result = SearchSummariesTool(summaryDao).execute(SearchSummariesTool.Args(query = "records"))

        assertTrue(result.contains("\"count\":1"))
        assertTrue(result.contains("Record and summary split"))
        coVerify { summaryDao.updateLastAccessed("s1", any()) }
    }

    @Test
    fun descriptors_exposeToolNames() {
        assertEquals("search_summaries", SearchSummariesTool(mockk()).name)
    }
}
