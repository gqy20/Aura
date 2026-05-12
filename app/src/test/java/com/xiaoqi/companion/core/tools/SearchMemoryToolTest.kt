package com.xiaoqi.companion.core.tools

import com.xiaoqi.companion.data.db.converter.MemoryType
import com.xiaoqi.companion.data.db.dao.MemoryDao
import com.xiaoqi.companion.data.db.entity.MemoryEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchMemoryToolTest {

    private val memoryDao: MemoryDao = mockk()

    @Test
    fun execute_returnsMatchingMemoriesByQuery() = runTest {
        val memories = listOf(
            MemoryEntity(id = "1", type = MemoryType.FACT, content = "User likes jasmine tea", importance = 0.9f, timestamp = 1000L),
            MemoryEntity(id = "2", type = MemoryType.EPISODE, content = "User visited Tokyo last summer", importance = 0.7f, timestamp = 2000L),
        )
        coEvery { memoryDao.observeAll() } returns flowOf(memories)

        val tool = SearchMemoryTool(memoryDao)
        val result = tool.execute(SearchMemoryTool.Args(query = "jasmine"))

        assertTrue(result.contains("jasmine tea"))
        assertTrue(!result.contains("Tokyo"))
    }

    @Test
    fun execute_filtersByType() = runTest {
        val memories = listOf(
            MemoryEntity(id = "1", type = MemoryType.FACT, content = "Likes cats", importance = 0.8f, timestamp = 1000L),
            MemoryEntity(id = "2", type = MemoryType.EPISODE, content = "Adopted a cat", importance = 0.6f, timestamp = 2000L),
        )
        coEvery { memoryDao.observeAll() } returns flowOf(memories)

        val tool = SearchMemoryTool(memoryDao)
        val result = tool.execute(SearchMemoryTool.Args(query = "cat", type = "FACT"))

        assertTrue(result.contains("Likes cats"))
        assertTrue(!result.contains("Adopted a cat"))
    }

    @Test
    fun execute_limitsResults() = runTest {
        val memories = listOf(
            MemoryEntity(id = "1", type = MemoryType.FACT, content = "Cat fact one", importance = 0.9f, timestamp = 1000L),
            MemoryEntity(id = "2", type = MemoryType.FACT, content = "Cat fact two", importance = 0.8f, timestamp = 2000L),
            MemoryEntity(id = "3", type = MemoryType.FACT, content = "Cat fact three", importance = 0.7f, timestamp = 3000L),
        )
        coEvery { memoryDao.observeAll() } returns flowOf(memories)

        val tool = SearchMemoryTool(memoryDao)
        val result = tool.execute(SearchMemoryTool.Args(query = "cat", limit = 2))

        val count = "\"count\":2"
        assertTrue(result.contains(count))
    }

    @Test
    fun execute_returnsEmptyWhenNoMatch() = runTest {
        coEvery { memoryDao.observeAll() } returns flowOf(emptyList())

        val tool = SearchMemoryTool(memoryDao)
        val result = tool.execute(SearchMemoryTool.Args(query = "nonexistent"))

        assertTrue(result.contains("\"count\":0"))
    }

    @Test
    fun descriptor_exposesKoogToolMetadata() {
        val tool = SearchMemoryTool(mockk())

        assertEquals("search_memory", tool.name)
        val desc = tool.descriptor.description
        assertTrue("Expected descriptor to contain 'search' or 'memory' but got: '$desc'", desc.contains("search", ignoreCase = true) || desc.contains("memory", ignoreCase = true))
    }
}
