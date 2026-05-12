package com.xiaoqi.companion.core.tools

import com.xiaoqi.companion.data.db.converter.MemoryType
import com.xiaoqi.companion.data.db.dao.MemoryDao
import com.xiaoqi.companion.data.db.entity.MemoryEntity
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveMemoryToolTest {

    private val memoryDao: MemoryDao = mockk(relaxed = true)

    @Test
    fun execute_savesMemory() = runTest {
        val tool = SaveMemoryTool(
            memoryDao = memoryDao,
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
            memoryDao.insert(match<MemoryEntity> {
                it.type == MemoryType.FACT &&
                    it.content == "User likes jasmine tea" &&
                    it.importance == 0.8f &&
                    it.source == "tool:save_memory"
            })
        }
    }

    @Test
    fun descriptor_exposesKoogToolMetadata() {
        val tool = SaveMemoryTool(
            memoryDao = memoryDao,
        )

        assertEquals("save_memory", tool.name)
        assertTrue(tool.descriptor.description.contains("memory"))
    }
}
