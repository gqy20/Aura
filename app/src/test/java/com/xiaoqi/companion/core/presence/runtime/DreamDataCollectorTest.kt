package com.xiaoqi.companion.core.presence.runtime

import com.xiaoqi.companion.data.datastore.AppPreferences
import com.xiaoqi.companion.data.db.converter.MessageRole
import com.xiaoqi.companion.data.db.converter.MemoryType
import com.xiaoqi.companion.data.db.dao.HealthSnapshotDao
import com.xiaoqi.companion.data.db.dao.MemoryDao
import com.xiaoqi.companion.data.db.dao.MessageDao
import com.xiaoqi.companion.data.db.dao.MoodSnapshotDao
import com.xiaoqi.companion.data.db.entity.MemoryEntity
import com.xiaoqi.companion.data.db.entity.MessageEntity
import com.xiaoqi.companion.data.db.entity.MoodSnapshotEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DreamDataCollectorTest {

    private val appPreferences: AppPreferences = mockk {
        every { currentSessionId } returns flowOf("default")
    }

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
            coEvery { getRecentImages(any()) } returns emptyList()
        }
        val collector = DreamDataCollector(moodDao, messageDao, memoryDao, mockk<HealthSnapshotDao>(relaxed = true), appPreferences)

        val snapshot = collector.collectLast7Days()

        assertTrue(snapshot.isEmpty)
        assertEquals(0, snapshot.moodSnapshots.size)
        assertEquals(0, snapshot.messages.size)
        assertEquals(0, snapshot.imageMemories.size)
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
            coEvery { getRecentImages(any()) } returns emptyList()
        }
        val collector = DreamDataCollector(moodDao, messageDao, memoryDao, mockk<HealthSnapshotDao>(relaxed = true), appPreferences)

        val snapshot = collector.collectLast7Days(now = now)

        assertEquals(2, snapshot.moodSnapshots.size)
        assertEquals(1, snapshot.messages.size)
        assertEquals(12, snapshot.memoryCount)
        assertFalse(snapshot.isEmpty)
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
            coEvery { getRecentImages(any()) } returns emptyList()
        }
        val collector = DreamDataCollector(moodDao, messageDao, memoryDao, mockk<HealthSnapshotDao>(relaxed = true), appPreferences)

        val snapshot = collector.collectLast7Days()
        val rendered = collector.render(snapshot)

        assertTrue(rendered.contains("Aura 看到的数据"))
        assertTrue(rendered.contains("情绪快照"))
        assertTrue(rendered.contains("消息"))
        assertTrue(rendered.contains("长期记忆总数:5"))
    }

    @Test
    fun collectLast7Days_includesImageMemoriesWithinWindow() = runTest {
        val now = 1_700_000_000_000L
        val threeDaysAgo = now - 3 * 86_400_000L
        val tenDaysAgo = now - 10 * 86_400_000L
        val moodDao = mockk<MoodSnapshotDao> {
            coEvery { findInRange(any(), any(), any()) } returns emptyList()
        }
        val messageDao = mockk<MessageDao> {
            coEvery { getRecentMessages(any(), any()) } returns emptyList()
        }
        val memoryDao = mockk<MemoryDao> {
            coEvery { countAll() } returns 0
            coEvery { getRecentImages(any()) } returns listOf(
                MemoryEntity(
                    id = "img-in-1",
                    type = MemoryType.FACT,
                    content = "[图片] 看夕阳",
                    timestamp = now - 86_400_000L,
                    importance = 0.7f,
                    imageBase64 = "aW5uZXIx",
                    imageMediaType = "image/jpeg",
                ),
                MemoryEntity(
                    id = "img-in-2",
                    type = MemoryType.FACT,
                    content = "[图片] 咖啡",
                    timestamp = threeDaysAgo,
                    importance = 0.5f,
                    imageBase64 = "aW5uZXIy",
                    imageMediaType = "image/jpeg",
                ),
                // 窗口外(>7 天),应被过滤
                MemoryEntity(
                    id = "img-out",
                    type = MemoryType.FACT,
                    content = "[图片] 旧图",
                    timestamp = tenDaysAgo,
                    importance = 0.3f,
                    imageBase64 = "b3V0",
                    imageMediaType = "image/jpeg",
                ),
            )
        }
        val collector = DreamDataCollector(moodDao, messageDao, memoryDao, mockk<HealthSnapshotDao>(relaxed = true), appPreferences)

        val snapshot = collector.collectLast7Days(now = now)

        assertEquals(2, snapshot.imageMemories.size)
        assertEquals(listOf("img-in-1", "img-in-2"), snapshot.imageMemories.map { it.id })
        // 重要性被映射过去
        assertEquals(0.7f, snapshot.imageMemories[0].importance, 0.001f)
    }

    @Test
    fun collectLast7Days_passesImageLimitToDao() = runTest {
        val moodDao = mockk<MoodSnapshotDao> {
            coEvery { findInRange(any(), any(), any()) } returns emptyList()
        }
        val messageDao = mockk<MessageDao> {
            coEvery { getRecentMessages(any(), any()) } returns emptyList()
        }
        val memoryDao = mockk<MemoryDao> {
            coEvery { countAll() } returns 0
            coEvery { getRecentImages(any()) } returns emptyList()
        }
        val collector = DreamDataCollector(moodDao, messageDao, memoryDao, mockk<HealthSnapshotDao>(relaxed = true), appPreferences)

        collector.collectLast7Days()

        // 限制 5 张 — 防止 prompt 膨胀
        coVerify { memoryDao.getRecentImages(DreamDataCollector.IMAGE_MEMORY_LIMIT) }
    }

    @Test
    fun collectLast7Days_imageMemoriesExcludeBase64() = runTest {
        val now = 1_700_000_000_000L
        val secretBase64 = "VERY_SECRET_BASE64_PAYLOAD_THAT_MUST_NEVER_LEAK"
        val moodDao = mockk<MoodSnapshotDao> {
            coEvery { findInRange(any(), any(), any()) } returns emptyList()
        }
        val messageDao = mockk<MessageDao> {
            coEvery { getRecentMessages(any(), any()) } returns emptyList()
        }
        val memoryDao = mockk<MemoryDao> {
            coEvery { countAll() } returns 0
            coEvery { getRecentImages(any()) } returns listOf(
                MemoryEntity(
                    id = "img1",
                    type = MemoryType.FACT,
                    content = "[图片] 看夕阳",
                    timestamp = now - 86_400_000L,
                    importance = 0.7f,
                    imageBase64 = secretBase64,
                    imageMediaType = "image/jpeg",
                ),
            )
        }
        val collector = DreamDataCollector(moodDao, messageDao, memoryDao, mockk<HealthSnapshotDao>(relaxed = true), appPreferences)

        val snapshot = collector.collectLast7Days(now = now)

        // ImageMemorySummary 字段检查 — content 不应等于 base64
        val summary = snapshot.imageMemories.single()
        assertNotEquals(secretBase64, summary.content)
        // toString 也不应含 base64(防止意外日志泄漏)
        assertFalse(summary.toString().contains(secretBase64))
    }

    @Test
    fun render_includesVisualSection_whenImagesPresent() = runTest {
        val now = 1_700_000_000_000L
        val snapshot = DreamDataCollector.Snapshot(
            rangeStart = now - 7 * 86_400_000L,
            rangeEnd = now,
            moodSnapshots = emptyList(),
            messages = emptyList(),
            memoryCount = 0,
            imageMemories = listOf(
                DreamDataCollector.ImageMemorySummary(
                    id = "img-1",
                    content = "[图片] 看夕阳",
                    timestamp = now - 86_400_000L,
                    importance = 0.7f,
                ),
                DreamDataCollector.ImageMemorySummary(
                    id = "img-2",
                    content = "[图片] 早餐咖啡",
                    timestamp = now - 2 * 86_400_000L,
                    importance = 0.5f,
                ),
            ),
        )
        val moodDao = mockk<MoodSnapshotDao>()
        val messageDao = mockk<MessageDao>()
        val memoryDao = mockk<MemoryDao>()
        val collector = DreamDataCollector(moodDao, messageDao, memoryDao, mockk<HealthSnapshotDao>(relaxed = true), appPreferences)

        val rendered = collector.render(snapshot)

        assertTrue(rendered.contains("## 视觉证据(2 张)"))
        assertTrue(rendered.contains("[图片] 看夕阳"))
        assertTrue(rendered.contains("[图片] 早餐咖啡"))
    }

    @Test
    fun render_omitsVisualSection_whenNoImages() = runTest {
        val moodDao = mockk<MoodSnapshotDao>()
        val messageDao = mockk<MessageDao>()
        val memoryDao = mockk<MemoryDao>()
        val collector = DreamDataCollector(moodDao, messageDao, memoryDao, mockk<HealthSnapshotDao>(relaxed = true), appPreferences)
        val snapshot = DreamDataCollector.Snapshot(
            rangeStart = 0L,
            rangeEnd = 1L,
            moodSnapshots = emptyList(),
            messages = emptyList(),
            memoryCount = 3,
        )

        val rendered = collector.render(snapshot)

        assertFalse(rendered.contains("视觉证据"))
    }

    @Test
    fun render_doesNotLeakBase64_evenWhenSummaryContainsBase64() = runTest {
        val now = 1_700_000_000_000L
        // 安全护栏:即使 ImageMemorySummary.content 异常地包含 base64 字符,
        // render 也不应把 imageBase64 字段(本地 LLM 完全吃不下)泄漏出去。
        // 这里我们用 Snapshot 直接构造,模拟异常情况。
        val leakedBase64 = "AAAA_BASE64_THAT_NEVER_APPEARS_IN_PROMPT"
        val snapshot = DreamDataCollector.Snapshot(
            rangeStart = now - 7 * 86_400_000L,
            rangeEnd = now,
            moodSnapshots = emptyList(),
            messages = emptyList(),
            memoryCount = 0,
            imageMemories = listOf(
                DreamDataCollector.ImageMemorySummary(
                    id = "img-1",
                    content = "[图片] 正常摘要",  // 不含 base64
                    timestamp = now - 86_400_000L,
                    importance = 0.7f,
                ),
            ),
        )
        val collector = DreamDataCollector(
            mockk<MoodSnapshotDao>(),
            mockk<MessageDao>(),
            mockk<MemoryDao>(),
            mockk<HealthSnapshotDao>(),
            appPreferences,
        )

        val rendered = collector.render(snapshot)

        assertFalse(
            "render output leaked base64 placeholder",
            rendered.contains(leakedBase64),
        )
    }
}
