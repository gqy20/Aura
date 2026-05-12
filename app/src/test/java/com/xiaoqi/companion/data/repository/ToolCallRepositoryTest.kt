package com.xiaoqi.companion.data.repository

import app.cash.turbine.test
import com.xiaoqi.companion.core.companion.model.ToolCallStatus
import com.xiaoqi.companion.data.db.dao.ToolCallDao
import com.xiaoqi.companion.data.db.entity.ToolCallEntity
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToolCallRepositoryTest {

    @Test
    fun observeBySession_mapsDaoEntitiesToSnapshots() = runTest {
        val dao: ToolCallDao = mockk {
            every { observeBySession("default") } returns flowOf(
                listOf(
                    ToolCallEntity(
                        id = "call-1",
                        sessionId = "default",
                        toolName = "save_memory",
                        argumentsJson = """{"content":"tea"}""",
                        resultJson = """{"ok":true}""",
                        status = "SUCCESS",
                        createdAt = 1_000L,
                        completedAt = 1_250L,
                    ),
                    ToolCallEntity(
                        id = "call-2",
                        sessionId = "default",
                        toolName = "search_memory",
                        argumentsJson = "{}",
                        status = "RUNNING",
                        createdAt = 2_000L,
                    ),
                )
            )
        }

        ToolCallRepositoryImpl(dao).observeBySession("default").test {
            val snapshots = awaitItem()
            assertEquals(ToolCallStatus.SUCCEEDED, snapshots[0].status)
            assertEquals(250L, snapshots[0].durationMs)
            assertEquals(ToolCallStatus.STARTED, snapshots[1].status)
            assertNull(snapshots[1].durationMs)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeBySession_mapsFailures() = runTest {
        val dao: ToolCallDao = mockk {
            every { observeBySession("default") } returns flowOf(
                listOf(
                    ToolCallEntity(
                        id = "call-1",
                        sessionId = "default",
                        toolName = "update_mood",
                        argumentsJson = "{}",
                        status = "FAILED",
                        createdAt = 1_000L,
                        completedAt = 900L,
                        errorMessage = "bad args",
                    )
                )
            )
        }

        ToolCallRepositoryImpl(dao).observeBySession("default").test {
            val snapshot = awaitItem().single()
            assertEquals(ToolCallStatus.FAILED, snapshot.status)
            assertEquals("bad args", snapshot.errorMessage)
            assertEquals(0L, snapshot.durationMs)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
