package com.xiaoqi.companion.data.repository

import app.cash.turbine.test
import com.xiaoqi.companion.data.db.dao.MessageDao
import com.xiaoqi.companion.data.db.entity.MessageEntity
import com.xiaoqi.companion.data.db.converter.MessageRole
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class MessageRepositoryTest {

    @Test
    fun getMessagesBySession_delegatesToDao() = runTest {
        val dao: MessageDao = mockk {
            every { observeBySession("s1") } returns flowOf(
                listOf(MessageEntity(id = "m1", sessionId = "s1", role = MessageRole.USER, content = "hi", timestamp = 1))
            )
        }

        val repo = MessageRepositoryImpl(dao)
        repo.getMessagesBySession("s1").test {
            val messages = awaitItem()
            assertEquals(1, messages.size)
            assertEquals("hi", messages[0].content)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun sendMessage_insertsWithUserRoleAndTimestamp() = runTest {
        val dao: MessageDao = mockk(relaxed = true) {
            coEvery { insert(any()) } returns Unit
        }

        val repo = MessageRepositoryImpl(dao)
        repo.sendMessage(sessionId = "s1", content = "hello")

        coVerify { dao.insert(match<MessageEntity> {
            it.role == MessageRole.USER && it.content == "hello" && it.sessionId == "s1"
        }) }
    }

    @Test
    fun saveAssistantMessage_insertsWithAssistantRoleAndTimestamp() = runTest {
        val dao: MessageDao = mockk(relaxed = true) {
            coEvery { insert(any()) } returns Unit
        }

        val repo = MessageRepositoryImpl(dao)
        repo.saveAssistantMessage(sessionId = "s1", content = "hello back")

        coVerify {
            dao.insert(match<MessageEntity> {
                it.role == MessageRole.ASSISTANT && it.content == "hello back" && it.sessionId == "s1"
            })
        }
    }

    @Test
    fun deleteSession_delegatesToDao() = runTest {
        val dao: MessageDao = mockk(relaxed = true) {
            coEvery { deleteBySession(any()) } returns Unit
        }

        val repo = MessageRepositoryImpl(dao)
        repo.deleteSession("s1")

        coVerify { dao.deleteBySession("s1") }
    }
}
