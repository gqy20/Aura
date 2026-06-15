package com.xiaoqi.companion.data.db.dao

import com.xiaoqi.companion.data.db.BaseAndroidDaoTest
import com.xiaoqi.companion.data.db.converter.MessageRole
import com.xiaoqi.companion.data.db.entity.MessageEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `message_search_docs_fts`（FTS5 + trigram）真行为验证。
 *
 * Robolectric 用的 sqlite4java 是 SQLite 3.7.x，**不支持** FTS5 + UPSERT 语法。
 * 这些场景只能在 androidTest 跑：
 * - `MessageSearchDao.upsertSearchDoc` 的 `ON CONFLICT DO UPDATE`
 * - `message_search_docs_fts` 虚拟表 + bm25 排序
 * - 中文 trigram tokenizer
 *
 * 断言**不锁 bm25 浮点数值**——只验方向（rank 越负 → 越相关）和集合形态。
 */
class MessageSearchDaoFts5Test : BaseAndroidDaoTest() {

    private lateinit var messageDao: MessageDao
    private lateinit var searchDao: MessageSearchDao

    override fun initDaos() {
        messageDao = db.messageDao()
        searchDao = db.messageSearchDao()
    }

    // --- helpers ---

    private fun seed(
        id: String,
        content: String,
        sessionId: String = "default",
        role: MessageRole = MessageRole.USER,
        imageBase64: String? = null,
        timestamp: Long = 1_000L,
    ) = runBlocking {
        val msg = MessageEntity(
            id = id,
            sessionId = sessionId,
            role = role,
            content = content,
            imageBase64 = imageBase64,
            timestamp = timestamp,
        )
        messageDao.insert(msg)
        searchDao.index(msg)
    }

    private fun hits(query: String, sessionId: String = "default") = runBlocking {
        searchDao.searchRecordsFts(
            sessionId = sessionId,
            matchQuery = query,
            role = null,
            after = null,
            before = null,
            hasImage = null,
            limit = 20,
        )
    }

    // --- 1. trigram 命中 ---

    @Test
    fun insert_indexedTrigramMatchesChineseSubstring() = runBlocking {
        seed("m1", "今天天气很好,适合出门散步")
        seed("m2", "昨天买了本书,还没开始看")
        seed("m3", "Tomorrow is another day")

        val matched = hits("天气")
        assertEquals(1, matched.size)
        assertEquals("m1", matched.single().id)

        // 不足 3 字符的子串,trigram tokenizer 不命中
        val short = hits("天")
        assertTrue("单字符不应被 trigram 命中: $short", short.isEmpty())
    }

    @Test
    fun insert_indexedTrigramMatchesEnglishTrigram() = runBlocking {
        seed("m1", "The quick brown fox jumps over the lazy dog")
        seed("m2", "Hello world from Kotlin")

        // trigram 切分 3 字符子串,英文也按这个规则
        val fox = hits("fox")
        assertEquals(1, fox.size)
        assertEquals("m1", fox.single().id)
    }

    // --- 2. UPSERT 替换后索引更新 ---

    @Test
    fun insert_replaceConflict_updatesFtsIndex() = runBlocking {
        val original = MessageEntity(
            id = "m1",
            sessionId = "default",
            role = MessageRole.USER,
            content = "今天天气很好",
            timestamp = 1_000L,
        )
        messageDao.insert(original)
        searchDao.index(original)

        // 替换为完全不相关的内容,旧词不应再被命中
        val updated = original.copy(content = "I love eating pizza", timestamp = 2_000L)
        messageDao.insert(updated)
        searchDao.index(updated)

        val oldHits = hits("天气")
        assertTrue("替换后旧词不应被 FTS 命中: $oldHits", oldHits.isEmpty())

        val newHits = hits("pizza")
        assertEquals(1, newHits.size)
        assertEquals("m1", newHits.single().id)
        assertEquals(2_000L, newHits.single().timestamp)
    }

    // --- 3. 组合过滤 ---

    @Test
    fun searchRecordsFts_filtersByRoleHasImageTimeRangeSession() = runBlocking {
        seed("user-1", "今天天气很好", role = MessageRole.USER, timestamp = 1_000L)
        seed("assistant-1", "今天确实适合出门", role = MessageRole.ASSISTANT, timestamp = 2_000L)
        seed(
            "user-2",
            "天气不错,来张图片",
            role = MessageRole.USER,
            imageBase64 = "img-base64",
            timestamp = 3_000L,
        )
        seed(
            "other-session",
            "天气和这里一样",
            sessionId = "other",
            role = MessageRole.USER,
            timestamp = 4_000L,
        )

        // session 隔离
        val otherSession = hits("天气", sessionId = "other")
        assertEquals(listOf("other-session"), otherSession.map { it.id })

        // role 过滤
        val onlyAssistant = searchDao.searchRecordsFts(
            sessionId = "default",
            matchQuery = "天气",
            role = MessageRole.ASSISTANT,
            after = null,
            before = null,
            hasImage = null,
            limit = 20,
        )
        assertEquals(listOf("assistant-1"), onlyAssistant.map { it.id })

        // hasImage 过滤
        val imageOnly = searchDao.searchRecordsFts(
            sessionId = "default",
            matchQuery = "天气",
            role = null,
            after = null,
            before = null,
            hasImage = true,
            limit = 20,
        )
        assertEquals(listOf("user-2"), imageOnly.map { it.id })

        // 时间范围
        val timeFiltered = searchDao.searchRecordsFts(
            sessionId = "default",
            matchQuery = "天气",
            role = null,
            after = 2_500L,
            before = null,
            hasImage = null,
            limit = 20,
        )
        assertEquals(listOf("user-2"), timeFiltered.map { it.id })
    }

    // --- 4. bm25 排序方向 ---

    @Test
    fun searchRecordsFts_bm25Rank_ordersMoreNegativeFirst() = runBlocking {
        // 多次出现"天气"的文档比只出现一次的更相关 → bm25 rank 更负
        seed("m-frequent", "今天天气真好,天气晴朗,适合出去看看天气")
        seed("m-once", "今天心情不错")
        seed("m-unrelated", "Kotlin 协程真不错")

        val matched = hits("天气")
        assertEquals(2, matched.size)
        assertEquals("m-frequent", matched.first().id)
        // 多次命中 → rank 更负（更小）
        assertTrue(
            "频繁命中者 rank 应更小: ${matched.map { it.id to it.ftsRank }}",
            matched[0].ftsRank < matched[1].ftsRank,
        )
    }

    // --- 5. unindexSession 清 FTS rowid ---

    @Test
    fun unindexSession_removesFtsRows() = runBlocking {
        seed("m1", "今天天气很好", sessionId = "s1")
        seed("m2", "天气不错", sessionId = "s1")
        seed("m3", "天气晴朗", sessionId = "s2")

        runBlocking { searchDao.unindexSession("s1") }

        val s1Hits = hits("天气", sessionId = "s1")
        assertTrue("s1 的 FTS 行应被清空: $s1Hits", s1Hits.isEmpty())

        // s2 的索引不受影响
        val s2Hits = hits("天气", sessionId = "s2")
        assertEquals(listOf("m3"), s2Hits.map { it.id })
    }

    // --- 6. MessageSearchHit 双 JOIN binding ---

    @Test
    fun searchRecordsFts_messageSearchHit_carriesImageBase64AndContent() = runBlocking {
        seed(
            "img-msg",
            "今天天气很好,顺便拍张照",
            imageBase64 = "data:image/jpeg;base64,/9j/abc==",
        )

        val matched = hits("天气")
        assertEquals(1, matched.size)
        val hit = matched.single()
        assertNotNull(hit.imageBase64)
        assertTrue(
            "imageBase64 应被原样透传: ${hit.imageBase64}",
            hit.imageBase64!!.contains("data:image/jpeg"),
        )
        assertEquals("今天天气很好,顺便拍张照", hit.content)
        assertEquals("img-msg", hit.id)
        assertEquals("default", hit.sessionId)
    }

    // --- 7. migration 9→10 由 BaseAndroidDaoTest 的 callback 复刻(见 BaseAndroidDaoTest 注释) ---
}
