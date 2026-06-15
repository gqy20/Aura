package com.xiaoqi.companion.core.presence.runtime

import com.xiaoqi.companion.data.db.converter.MessageRole
import com.xiaoqi.companion.data.db.dao.MemoryDao
import com.xiaoqi.companion.data.db.dao.MessageDao
import com.xiaoqi.companion.data.db.dao.MoodSnapshotDao
import com.xiaoqi.companion.data.db.entity.MessageEntity
import com.xiaoqi.companion.data.db.entity.MoodSnapshotEntity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DreamDataCollectorTest {

    @Test
    fun collectLast7Days_returnsEmptyWhenNoData() = runTest {
        val moodDao = mockk<MoodSnapshotDao> {
            coEvery { findInRange(any(), any(), any()) } returns emptyList()
        }
        val messageDao = mockk<MessageDao> {
            coEvery { getRecentMessages(any(), any()) } returns emptyList()
        }
        val memoryDao = mockk<MemoryDao> {
            coEvery { countAll() } returns 0
        }
        val collector = DreamDataCollector(moodDao, messageDao, memoryDao)

        val snapshot = collector.collectLast7Days()

        assertTrue(snapshot.isEmpty)
        assertEquals(0, snapshot.moodSnapshots.size)
        assertEquals(0, snapshot.messages.size)
    }

    @Test
    fun collectLast7Days_aggregatesMoodAndCountsMemory() = runTest {
        val now = 1_700_000_000_000L
        val moodDao = mockk<MoodSnapshotDao> {
            coEvery { findInRange(any(), any(), any()) } returns listOf(
                MoodSnapshotEntity(id = "m1", companionId = "default", mood = "happy", intensity = 0.7f, timestamp = now - 86_400_000L),
                MoodSnapshotEntity(id = "m2", companionId = "default", mood = "sad", intensity = 0.3f, timestamp = now - 2 * 86_400_000L),
            )
        }
        val messageDao = mockk<MessageDao> {
            coEvery { getRecentMessages(any(), any()) } returns listOf(
                MessageEntity(id = "msg1", sessionId = "default", role = MessageRole.USER, content = "今天有点累", timestamp = now - 86_400_000L),
            )
        }
        val memoryDao = mockk<MemoryDao> {
            coEvery { countAll() } returns 12
        }
        val collector = DreamDataCollector(moodDao, messageDao, memoryDao)

        val snapshot = collector.collectLast7Days(now = now)

        assertEquals(2, snapshot.moodSnapshots.size)
        assertEquals(1, snapshot.messages.size)
        assertEquals(12, snapshot.memoryCount)
        assertFalse(snapshot.isEmpty)
    }

    @Test
    fun collectLast7Days_extractsTopKeywords() = runTest {
        val moodDao = mockk<MoodSnapshotDao> {
            coEvery { findInRange(any(), any(), any()) } returns emptyList()
        }
        val messageDao = mockk<MessageDao> {
            coEvery { getRecentMessages(any(), any()) } returns listOf(
                MessageEntity(id = "msg1", sessionId = "default", role = MessageRole.USER, content = "工作压力很大很累 工作", timestamp = 1L),
                MessageEntity(id = "msg2", sessionId = "default", role = MessageRole.USER, content = "工作今天加班", timestamp = 2L),
            )
        }
        val memoryDao = mockk<MemoryDao> {
            coEvery { countAll() } returns 0
        }
        val collector = DreamDataCollector(moodDao, messageDao, memoryDao)

        val snapshot = collector.collectLast7Days(now = 2L)

        assertTrue(snapshot.topKeywords.contains("工作"))
        assertTrue(snapshot.topKeywords.indexOf("工作") < snapshot.topKeywords.size)
    }

    @Test
    fun render_containsKeySections() = runTest {
        val moodDao = mockk<MoodSnapshotDao> {
            coEvery { findInRange(any(), any(), any()) } returns emptyList()
        }
        val messageDao = mockk<MessageDao> {
            coEvery { getRecentMessages(any(), any()) } returns emptyList()
        }
        val memoryDao = mockk<MemoryDao> {
            coEvery { countAll() } returns 5
        }
        val collector = DreamDataCollector(moodDao, messageDao, memoryDao)

        val snapshot = collector.collectLast7Days()
        val rendered = collector.render(snapshot)

        assertTrue(rendered.contains("Aura 看到的数据"))
        assertTrue(rendered.contains("情绪快照"))
        assertTrue(rendered.contains("消息"))
        assertTrue(rendered.contains("长期记忆总数:5"))
    }
}
