package com.xiaoqi.companion.data.repository

import com.xiaoqi.companion.data.db.BaseDaoTest
import com.xiaoqi.companion.data.db.converter.MemoryType
import com.xiaoqi.companion.data.db.converter.SummaryType
import com.xiaoqi.companion.data.db.dao.MemoryDao
import com.xiaoqi.companion.data.db.dao.MemorySummaryDao
import com.xiaoqi.companion.data.db.entity.MemoryEntity
import com.xiaoqi.companion.data.db.entity.MemorySummaryEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRepositoryTest : BaseDaoTest() {

    private lateinit var memoryDao: MemoryDao
    private lateinit var summaryDao: MemorySummaryDao
    private lateinit var repository: MemoryRepository

    override fun initDaos() {
        memoryDao = db.memoryDao()
        summaryDao = db.memorySummaryDao()
        repository = MemoryRepository(memoryDao, summaryDao)
    }

    @Test
    fun saveMemory_mergesSimilarMemory() = runTest {
        val first = repository.saveMemory(
            SaveMemoryRequest(
                content = "User likes jasmine tea",
                type = MemoryType.FACT,
                importance = 0.5f,
            )
        )
        val second = repository.saveMemory(
            SaveMemoryRequest(
                content = "User likes jasmine tea in the evening",
                type = MemoryType.FACT,
                importance = 0.9f,
                confidence = 0.8f,
            )
        )

        val all = memoryDao.getPromptMemories(10)

        assertEquals(first.memory.id, second.memory.id)
        assertTrue(second.merged)
        assertEquals(1, all.size)
        assertEquals(0.9f, all.single().importance)
        assertTrue(all.single().content.contains("evening"))
    }

    @Test
    fun searchMemories_marksResultsAccessed() = runTest {
        memoryDao.insert(memory(id = "m1", content = "User likes jasmine tea", lastAccessed = 1_000L))

        val result = repository.searchMemories("jasmine")

        assertEquals(listOf("m1"), result.memories.map { it.id })
        assertTrue(memoryDao.getById("m1")!!.lastAccessed > 1_000L)
    }

    @Test
    fun selectPromptContext_combinesRelevantImportantRecentAndSummaries() = runTest {
        memoryDao.insert(memory(id = "relevant", content = "User likes jasmine tea", importance = 0.4f, timestamp = 1_000L))
        memoryDao.insert(memory(id = "important", content = "User is allergic to peanuts", importance = 0.95f, timestamp = 2_000L))
        memoryDao.insert(memory(id = "recent", content = "User started learning pottery", importance = 0.3f, timestamp = 9_000L))
        summaryDao.insert(
            summary(
                id = "summary",
                title = "Tea preferences",
                summary = "The user often returns to jasmine tea as a comfort ritual.",
                keywords = """["jasmine","tea"]""",
            )
        )

        val context = repository.selectPromptContext("Do I like jasmine?")

        assertTrue(context.memoryIds.contains("relevant"))
        assertTrue(context.memoryIds.contains("important"))
        assertTrue(context.memoryIds.contains("recent"))
        assertTrue(context.summaryIds.contains("summary"))
        assertFalse(context.summarySnippets.single().isBlank())
    }

    private fun memory(
        id: String,
        content: String,
        importance: Float = 0.5f,
        timestamp: Long = System.currentTimeMillis(),
        lastAccessed: Long = timestamp,
    ) = MemoryEntity(
        id = id,
        type = MemoryType.FACT,
        content = content,
        importance = importance,
        timestamp = timestamp,
        lastAccessed = lastAccessed,
    )

    private fun summary(
        id: String,
        title: String,
        summary: String,
        keywords: String,
    ) = MemorySummaryEntity(
        id = id,
        type = SummaryType.TOPIC,
        title = title,
        summary = summary,
        keywords = keywords,
        createdAt = 1_000L,
        updatedAt = 1_000L,
        lastAccessed = 1_000L,
    )
}
