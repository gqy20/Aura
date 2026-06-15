package com.xiaoqi.companion.data.db

import com.xiaoqi.companion.data.db.dao.HealthSnapshotDao
import com.xiaoqi.companion.data.db.entity.HealthSnapshotEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HealthSnapshotDaoTest : BaseDaoTest() {

    private lateinit var dao: HealthSnapshotDao

    override fun initDaos() {
        dao = db.healthSnapshotDao()
    }

    @Before fun beforeEach() = runTest {
        dao.clearAll()
    }

    private fun snap(
        date: Int,
        steps: Int = 0,
        avgHr: Int? = null,
        sleepMin: Int? = null,
    ) = HealthSnapshotEntity(
        date = date,
        steps = steps,
        avgHeartRate = avgHr,
        sleepDurationMinutes = sleepMin,
        fetchedAt = 1_700_000_000_000L,
    )

    @Test fun `upsert inserts and reads back`() = runTest {
        dao.upsert(snap(date = 20260615, steps = 8421))
        val got = dao.findByDate(20260615)
        assertEquals(8421, got?.steps)
    }

    @Test fun `upsert replaces by primary key (date)`() = runTest {
        dao.upsert(snap(date = 20260615, steps = 1000))
        dao.upsert(snap(date = 20260615, steps = 9999))
        assertEquals(9999, dao.findByDate(20260615)?.steps)
    }

    @Test fun `findInRange returns ordered descending and respects bounds`() = runTest {
        dao.upsertAll(
            listOf(
                snap(date = 20260613),
                snap(date = 20260615),
                snap(date = 20260614),
                snap(date = 20260610), // outside range
            )
        )
        val got = dao.findInRange(20260613, 20260615)
        assertEquals(listOf(20260615, 20260614, 20260613), got.map { it.date })
    }

    @Test fun `getLatest returns row with max date`() = runTest {
        dao.upsertAll(listOf(snap(date = 20260610), snap(date = 20260615), snap(date = 20260613)))
        assertEquals(20260615, dao.getLatest()?.date)
    }

    @Test fun `findByDate returns null for unknown date`() = runTest {
        assertNull(dao.findByDate(20260615))
    }

    @Test fun `clearAll wipes everything`() = runTest {
        dao.upsertAll(listOf(snap(date = 20260615), snap(date = 20260614)))
        dao.clearAll()
        assertEquals(0, dao.countAll())
    }

    @Test fun `health snapshot preserves optional fields`() = runTest {
        val entity = HealthSnapshotEntity(
            date = 20260615,
            steps = 8000,
            distanceMeters = 6.4,
            caloriesKcal = 420.0,
            avgHeartRate = 72,
            restingHeartRate = 60,
            minHeartRate = 55,
            maxHeartRate = 130,
            sleepDurationMinutes = 480,
            sleepStagesJson = """[{"stage":"DEEP","startEpoch":1,"endEpoch":2}]""",
            sourcePackages = """["com.mi.health"]""",
            fetchedAt = 1_700_000_000_000L,
        )
        dao.upsert(entity)
        val got = dao.findByDate(20260615)!!
        assertEquals(72, got.avgHeartRate)
        assertEquals(55, got.minHeartRate)
        assertEquals(130, got.maxHeartRate)
        assertEquals(480, got.sleepDurationMinutes)
        assertTrue(got.sleepStagesJson.contains("DEEP"))
        assertTrue(got.sourcePackages.contains("com.mi.health"))
    }

    /**
     * M7 多源合并写入:[SensorManagerHealthSource] 调 `updateStepsOnly`,只覆盖
     * `steps` / `sourcePackages` / `fetchedAt` 三列,**保留**心率/睡眠。
     */
    @Test fun `updateStepsOnly preserves heart rate and sleep fields`() = runTest {
        // 1) Health Connect 先写一行全指标
        dao.upsert(
            HealthSnapshotEntity(
                date = 20260615,
                steps = 5000,
                avgHeartRate = 72,
                sleepDurationMinutes = 480,
                sourcePackages = """["com.mi.health"]""",
                fetchedAt = 1_700_000_000_000L,
            ),
        )
        // 2) 本机 sensor 后写 — 模拟步数更新
        val updated = dao.updateStepsOnly(
            date = 20260615,
            steps = 8500,
            sourcePackages = """["com.mi.health","android.sensor"]""",
            fetchedAt = 1_700_000_001_000L,
        )
        assertEquals(1, updated)
        // 3) 心率/睡眠应保留,步数被更新
        val got = dao.findByDate(20260615)!!
        assertEquals(8500, got.steps)
        assertEquals(72, got.avgHeartRate)
        assertEquals(480, got.sleepDurationMinutes)
    }

    @Test fun `updateStepsOnly on missing date returns zero rows affected`() = runTest {
        val updated = dao.updateStepsOnly(
            date = 20991231,
            steps = 1,
            sourcePackages = "[]",
            fetchedAt = 1L,
        )
        assertEquals(0, updated)
        assertNull(dao.findByDate(20991231))
    }
}
