package com.xiaoqi.companion.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xiaoqi.companion.data.db.CompanionDatabase
import com.xiaoqi.companion.data.db.converter.MessageRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import app.cash.turbine.test
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MessageDaoTest {

    private lateinit var db: CompanionDatabase
    private lateinit var dao: MessageDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, CompanionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.messageDao()
    }

    @After
    fun teardown() {
        db.close()
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

    // --- deleteBySession ---

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
