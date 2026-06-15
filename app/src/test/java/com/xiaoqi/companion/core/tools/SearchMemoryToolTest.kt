package com.xiaoqi.companion.core.tools

import com.xiaoqi.companion.data.db.converter.MemoryType
import com.xiaoqi.companion.data.db.entity.MemoryEntity
import com.xiaoqi.companion.data.repository.MemoryRepository
import com.xiaoqi.companion.data.repository.MemorySearchResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchMemoryToolTest {

    private val memoryRepository: MemoryRepository = mockk()

    @Test
    fun execute_returnsMatchingMemoriesByQuery() = runTest {
        val memories = listOf(
            MemoryEntity(id = "1", type = MemoryType.FACT, content = "User likes jasmine tea", importance = 0.9f, timestamp = 1000L),
            MemoryEntity(id = "2", type = MemoryType.EPISODE, content = "User visited Tokyo last summer", importance = 0.7f, timestamp = 2000L),
        )
        coEvery { memoryRepository.searchMemories("jasmine", null, any()) } returns MemorySearchResult("jasmine", memories.take(1))

        val tool = SearchMemoryTool(memoryRepository)
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
        coEvery { memoryRepository.searchMemories("cat", MemoryType.FACT, any()) } returns MemorySearchResult("cat", memories.take(1))

        val tool = SearchMemoryTool(memoryRepository)
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
        coEvery { memoryRepository.searchMemories("cat", null, 2) } returns MemorySearchResult("cat", memories.take(2))

        val tool = SearchMemoryTool(memoryRepository)
        val result = tool.execute(SearchMemoryTool.Args(query = "cat", limit = 2))

        val count = "\"count\":2"
        assertTrue(result.contains(count))
    }

    @Test
    fun execute_returnsEmptyWhenNoMatch() = runTest {
        coEvery { memoryRepository.searchMemories("nonexistent", null, any()) } returns MemorySearchResult("nonexistent", emptyList())

        val tool = SearchMemoryTool(memoryRepository)
        val result = tool.execute(SearchMemoryTool.Args(query = "nonexistent"))

        assertTrue(result.contains("\"count\":0"))
    }

    @Test
    fun execute_passesRawQueryToRepository() = runTest {
        coEvery { memoryRepository.searchMemories("100%", null, any()) } returns MemorySearchResult("100%", emptyList())

        val tool = SearchMemoryTool(memoryRepository)
        tool.execute(SearchMemoryTool.Args(query = "100%"))
    }

    @Test
    fun descriptor_exposesKoogToolMetadata() {
        val tool = SearchMemoryTool(mockk())

        assertEquals("search_memory", tool.name)
        val desc = tool.descriptor.description
        assertTrue("Expected descriptor to contain 'search' or 'memory' but got: '$desc'", desc.contains("search", ignoreCase = true) || desc.contains("memory", ignoreCase = true))
    }

    // --- description 包含 disambiguation 段,防回归 ---

    @Test
    fun descriptor_distinguishesFromOtherSearchTools() {
        val tool = SearchMemoryTool(mockk())
        val desc = tool.descriptor.description

        // 自己的语义
        assertTrue("description 应提到 long-term / fact / preference 等关键词之一, got: '$desc'",
            desc.contains("FACT", ignoreCase = true) ||
                desc.contains("preference", ignoreCase = true) ||
                desc.contains("profile", ignoreCase = true))
        // 引导 LLM 选 search_records 替代
        assertTrue("description 应引导 'specific message' 用 search_records 替代, got: '$desc'",
            desc.contains("search_records"))
        // 引导 LLM 选 search_summaries 替代
        assertTrue("description 应引导 'time-windowed' 用 search_summaries 替代, got: '$desc'",
            desc.contains("search_summaries"))
    }
}
