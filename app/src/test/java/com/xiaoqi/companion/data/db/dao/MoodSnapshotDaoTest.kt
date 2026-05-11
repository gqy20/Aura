package com.xiaoqi.companion.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xiaoqi.companion.data.db.CompanionDatabase
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
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MoodSnapshotDaoTest {

    private lateinit var db: CompanionDatabase
    private lateinit var dao: MoodSnapshotDao
    private lateinit var stateDao: AgentStateDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, CompanionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.moodSnapshotDao()
        stateDao = db.agentStateDao()
    }

    @After
    fun teardown() { db.close() }

    @Test
    fun observeByCompanionId_returnsSnapshotsDesc() = runTest {
        val cid = "c1"
        stateDao.insert(makeState(cid))
        dao.insert(makeSnapshot(cid, timestamp = 1000))
        dao.insert(makeSnapshot(cid, timestamp = 3000))
        dao.insert(makeSnapshot(cid, timestamp = 2000))

        dao.observeByCompanionId(cid).test {
            val items = awaitItem()
            assertEquals(3, items.size)
            assertEquals(3000L, items[0].timestamp)
            assertEquals(2000L, items[1].timestamp)
            assertEquals(1000L, items[2].timestamp)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeByDateRange_filtersCorrectly() = runTest {
        val cid = "c1"
        stateDao.insert(makeState(cid))
        dao.insert(makeSnapshot(cid, timestamp = 1000))
        dao.insert(makeSnapshot(cid, timestamp = 2000))
        dao.insert(makeSnapshot(cid, timestamp = 3000))
        dao.insert(makeSnapshot(cid, timestamp = 4000))

        dao.observeByDateRange(cid, start = 1500, end = 3500).test {
            val items = awaitItem()
            assertEquals(2, items.size) // 2000 and 3000
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getLatestSnapshot_returnsMostRecent() = runTest {
        val cid = "c1"
        stateDao.insert(makeState(cid))
        dao.insert(makeSnapshot(cid, timestamp = 1000))
        dao.insert(makeSnapshot(cid, timestamp = 5000))
        dao.insert(makeSnapshot(cid, timestamp = 3000))

        val latest = dao.getLatestSnapshot(cid)
        assertNotNull(latest)
        assertEquals(5000L, latest!!.timestamp)
    }

    @Test
    fun deleteByCompanionId_removesSnapshots() = runTest {
        val cid = "c1"
        stateDao.insert(makeState(cid))
        dao.insert(makeSnapshot(cid))
        dao.insert(makeSnapshot(cid))

        dao.deleteByCompanionId(cid)
        dao.observeByCompanionId(cid).test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun fkCascade_deletesSnapshotsWhenStateDeleted() = runTest {
        val cid = "c1"
        stateDao.insert(makeState(cid))
        dao.insert(makeSnapshot(cid, mood = "happy"))
        dao.insert(makeSnapshot(cid, mood = "sad"))

        // Delete parent state → FK CASCADE should remove snapshots
        stateDao.deleteByCompanionId(cid)

        dao.observeByCompanionId(cid).test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun makeState(companionId: String) =
        com.xiaoqi.companion.data.db.entity.AgentStateEntity(
            id = UUID.randomUUID().toString(), companionId = companionId,
            createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis(),
        )

    private fun makeSnapshot(
        companionId: String,
        mood: String = "neutral",
        trigger: String? = null,
        intensity: Float = 0.5f,
        timestamp: Long = System.currentTimeMillis(),
    ) = com.xiaoqi.companion.data.db.entity.MoodSnapshotEntity(
        id = UUID.randomUUID().toString(), companionId = companionId,
        mood = mood, trigger = trigger, intensity = intensity, timestamp = timestamp,
    )
}
