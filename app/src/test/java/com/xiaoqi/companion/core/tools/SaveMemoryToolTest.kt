package com.xiaoqi.companion.core.tools

import com.xiaoqi.companion.data.db.converter.MemoryType
import com.xiaoqi.companion.data.db.dao.MemoryDao
import com.xiaoqi.companion.data.db.dao.ToolCallDao
import com.xiaoqi.companion.data.db.entity.MemoryEntity
import com.xiaoqi.companion.data.db.entity.ToolCallEntity
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveMemoryToolTest {

    private val memoryDao: MemoryDao = mockk(relaxed = true)
    private val toolCallDao: ToolCallDao = mockk(relaxed = true)
    private val recorder = ToolCallRecorder(toolCallDao)

    @Test
    fun execute_savesMemoryAndRecordsToolCall() = runTest {
        val tool = SaveMemoryTool(
            memoryDao = memoryDao,
            recorder = recorder,
            sessionIdProvider = { "session" },
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
            toolCallDao.insert(match<ToolCallEntity> {
                it.sessionId == "session" &&
                    it.toolName == "save_memory" &&
                    it.argumentsJson.contains("jasmine tea") &&
                    it.status == "RUNNING"
            })
            toolCallDao.updateResult(
                id = any(),
                status = "SUCCESS",
                resultJson = match { it.contains("memoryId") },
                errorMessage = null,
                completedAt = any(),
            )
        }
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
            recorder = recorder,
            sessionIdProvider = { "session" },
        )

        assertEquals("save_memory", tool.name)
        assertTrue(tool.descriptor.description.contains("memory"))
    }
}
