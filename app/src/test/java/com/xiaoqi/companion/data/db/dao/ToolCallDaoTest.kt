package com.xiaoqi.companion.data.db.dao

import app.cash.turbine.test
import com.xiaoqi.companion.data.db.BaseDaoTest
import com.xiaoqi.companion.data.db.entity.ToolCallEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolCallDaoTest : BaseDaoTest() {

    private lateinit var dao: ToolCallDao

    override fun initDaos() {
        dao = db.toolCallDao()
    }

    @Test
    fun observeBySession_ordersNewestFirst() = runTest {
        dao.insert(makeToolCall(id = "old", createdAt = 1000L))
        dao.insert(makeToolCall(id = "new", createdAt = 2000L))

        dao.observeBySession("session").test {
            val calls = awaitItem()
            assertEquals(listOf("new", "old"), calls.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun updateResult_marksCallComplete() = runTest {
        dao.insert(makeToolCall(id = "call-1", status = "RUNNING"))

        dao.updateResult(
            id = "call-1",
            status = "SUCCESS",
            resultJson = """{"memoryId":"m1"}""",
            errorMessage = null,
            completedAt = 3000L,
        )

        val call = dao.getById("call-1")!!
        assertEquals("SUCCESS", call.status)
        assertEquals("""{"memoryId":"m1"}""", call.resultJson)
        assertEquals(3000L, call.completedAt)
    }

    private fun makeToolCall(
        id: String,
        sessionId: String = "session",
        toolName: String = "save_memory",
        status: String = "PENDING",
        createdAt: Long = 1000L,
    ) = ToolCallEntity(
        id = id,
        sessionId = sessionId,
        toolName = toolName,
        argumentsJson = """{"content":"hello"}""",
        status = status,
        createdAt = createdAt,
    )
}
