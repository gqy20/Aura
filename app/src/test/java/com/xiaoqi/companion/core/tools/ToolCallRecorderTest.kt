package com.xiaoqi.companion.core.tools

import com.xiaoqi.companion.data.db.dao.ToolCallDao
import com.xiaoqi.companion.data.db.entity.ToolCallEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolCallRecorderTest {

    private val dao: ToolCallDao = mockk(relaxed = true)
    private val recorder = ToolCallRecorder(dao)

    @Test
    fun recordSuccess_insertsRunningCallAndUpdatesSuccess() = runTest {
        val result = recorder.record(
            sessionId = "session",
            toolName = "save_memory",
            argumentsJson = """{"content":"remember me"}""",
        ) {
            """{"ok":true}"""
        }

        assertEquals("""{"ok":true}""", result)
        coVerify {
            dao.insert(match<ToolCallEntity> {
                it.sessionId == "session" &&
                    it.toolName == "save_memory" &&
                    it.argumentsJson.contains("remember me") &&
                    it.status == "RUNNING"
            })
            dao.updateResult(
                id = any(),
                status = "SUCCESS",
                resultJson = """{"ok":true}""",
                errorMessage = null,
                completedAt = any(),
            )
        }
    }

    @Test
    fun recordFailure_updatesFailureAndRethrows() = runTest {
        val error = RuntimeException("boom")
        coEvery { dao.updateResult(any(), any(), any(), any(), any()) } returns Unit

        runCatching {
            recorder.record(
                sessionId = "session",
                toolName = "save_memory",
                argumentsJson = "{}",
            ) {
                throw error
            }
        }.onFailure {
            assertEquals(error, it)
        }

        coVerify {
            dao.updateResult(
                id = any(),
                status = "FAILED",
                resultJson = "",
                errorMessage = "boom",
                completedAt = any(),
            )
        }
    }
}
