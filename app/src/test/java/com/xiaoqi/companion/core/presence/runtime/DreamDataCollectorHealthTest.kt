package com.xiaoqi.companion.core.presence.runtime

import com.xiaoqi.companion.data.datastore.AppPreferences
import com.xiaoqi.companion.data.db.dao.HealthSnapshotDao
import com.xiaoqi.companion.data.db.dao.MessageDao
import com.xiaoqi.companion.data.db.dao.MoodSnapshotDao
import com.xiaoqi.companion.data.db.entity.HealthSnapshotEntity
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证 M7 Health Connect 接入后:
 * 1) collectLast7Days 读取 health_snapshot 表
 * 2) render() 输出 ## 健康快照 section
 * 3) 不影响现有其他 section(情绪/消息/记忆)
 */
class DreamDataCollectorHealthTest {

    private val moodDao: MoodSnapshotDao = mockk(relaxed = true)
    private val messageDao: MessageDao = mockk(relaxed = true)
    private val memoryDao: com.xiaoqi.companion.data.db.dao.MemoryDao = mockk(relaxed = true)
    private val healthDao: HealthSnapshotDao = mockk(relaxed = true)
    private val appPreferences: AppPreferences = mockk {
        every { currentSessionId } returns flowOf("default")
    }

    private val collector = DreamDataCollector(moodDao, messageDao, memoryDao, healthDao, appPreferences)

    @Test fun `snapshot includes health data`() = runTest {
        coEvery { moodDao.findInRange(any(), any(), any()) } returns emptyList()
        coEvery { messageDao.getRecentMessages(any(), any()) } returns emptyList()
        coEvery { memoryDao.countAll() } returns 0
        coEvery { memoryDao.getRecentImages(any()) } returns emptyList()

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val todayInt = today.year * 10000 + today.monthValue * 100 + today.dayOfMonth
        coEvery { healthDao.findInRange(any(), any()) } returns listOf(
            HealthSnapshotEntity(
                date = todayInt,
                steps = 8421,
                avgHeartRate = 72,
                sleepDurationMinutes = 480,
                fetchedAt = 1_700_000_000_000L,
            ),
        )

        val snapshot = collector.collectLast7Days(now = System.currentTimeMillis())
        assertEquals(1, snapshot.healthSnapshots.size)
        assertEquals(8421, snapshot.healthSnapshots[0].steps)
    }

    @Test fun `render outputs health section when data exists`() = runTest {
        coEvery { moodDao.findInRange(any(), any(), any()) } returns emptyList()
        coEvery { messageDao.getRecentMessages(any(), any()) } returns emptyList()
        coEvery { memoryDao.countAll() } returns 0
        coEvery { memoryDao.getRecentImages(any()) } returns emptyList()

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val todayInt = today.year * 10000 + today.monthValue * 100 + today.dayOfMonth
        coEvery { healthDao.findInRange(any(), any()) } returns listOf(
            HealthSnapshotEntity(
                date = todayInt,
                steps = 10000,
                avgHeartRate = 70,
                minHeartRate = 60,
                maxHeartRate = 120,
                sleepDurationMinutes = 450,
                fetchedAt = 1_700_000_000_000L,
            ),
        )

        val snapshot = collector.collectLast7Days()
        val rendered = collector.render(snapshot)
        assertTrue(rendered.contains("## 健康快照"))
        assertTrue(rendered.contains("步数=10000"))
        assertTrue(rendered.contains("平均心率=70bpm"))
        assertTrue(rendered.contains("睡眠=7h30m"))
    }

    @Test fun `render skips health section when empty`() = runTest {
        coEvery { moodDao.findInRange(any(), any(), any()) } returns emptyList()
        coEvery { messageDao.getRecentMessages(any(), any()) } returns emptyList()
        coEvery { memoryDao.countAll() } returns 0
        coEvery { memoryDao.getRecentImages(any()) } returns emptyList()
        coEvery { healthDao.findInRange(any(), any()) } returns emptyList()

        val snapshot = collector.collectLast7Days()
        val rendered = collector.render(snapshot)
        assertTrue(!rendered.contains("## 健康快照"))
    }

    @Test fun `isEmpty considers health snapshots`() = runTest {
        coEvery { moodDao.findInRange(any(), any(), any()) } returns emptyList()
        coEvery { messageDao.getRecentMessages(any(), any()) } returns emptyList()
        coEvery { memoryDao.countAll() } returns 0
        coEvery { memoryDao.getRecentImages(any()) } returns emptyList()
        coEvery { healthDao.findInRange(any(), any()) } returns listOf(
            HealthSnapshotEntity(date = 20260615, steps = 1, fetchedAt = 0),
        )
        val snapshot = collector.collectLast7Days()
        assertTrue(!snapshot.isEmpty)
    }
}
