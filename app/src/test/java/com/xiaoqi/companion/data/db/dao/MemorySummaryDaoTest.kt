package com.xiaoqi.companion.data.db.dao

import com.xiaoqi.companion.data.db.BaseDaoTest
import com.xiaoqi.companion.data.db.converter.SummaryType
import com.xiaoqi.companion.data.db.entity.MemorySummaryEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MemorySummaryDaoTest : BaseDaoTest() {

    private lateinit var dao: MemorySummaryDao

    override fun initDaos() {
        dao = db.memorySummaryDao()
    }

    @Test
    fun searchByText_findsSummariesByTitleBodyOrKeywords() = runTest {
        dao.insert(summary(id = "project", type = SummaryType.PROJECT, title = "Aura MCP work", keywords = """["mcp","tools"]"""))
        dao.insert(summary(id = "daily", type = SummaryType.DAILY, title = "Daily note", summary = "We polished memory rooms"))

        val results = dao.searchByText(pattern = "%mcp%", type = SummaryType.PROJECT, limit = 10)

        assertEquals(listOf("project"), results.map { it.id })
    }

    @Test
    fun updateLastAccessed_updatesSummary() = runTest {
        dao.insert(summary(id = "s1", lastAccessed = 1_000L))

        dao.updateLastAccessed("s1", 9_000L)

        assertEquals(9_000L, dao.getById("s1")!!.lastAccessed)
    }

    private fun summary(
        id: String,
        type: SummaryType = SummaryType.TOPIC,
        title: String = "Title",
        summary: String = "Summary",
        keywords: String = "[]",
        lastAccessed: Long = 1_000L,
    ): MemorySummaryEntity =
        MemorySummaryEntity(
            id = id,
            type = type,
            title = title,
            summary = summary,
            keywords = keywords,
            sourceMessageIds = "[]",
            createdAt = 1_000L,
            updatedAt = 1_000L,
            lastAccessed = lastAccessed,
        )
}
