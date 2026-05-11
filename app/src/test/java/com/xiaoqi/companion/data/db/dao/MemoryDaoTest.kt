package com.xiaoqi.companion.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xiaoqi.companion.data.db.CompanionDatabase
import com.xiaoqi.companion.data.db.converter.MemoryType
import kotlinx.coroutines.test.runTest
import app.cash.turbine.test
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MemoryDaoTest {

    private lateinit var db: CompanionDatabase
    private lateinit var dao: MemoryDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, CompanionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.memoryDao()
    }

    @After
    fun teardown() { db.close() }

    @Test
    fun observeAll_returnsInsertedMemories() = runTest {
        dao.insert(makeMemory(id = "m1", type = MemoryType.FACT))
        dao.observeAll().test {
            val items = awaitItem()
            assertEquals(1, items.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeByType_filtersCorrectly() = runTest {
        dao.insert(makeMemory(id = "m1", type = MemoryType.FACT))
        dao.insert(makeMemory(id = "m2", type = MemoryType.EPISODE))

        dao.observeByType(MemoryType.FACT).test {
            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeByType_ordersByImportanceDesc() = runTest {
        dao.insert(makeMemory(id = "m1", importance = 0.3f))
        dao.insert(makeMemory(id = "m2", importance = 0.9f))
        dao.insert(makeMemory(id = "m3", importance = 0.6f))

        dao.observeAll().test {
            val items = awaitItem()
            assertEquals("m2", items[0].id)
            assertEquals("m3", items[1].id)
            assertEquals("m1", items[2].id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getById_returnsCorrect() = runTest {
        val mem = makeMemory(id = "m1")
        dao.insert(mem)
        assertNotNull(dao.getById("m1"))
    }

    @Test
    fun updateLastAccessed_updatesTimestamp() = runTest {
        dao.insert(makeMemory(id = "m1", lastAccessed = 1000L))
        dao.updateLastAccessed("m1", 9999L)
        val result = dao.getById("m1")!!
        assertEquals(9999L, result.lastAccessed)
    }

    @Test
    fun observeRecent_filtersByTime() = runTest {
        val now = System.currentTimeMillis()
        dao.insert(makeMemory(id = "m1", timestamp = now - 10000))
        dao.insert(makeMemory(id = "m2", timestamp = now - 1000))
        dao.insert(makeMemory(id = "m3", timestamp = now - 5000))

        dao.observeRecent(after = now - 6000, limit = 10).test {
            val items = awaitItem()
            assertEquals(2, items.size) // m2 and m3
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deleteById_removesMemory() = runTest {
        dao.insert(makeMemory(id = "m1"))
        dao.deleteById("m1")
        dao.observeAll().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun makeMemory(
        id: String = UUID.randomUUID().toString(),
        type: MemoryType = MemoryType.FACT,
        content: String = "test memory",
        importance: Float = 0.5f,
        timestamp: Long = System.currentTimeMillis(),
        lastAccessed: Long = timestamp,
    ) = com.xiaoqi.companion.data.db.entity.MemoryEntity(
        id = id, type = type, content = content,
        importance = importance, timestamp = timestamp, lastAccessed = lastAccessed,
    )
}
