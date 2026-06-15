package com.xiaoqi.companion.core.insight

import com.xiaoqi.companion.data.db.dao.InsightDao
import com.xiaoqi.companion.data.db.dao.MemoryDao
import com.xiaoqi.companion.data.db.dao.MessageDao
import com.xiaoqi.companion.data.db.dao.MoodSnapshotDao
import com.xiaoqi.companion.data.db.entity.InsightEntity
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class InsightValidatorTest {

    private val messageDao: MessageDao = mockk()
    private val memoryDao: MemoryDao = mockk()
    private val moodSnapshotDao: MoodSnapshotDao = mockk()
    private val insightDao: InsightDao = mockk()
    private val validator = InsightValidator(messageDao, memoryDao, moodSnapshotDao, insightDao)

    private fun emptyEvidenceDraft() = InsightDraft(
        triggerType = "PATTERN_DETECT",
        category = "情绪",
        headline = "周日情绪偏低",
        bodyMarkdown = "连续 3 个周日 intensity < 0.4",
        relevanceWindow = "近 21 天",
        confidence = 0.8f,
    )

    @Test
    fun validate_emptyEvidence_returnsNull() = runTest {
        val draft = emptyEvidenceDraft()

        val result = validator.validate(draft)

        assertNull(result)
    }

    @Test
    fun validate_allEvidenceHallucinated_returnsNull() = runTest {
        val draft = emptyEvidenceDraft().copy(
            evidenceMessageIds = listOf("m1", "m2"),
            evidenceMemoryIds = listOf("mem1"),
        )
        coEvery { messageDao.existsById(any()) } returns false
        coEvery { memoryDao.existsById(any()) } returns false

        val result = validator.validate(draft)

        assertNull(result)
    }

    @Test
    fun validate_halfEvidenceMissing_returnsDraft() = runTest {
        // 4 个 id 全部声称存在,只有 2 个真实 → 50% 命中,刚好过门槛
        val draft = emptyEvidenceDraft().copy(
            evidenceMessageIds = listOf("m-real", "m-fake"),
            evidenceMemoryIds = listOf("mem-real", "mem-fake"),
        )
        coEvery { messageDao.existsById("m-real") } returns true
        coEvery { messageDao.existsById("m-fake") } returns false
        coEvery { memoryDao.existsById("mem-real") } returns true
        coEvery { memoryDao.existsById("mem-fake") } returns false
        every { insightDao.observeRecentHeadings(any()) } returns flowOf(emptyList())

        val result = validator.validate(draft)

        assertNotNull(result)
    }

    @Test
    fun validate_lowConfidence_returnsNull() = runTest {
        val draft = emptyEvidenceDraft().copy(
            evidenceMoodSnapshotIds = listOf("mood-1"),
            confidence = 0.55f,  // < 0.6
        )
        coEvery { moodSnapshotDao.existsById("mood-1") } returns true

        val result = validator.validate(draft)

        assertNull(result)
    }

    @Test
    fun validate_duplicateHeading_returnsNull() = runTest {
        val draft = emptyEvidenceDraft().copy(
            evidenceMoodSnapshotIds = listOf("mood-1"),
            headline = "周日傍晚情绪偏低",
        )
        coEvery { moodSnapshotDao.existsById("mood-1") } returns true
        every {
            insightDao.observeRecentHeadings(any())
        } returns flowOf(listOf("周日傍晚情绪偏低", "其他标题"))

        val result = validator.validate(draft)

        assertNull(result)
    }

    @Test
    fun validate_validDraft_returnsDraft() = runTest {
        val draft = emptyEvidenceDraft().copy(
            evidenceMessageIds = listOf("m1", "m2"),
            headline = "周一通常没精神",
            confidence = 0.7f,
        )
        coEvery { messageDao.existsById(any()) } returns true
        every {
            insightDao.observeRecentHeadings(any())
        } returns flowOf(listOf("完全不同的洞察", "其他标题"))

        val result = validator.validate(draft)

        assertNotNull(result)
        assertEquals("周一通常没精神", result!!.headline)
    }

    @Test
    fun validate_halfEvidenceMissingBelowThreshold_returnsNull() = runTest {
        // 4 个 id, 只有 1 个真实 → 25% 命中,不过门槛
        val draft = emptyEvidenceDraft().copy(
            evidenceMessageIds = listOf("m-real", "m-fake-1", "m-fake-2"),
            evidenceMemoryIds = listOf("mem-fake"),
        )
        coEvery { messageDao.existsById("m-real") } returns true
        coEvery { messageDao.existsById("m-fake-1") } returns false
        coEvery { messageDao.existsById("m-fake-2") } returns false
        coEvery { memoryDao.existsById("mem-fake") } returns false

        val result = validator.validate(draft)

        assertNull(result)
    }

    @Test
    fun validate_jaccardBelowThreshold_passesDuplicateCheck() = runTest {
        // 50% 重叠 → Jaccard = 1 / (|A| + |B| - 1) 不会到 0.8
        val draft = emptyEvidenceDraft().copy(
            evidenceMessageIds = listOf("m1"),
            headline = "周日通常没精神晚上有聚会",
        )
        coEvery { messageDao.existsById("m1") } returns true
        every {
            insightDao.observeRecentHeadings(any())
        } returns flowOf(listOf("周一晚上有聚会"))

        val result = validator.validate(draft)

        assertNotNull(result)
    }
}
