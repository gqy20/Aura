package com.xiaoqi.companion.core.tools

import com.xiaoqi.companion.data.db.converter.MemoryType
import com.xiaoqi.companion.data.db.entity.MemoryEntity
import com.xiaoqi.companion.data.repository.MemoryRepository
import com.xiaoqi.companion.data.repository.SaveMemoryRequest
import com.xiaoqi.companion.data.repository.SaveMemoryResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveMemoryToolTest {

    private val memoryRepository: MemoryRepository = mockk(relaxed = true)

    @Test
    fun execute_savesMemory() = runTest {
        coEvery { memoryRepository.saveMemory(any()) } returns SaveMemoryResult(
            memory = MemoryEntity(
                id = "memory-1",
                type = MemoryType.FACT,
                content = "User likes jasmine tea",
                source = "tool:save_memory",
                importance = 0.8f,
                timestamp = 1_000L,
            ),
            merged = false,
        )
        val tool = SaveMemoryTool(
            memoryRepository = memoryRepository,
        )

        val result = tool.execute(
            SaveMemoryTool.Args(
                content = "User likes jasmine tea",
                type = "FACT",
                importance = 0.8f,
            )
        )

        assertTrue(result.contains("saved"))
        coVerify {
            memoryRepository.saveMemory(match<SaveMemoryRequest> {
                it.type == MemoryType.FACT &&
                    it.content == "User likes jasmine tea" &&
                    it.importance == 0.8f
            })
        }
    }

    @Test
    fun descriptor_exposesKoogToolMetadata() {
        val tool = SaveMemoryTool(
            memoryRepository = memoryRepository,
        )

        assertEquals("save_memory", tool.name)
        assertTrue(tool.descriptor.description.contains("memory"))
    }
}
