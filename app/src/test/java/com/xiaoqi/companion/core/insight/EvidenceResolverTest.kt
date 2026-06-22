package com.xiaoqi.companion.core.insight

import com.xiaoqi.companion.core.presence.runtime.DreamDataCollector.Snapshot
import com.xiaoqi.companion.data.db.dao.MemoryDao
import com.xiaoqi.companion.data.db.dao.MessageSearchDao
import com.xiaoqi.companion.data.db.dao.MessageSearchHit
import com.xiaoqi.companion.data.db.converter.MessageRole
import com.xiaoqi.companion.data.db.entity.MoodSnapshotEntity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EvidenceResolverTest {

    private val messageSearchDao: MessageSearchDao = mockk()
    private val memoryDao: MemoryDao = mockk()
    private lateinit var resolver: EvidenceResolver

    @Before
    fun setUp() {
        resolver = EvidenceResolver(messageSearchDao, memoryDao)
    }

    // ── 关键词提取 ──

    @Test
    fun extractKeywords_chineseText_returnsContentWords() = runTest {
        val keywords = resolver.extractKeywords(
            "用户最近经常在晚上提到叉烧饭",
            "用户在请求生成地图链接时，明确指定了步行至三大爹叉烧滑蛋饭",
        )

        assertTrue("应提取内容词", keywords.any { it.contains("叉烧") || it.contains("散步") || it.contains("路线") })
        assertTrue("应过滤停用词", !keywords.contains("的"))
        assertTrue("应过滤停用词", !keywords.contains("在"))
        assertTrue("应过滤停用词", !keywords.contains("了"))
        assertTrue("应过滤领域噪声词 '用户'", !keywords.contains("用户"))
    }

    @Test
    fun extractKeywords_filtersStopWords() = runTest {
        val keywords = resolver.extractKeywords(
            "这是一个关于用户的发现",
            "用户在最近的对话中提到了很多有趣的事情",
        )

        assertTrue("停用词 '的' 应被过滤", !keywords.contains("的"))
        assertTrue("停用词 '是' 应被过滤", !keywords.contains("是"))
    }

    @Test
    fun extractKeywords_filtersPureNumbers() = runTest {
        val keywords = resolver.extractKeywords(
            "2024年6月的数据",
            "步数达到8000步",
        )

        assertTrue("纯数字 2024 应被过滤", !keywords.contains("2024"))
        assertTrue("纯数字 8000 应被过滤", !keywords.contains("8000"))
    }

    @Test
    fun extractKeywords_emptyInput_returnsEmpty() = runTest {
        val keywords = resolver.extractKeywords("", "")
        assertEquals(emptyList<String>(), keywords)
    }

    @Test
    fun extractKeywords_deduplicatesAndLimits() = runTest {
        val keywords = resolver.extractKeywords(
            "关键词A 关键词B 关键词C 关键词D 关键词E 关键词F 关键词G 关键词H 关键词I 关键词J",
            "",
        )

        assertTrue("最多返回 MAX_KEYWORDS(8) 个", keywords.size <= 8)
        assertEquals(keywords.size, keywords.distinct().size)
    }

    // ── FTS 消息回填 ──

    @Test
    fun resolve_withMatchingMessages_fillsMessageIds() = runTest {
        coEvery {
            messageSearchDao.searchRecordsFts(
                sessionId = "default",
                matchQuery = any(),
                role = null,
                after = any(),
                before = any(),
                hasImage = null,
                limit = 3,
            )
        } returns listOf(
            msgHit("msg-uuid-1", "default", MessageRole.USER, "最近睡不着，有点失眠"),
            msgHit("msg-uuid-2", "default", MessageRole.USER, "想吃叉烧饭"),
        )

        val draft = InsightDraft(
            triggerType = "PATTERN_DETECT",
            category = "习惯",
            headline = "用户习惯上倾向于将散步路线与美食选择同步规划",
            bodyMarkdown = "用户在请求生成地图链接时，明确指定了步行至三大爹叉烧滑蛋饭",
            relevanceWindow = "近 7 天",
            confidence = 0.75f,
        )

        val result = resolver.resolve(listOf(draft), emptySnapshot(), "default")

        assertEquals(1, result.size)
        assertTrue("应有 message evidence", result[0].evidenceMessageIds.isNotEmpty())
        assertTrue(result[0].evidenceMessageIds.any { it.startsWith("msg-uuid") })
    }

    @Test
    fun resolve_noMatchingMessages_keepsEmptyMessageIds() = runTest {
        coEvery {
            messageSearchDao.searchRecordsFts(any(), any(), any(), any(), any(), any(), any())
        } returns emptyList()

        val draft = InsightDraft(
            triggerType = "PATTERN_DETECT",
            category = "情绪",
            headline = "完全无关的话题",
            bodyMarkdown = "外星人入侵地球的详细计划",
            relevanceWindow = "近 7 天",
            confidence = 0.6f,
        )

        val result = resolver.resolve(listOf(draft), emptySnapshot(), "default")

        assertEquals(1, result.size)
        assertEquals(emptyList<String>(), result[0].evidenceMessageIds)
    }

    // ── Mood 内存匹配（验证不崩溃 + 字段保持，精确匹配依赖分词器）──

    @Test
    fun resolve_moodSnapshots_doesNotCrash() = runTest {
        coEvery {
            messageSearchDao.searchRecordsFts(any(), any(), any(), any(), any(), any(), any())
        } returns emptyList()

        val draft = InsightDraft(
            triggerType = "PATTERN_DETECT",
            category = "情绪",
            headline = "周日感到很难过很伤心",
            bodyMarkdown = "连续多个周日的情绪记录显示很不开心",
            relevanceWindow = "近 7 天",
            confidence = 0.72f,
        )
        val snapshot = Snapshot(
            rangeStart = now - 604800000L,
            rangeEnd = now,
            moodSnapshots = listOf(
                MoodSnapshotEntity(id="mood-sad-1", companionId="default", mood="sad", intensity=0.3f, timestamp=now - 172800000L),
                MoodSnapshotEntity(id="mood-happy-1", companionId="default", mood="happy", intensity=0.8f, timestamp=now - 86400000L),
            ),
            messages = emptyList(),
            memoryCount = 0,
        )

        // 核心验证：resolution 不崩溃，且非 evidence 字段完整保留
        val result = resolver.resolve(listOf(draft), snapshot, "default")
        assertEquals(1, result.size)
        assertEquals("PATTERN_DETECT", result[0].triggerType)
        assertEquals("情绪", result[0].category)
        assertEquals(0.72f, result[0].confidence, 0.001f)
    }

    @Test
    fun resolve_withLowIntensityMood_doesNotCrash() = runTest {
        coEvery {
            messageSearchDao.searchRecordsFts(any(), any(), any(), any(), any(), any(), any())
        } returns emptyList()

        val draft = InsightDraft(
            triggerType = "PATTERN_DETECT",
            category = "情绪",
            headline = "最近感觉很低落很焦虑紧张",
            bodyMarkdown = "用户表现出明显的低落情绪，强度偏低",
            relevanceWindow = "近 7 天",
            confidence = 0.65f,
        )
        val snapshot = Snapshot(
            rangeStart = now - 604800000L,
            rangeEnd = now,
            moodSnapshots = listOf(
                MoodSnapshotEntity(id="mood-low-1", companionId="default", mood="neutral", intensity=0.35f, timestamp=now - 172800000L),
                MoodSnapshotEntity(id="mood-high-1", companionId="default", mood="neutral", intensity=0.78f, timestamp=now - 86400000L),
            ),
            messages = emptyList(),
            memoryCount = 0,
        )

        val result = resolver.resolve(listOf(draft), snapshot, "default")
        assertEquals(1, result.size)
        assertEquals("最近感觉很低落很焦虑紧张", result[0].headline)
    }

    // ── 已有 evidence 保护 ──

    @Test
    fun resolve_preservesExistingEvidence() = runTest {
        val draft = InsightDraft(
            triggerType = "PATTERN_DETECT",
            category = "情绪",
            headline = "测试洞察",
            bodyMarkdown = "测试内容",
            relevanceWindow = "近 7 天",
            confidence = 0.7f,
            evidenceMessageIds = listOf("existing-msg-id"),
        )

        val result = resolver.resolve(listOf(draft), emptySnapshot(), "default")

        assertEquals(1, result.size)
        assertEquals(listOf("existing-msg-id"), result[0].evidenceMessageIds)
    }

    // ── 多 draft 独立性 ──

    @Test
    fun resolve_multipleDrafts_resolvesIndependently() = runTest {
        var callCount = 0
        coEvery {
            messageSearchDao.searchRecordsFts(matchQuery = any(), sessionId = any(),
                role = any(), after = any(), before = any(), hasImage = any(), limit = any())
        } answers {
            callCount++
            if (callCount == 1) listOf(msgHit("msg-sleep-1", "default", MessageRole.USER, "睡不着"))
            else listOf(msgHit("msg-food-1", "default", MessageRole.USER, "想吃好吃的"))
        }

        val draftSleep = InsightDraft(
            triggerType = "PATTERN_DETECT", category = "习惯",
            headline = "睡眠质量下降", bodyMarkdown = "最近总是睡不着", relevanceWindow = "近 7 天", confidence = 0.7f,
        )
        val draftFood = InsightDraft(
            triggerType = "PATTERN_DETECT", category = "习惯",
            headline = "饮食偏好变化", bodyMarkdown = "频繁提到想吃饭店", relevanceWindow = "近 7 天", confidence = 0.68f,
        )

        val results = resolver.resolve(listOf(draftSleep, draftFood), emptySnapshot(), "default")

        assertEquals(2, results.size)
        assertTrue(results[0].evidenceMessageIds.contains("msg-sleep-1"))
        assertTrue(results[1].evidenceMessageIds.contains("msg-food-1"))
    }

    // ── 空输入降级 ──

    @Test
    fun resolve_emptyDraftsList_returnsEmpty() = runTest {
        val result = resolver.resolve(emptyList<InsightDraft>(), emptySnapshot(), "default")
        assertEquals(emptyList<InsightDraft>(), result)
    }

    @Test
    fun resolve_emptySnapshot_returnsEmptyMoodEvidence() = runTest {
        coEvery {
            messageSearchDao.searchRecordsFts(any(), any(), any(), any(), any(), any(), any())
        } returns emptyList()

        val draft = InsightDraft(
            triggerType = "PATTERN_DETECT", category = "情绪",
            headline = "任何内容", bodyMarkdown = "任何描述", relevanceWindow = "近 7 天", confidence = 0.5f,
        )

        val result = resolver.resolve(listOf(draft), emptySnapshot(), "default")

        assertEquals(1, result.size)
        assertEquals(emptyList<String>(), result[0].evidenceMoodSnapshotIds)
    }

    // ── 字段保持 ──

    @Test
    fun resolve_preservesNonEvidenceFields() = runTest {
        val original = InsightDraft(
            triggerType = "ANNIVERSARY",
            category = "重要日期",
            headline = "生日快到了",
            bodyMarkdown = "下周三是妈妈的生日",
            relevanceWindow = "近 30 天",
            confidence = 0.82f,
        )

        val result = resolver.resolve(listOf(original), emptySnapshot(), "default")

        assertEquals(1, result.size)
        assertEquals("ANNIVERSARY", result[0].triggerType)
        assertEquals("重要日期", result[0].category)
        assertEquals("生日快到了", result[0].headline)
        assertEquals("下周三是妈妈的生日", result[0].bodyMarkdown)
        assertEquals("近 30 天", result[0].relevanceWindow)
        assertEquals(0.82f, result[0].confidence, 0.001f)
    }

    // ── helpers ──

    private fun emptySnapshot() = Snapshot(
        rangeStart = now - 604800000L,
        rangeEnd = now,
        moodSnapshots = emptyList(),
        messages = emptyList(),
        memoryCount = 0,
    )

    private fun msgHit(id: String, sessionId: String, role: MessageRole, content: String) =
        MessageSearchHit(id, sessionId, role, content, null, now - 86400000L, 0.5)

    companion object {
        private val now = System.currentTimeMillis()
    }
}
