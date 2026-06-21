package com.xiaoqi.companion.data.repository

import app.cash.turbine.test
import com.xiaoqi.companion.data.db.dao.ConversationDao
import com.xiaoqi.companion.data.db.entity.ConversationEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ConversationRepositoryTest {

    private fun fakeDao(
        entities: List<ConversationEntity> = emptyList(),
    ): ConversationDao = mockk(relaxed = true) {
        every { observeAll() } returns flowOf(entities)
        entities.forEach { entity ->
            coEvery { getById(entity.id) } returns entity
            every { observeById(entity.id) } returns flowOf(entity)
        }
    }

    @Test
    fun observeAll_mapsEntitiesToItems() = runTest {
        val entity = ConversationEntity(
            id = "c1", title = "Hello", createdAt = 100, updatedAt = 200, messageCount = 5,
        )
        val repo = ConversationRepository(fakeDao(listOf(entity)))

        repo.observeAll().test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("c1", items[0].id)
            assertEquals("Hello", items[0].title)
            assertEquals(5, items[0].messageCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun createNew_generatesUuidAndInserts() = runTest {
        val dao: ConversationDao = mockk(relaxed = true) {
            coEvery { insert(any()) } returns Unit
        }
        val repo = ConversationRepository(dao)

        val item = repo.createNew()

        assertEquals(36, item.id.length)
        assertEquals("New conversation", item.title)
        assertEquals(0, item.messageCount)
        coVerify { dao.insert(match<ConversationEntity> { it.id == item.id && it.title == "New conversation" }) }
    }

    @Test
    fun createNew_withFirstMessage_usesTruncatedTitle() = runTest {
        val dao: ConversationDao = mockk(relaxed = true) {
            coEvery { insert(any()) } returns Unit
        }
        val repo = ConversationRepository(dao)

        val longMessage = "这是一条很长很长很长很长很长很长很长很长很长很长的消息内容用于测试标题截断功能"
        val item = repo.createNew(firstMessage = longMessage)

        assertEquals(31, item.title.length) // 30 chars + "…"
        assert(item.title.endsWith("…"))
        coVerify { dao.insert(match<ConversationEntity> { it.title.endsWith("…") }) }
    }

    @Test
    fun createNew_withShortMessage_usesFullMessage() = runTest {
        val dao: ConversationDao = mockk(relaxed = true) {
            coEvery { insert(any()) } returns Unit
        }
        val repo = ConversationRepository(dao)

        val item = repo.createNew(firstMessage = "你好")

        assertEquals("你好", item.title)
    }

    @Test
    fun onMessageSent_incrementsCount() = runTest {
        val dao: ConversationDao = mockk(relaxed = true) {
            coEvery { getById("c1") } returns ConversationEntity(
                id = "c1", title = "Hello", createdAt = 100, updatedAt = 200, messageCount = 3,
            )
        }
        val repo = ConversationRepository(dao)

        repo.onMessageSent("c1", "new message")

        coVerify { dao.incrementMessageCount("c1", any()) }
    }

    @Test
    fun onMessageSent_firstMessage_updatesTitle() = runTest {
        val dao: ConversationDao = mockk(relaxed = true) {
            coEvery { getById("c1") } returns ConversationEntity(
                id = "c1", title = "New conversation", createdAt = 100, updatedAt = 200, messageCount = 0,
            )
        }
        val repo = ConversationRepository(dao)

        repo.onMessageSent("c1", "first real message")

        coVerify { dao.incrementMessageCount("c1", any()) }
        coVerify { dao.updateTitle("c1", "first real message", any()) }
    }

    @Test
    fun onMessageSent_unknownSession_doesNothing() = runTest {
        val dao: ConversationDao = mockk(relaxed = true) {
            coEvery { getById("unknown") } returns null
        }
        val repo = ConversationRepository(dao)

        repo.onMessageSent("unknown", "hello")

        coVerify(exactly = 0) { dao.incrementMessageCount(any(), any()) }
    }

    @Test
    fun getById_delegatesToDao() = runTest {
        val entity = ConversationEntity(
            id = "c1", title = "Test", createdAt = 100, updatedAt = 200, messageCount = 2,
        )
        val repo = ConversationRepository(fakeDao(listOf(entity)))

        val item = repo.getById("c1")

        assertNotNull(item)
        assertEquals("Test", item!!.title)
    }

    @Test
    fun getById_returnsNullForMissing() = runTest {
        val dao: ConversationDao = mockk(relaxed = true) {
            coEvery { getById("missing") } returns null
        }
        val repo = ConversationRepository(dao)

        val item = repo.getById("missing")

        assertNull(item)
    }

    @Test
    fun delete_delegatesToDao() = runTest {
        val dao: ConversationDao = mockk(relaxed = true) {
            coEvery { deleteById("c1") } returns Unit
        }
        val repo = ConversationRepository(dao)

        repo.delete("c1")

        coVerify { dao.deleteById("c1") }
    }

    @Test
    fun count_delegatesToDao() = runTest {
        val dao: ConversationDao = mockk(relaxed = true) {
            coEvery { count() } returns 42
        }
        val repo = ConversationRepository(dao)

        assertEquals(42, repo.count())
    }
}
