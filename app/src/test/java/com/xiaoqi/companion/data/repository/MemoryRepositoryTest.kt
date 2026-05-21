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

    @Test
    fun selectPromptContext_whenQueryIsUnrelated_stillIncludesImportantMemories() = runTest {
        memoryDao.insert(memory(id = "important", content = "User likes jasmine tea", importance = 0.95f, timestamp = 1_000L))
        memoryDao.insert(memory(id = "recent", content = "User started learning pottery", importance = 0.3f, timestamp = 9_000L))

        val context = repository.selectPromptContext("Tell me a joke")

        assertTrue(context.memoryIds.contains("important"))
        assertTrue(context.memoryIds.contains("recent"))
    }

    @Test
    fun selectPromptContext_includesMoreThanEightMemoriesWhenWithinTokenBudget() = runTest {
        repeat(12) { index ->
            memoryDao.insert(
                memory(
                    id = "memory-$index",
                    content = "Stable preference ${index + 1}",
                    importance = 0.9f - index * 0.01f,
                    timestamp = 1_000L + index,
                )
            )
        }

        val context = repository.selectPromptContext("unmatched")

        assertEquals(12, context.memoryIds.size)
        assertTrue(context.memoryIds.contains("memory-11"))
    }

    @Test
    fun selectPromptContext_truncatesSingleMemoryToTokenBudget() = runTest {
        val longContent = "a".repeat(31_000)
        memoryDao.insert(memory(id = "long", content = longContent, importance = 0.99f))
        memoryDao.insert(memory(id = "small", content = "small memory", importance = 0.1f))

        val context = repository.selectPromptContext("unmatched")

        assertEquals(listOf("long"), context.memoryIds)
        assertTrue(context.memorySnippets.single().length <= 30_000)
        assertTrue(context.memorySnippets.single().endsWith("..."))
    }

    @Test
    fun selectPromptContext_includesMoreThanTwoSummariesWhenWithinTokenBudget() = runTest {
        repeat(4) { index ->
            summaryDao.insert(
                summary(
                    id = "summary-$index",
                    title = "Topic ${index + 1}",
                    summary = "The user mentioned summary detail ${index + 1}.",
                    keywords = """["topic"]""",
                )
            )
        }

        val context = repository.selectPromptContext("topic")

        assertEquals(4, context.summaryIds.size)
        assertTrue(context.summarySnippets.all { it.contains(":") })
    }

    @Test
    fun selectPromptContext_truncatesSummaryToTokenBudget() = runTest {
        summaryDao.insert(
            summary(
                id = "long-summary",
                title = "Long Summary",
                summary = "s".repeat(16_000),
                keywords = """["long"]""",
            )
        )
        summaryDao.insert(
            summary(
                id = "small-summary",
                title = "Small Summary",
                summary = "short",
                keywords = """["small"]""",
            )
        )

        val context = repository.selectPromptContext("long")

        assertEquals(listOf("long-summary"), context.summaryIds)
        assertTrue(context.summarySnippets.single().length <= 15_000)
        assertTrue(context.summarySnippets.single().endsWith("..."))
    }

    @Test
    fun selectPromptContext_excludesExpiredMemories() = runTest {
        memoryDao.insert(
            memory(
                id = "expired",
                content = "User used to live in Tokyo",
                importance = 0.95f,
                expiresAt = System.currentTimeMillis() - 1_000L,
            )
        )

        val context = repository.selectPromptContext("Where did I live?")

        assertFalse(context.memoryIds.contains("expired"))
    }

    @Test
    fun selectPromptContext_onlyIncludesPrivateMemoryWhenRelevant() = runTest {
        memoryDao.insert(
            memory(
                id = "private",
                content = "User private journal mentions jasmine tea",
                importance = 0.95f,
                sensitivity = "private",
            )
        )

        val unrelated = repository.selectPromptContext("What should I cook tonight?")
        val related = repository.selectPromptContext("Do I mention jasmine tea?")

        assertFalse(unrelated.memoryIds.contains("private"))
        assertTrue(related.memoryIds.contains("private"))
    }

    private fun memory(
        id: String,
        content: String,
        importance: Float = 0.5f,
        timestamp: Long = System.currentTimeMillis(),
        lastAccessed: Long = timestamp,
        expiresAt: Long? = null,
        sensitivity: String = "normal",
    ) = MemoryEntity(
        id = id,
        type = MemoryType.FACT,
        content = content,
        importance = importance,
        timestamp = timestamp,
        lastAccessed = lastAccessed,
        expiresAt = expiresAt,
        sensitivity = sensitivity,
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
