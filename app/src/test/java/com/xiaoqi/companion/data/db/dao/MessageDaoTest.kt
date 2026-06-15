package com.xiaoqi.companion.data.db.dao

import com.xiaoqi.companion.data.db.BaseDaoTest
import com.xiaoqi.companion.data.db.converter.MessageRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import app.cash.turbine.test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageDaoTest : BaseDaoTest() {

    private lateinit var dao: MessageDao

    override fun initDaos() {
        dao = db.messageDao()
    }

    // --- insert + observe ---

    @Test
    fun observeBySession_returnsInsertedMessage() = runTest {
        val msg = makeMessage(id = "m1", sessionId = "s1", content = "hello")
        dao.insert(msg)

        dao.observeBySession("s1").test {
            val messages = awaitItem()
            assertEquals(1, messages.size)
            assertEquals("hello", messages[0].content)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- filter by session ---

    @Test
    fun observeBySession_filtersCorrectly() = runTest {
        dao.insert(makeMessage(id = "m1", sessionId = "s1", content = "a"))
        dao.insert(makeMessage(id = "m2", sessionId = "s2", content = "b"))

        dao.observeBySession("s1").test {
            val messages = awaitItem()
            assertEquals(1, messages.size)
            assertEquals("a", messages[0].content)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- order by timestamp ---

    @Test
    fun observeBySession_ordersByTimestampAsc() = runTest {
        dao.insert(makeMessage(id = "m1", sessionId = "s1", timestamp = 3000))
        dao.insert(makeMessage(id = "m2", sessionId = "s1", timestamp = 1000))
        dao.insert(makeMessage(id = "m3", sessionId = "s1", timestamp = 2000))

        dao.observeBySession("s1").test {
            val messages = awaitItem()
            assertEquals(3, messages.size)
            assertEquals("m2", messages[0].id)
            assertEquals("m3", messages[1].id)
            assertEquals("m1", messages[2].id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- getById ---

    @Test
    fun getById_returnsCorrectMessage() = runTest {
        val msg = makeMessage(id = "m1", sessionId = "s1")
        dao.insert(msg)
        val result = dao.getById("m1")
        assertNotNull(result)
        assertEquals("m1", result!!.id)
    }

    @Test
    fun getById_returnsNullForNonExistent() = runTest {
        val result = dao.getById("nonexistent")
        assertNull(result)
    }

    // --- insertAll ---

    @Test
    fun insertAll_insertsMultipleMessages() = runTest {
        val msgs = listOf(
            makeMessage(id = "m1"),
            makeMessage(id = "m2"),
            makeMessage(id = "m3")
        )
        dao.insertAll(msgs)

        val count = dao.observeBySession("default").first().size
        assertEquals(3, count)
    }

    @Test
    fun getInteractionSummary_returnsCountsAndLastInteraction() = runTest {
        dao.insert(makeMessage(id = "old", role = MessageRole.USER, timestamp = 1_000L))
        dao.insert(makeMessage(id = "todayUser", role = MessageRole.USER, timestamp = 10_000L))
        dao.insert(makeMessage(id = "todayAssistant", role = MessageRole.ASSISTANT, timestamp = 12_000L))
        dao.insert(makeMessage(id = "otherSession", sessionId = "other", role = MessageRole.USER, timestamp = 20_000L))

        val summary = dao.getInteractionSummary(sessionId = "default", startOfToday = 9_000L)

        assertEquals(3, summary.messageCount)
        assertEquals(2, summary.userMessageCount)
        assertEquals(1, summary.assistantMessageCount)
        assertEquals(2, summary.messagesToday)
        assertEquals(1, summary.userMessagesToday)
        assertEquals(1, summary.assistantMessagesToday)
        assertEquals("ASSISTANT", summary.lastMessageRole)
        assertEquals(12_000L, summary.lastMessageAt)
        assertEquals(10_000L, summary.lastUserMessageAt)
    }

    @Test
    fun getInteractionSummary_handlesEmptySession() = runTest {
        val summary = dao.getInteractionSummary(sessionId = "missing", startOfToday = 9_000L)

        assertEquals(0, summary.messageCount)
        assertEquals(0, summary.userMessageCount)
        assertEquals(0, summary.assistantMessageCount)
        assertEquals(0, summary.messagesToday)
        assertNull(summary.lastMessageRole)
        assertEquals(0L, summary.lastMessageAt)
        assertEquals(0L, summary.lastUserMessageAt)
    }

    @Test
    fun getMessagesBeforeAndAfter_returnsNearbyContext() = runTest {
        dao.insert(makeMessage(id = "m1", timestamp = 1_000L))
        dao.insert(makeMessage(id = "m2", timestamp = 2_000L))
        dao.insert(makeMessage(id = "m3", timestamp = 3_000L))
        dao.insert(makeMessage(id = "m4", timestamp = 4_000L))

        val before = dao.getMessagesBefore(sessionId = "default", timestamp = 3_000L, limit = 2)
        val after = dao.getMessagesAfter(sessionId = "default", timestamp = 2_000L, limit = 2)

        assertEquals(listOf("m2", "m1"), before.map { it.id })
        assertEquals(listOf("m3", "m4"), after.map { it.id })
    }

    // --- deleteBySession ---
    // 注：仅断言 messages 表删除行为。FTS5 rowid 清理的真行为由 androidTest MessageDaoFts5Test 验证。

    @Test
    fun deleteBySession_removesOnlyThatSessionsMessages() = runTest {
        dao.insert(makeMessage(id = "m1", sessionId = "s1"))
        dao.insert(makeMessage(id = "m2", sessionId = "s2"))

        dao.deleteBySession("s1")

        dao.observeBySession("s1").test {
            val remaining = awaitItem()
            assertTrue(remaining.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }

        dao.observeBySession("s2").test {
            val kept = awaitItem()
            assertEquals(1, kept.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- REPLACE conflict ---

    @Test
    fun insert_replaceConflict_updatesExisting() = runTest {
        val original = makeMessage(id = "m1", content = "old")
        dao.insert(original)

        val updated = makeMessage(id = "m1", content = "new", timestamp = 9999)
        dao.insert(updated)

        val result = dao.getById("m1")!!
        assertEquals("new", result.content)
        assertEquals(9999L, result.timestamp)
    }

    // --- helper ---

    private fun makeMessage(
        id: String = java.util.UUID.randomUUID().toString(),
        sessionId: String = "default",
        role: MessageRole = MessageRole.USER,
        content: String = "test",
        imageBase64: String? = null,
        timestamp: Long = System.currentTimeMillis(),
    ) = com.xiaoqi.companion.data.db.entity.MessageEntity(
        id = id,
        sessionId = sessionId,
        role = role,
        content = content,
        imageBase64 = imageBase64,
        timestamp = timestamp,
        metadata = null,
    )
}
