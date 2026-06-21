package com.xiaoqi.companion.core.insight

import com.xiaoqi.companion.core.presence.runtime.DreamDataCollector.Snapshot
import com.xiaoqi.companion.data.db.dao.MemoryDao
import com.xiaoqi.companion.data.db.dao.MessageSearchDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Post-Hoc Evidence Resolution — 从 LLM 文本输出反查真实 DB ID。
 *
 * 根本问题：LLM（尤其 0.8B/2B 小模型）无法返回真实的 message/memory/mood ID，
 * 因为 `DreamDataCollector.render()` 传给 LLM 的输入数据里不含这些 ID。
 * 即使 prompt 要求 `evidence_ids`，模型要么跳过、要么编造 → Validator 全部拒绝。
 *
 * 本组件在 **parsePatternDetectOutput 之后、InsightValidator 之前** 插入一个确定性步骤：
 *   1. 从 draft 的 headline + body 提取关键词
 *   2. 用 FTS5 / LIKE / 内存匹配反查各表的真实 ID
 *   3. 回填到 InsightDraft 的三个 evidence 列表中
 *
 * 参考 `seedDemoInsights()` 已证明的结论：**只要给 Validator 真实 ID，100% 通过校验**。
 */
@Singleton
class EvidenceResolver @Inject constructor(
    private val messageSearchDao: MessageSearchDao,
    private val memoryDao: MemoryDao,
) {

    suspend fun resolve(
        drafts: List<InsightDraft>,
        snapshot: Snapshot,
        sessionId: String = "default",
    ): List<InsightDraft> = withContext(Dispatchers.IO) {
        drafts.map { resolveSingle(it, snapshot, sessionId) }
    }

    private suspend fun resolveSingle(
        draft: InsightDraft,
        snapshot: Snapshot,
        sessionId: String,
    ): InsightDraft {
        // 如果 LLM 已经给出了看起来像真实 ID 的 evidence（比如从 render() 的 [id:xxx] 复制的），
        // 先保留它们；resolution 只做补充，不覆盖已有 ID。
        val hasExistingEvidence = draft.evidenceMessageIds.isNotEmpty()
            || draft.evidenceMemoryIds.isNotEmpty()
            || draft.evidenceMoodSnapshotIds.isNotEmpty()

        if (hasExistingEvidence) return draft

        val keywords = extractKeywords(draft.headline, draft.bodyMarkdown)
        if (keywords.isEmpty()) return draft

        return draft.copy(
            evidenceMessageIds = resolveMessageEvidence(keywords, snapshot, sessionId),
            evidenceMemoryIds = resolveMemoryEvidence(keywords),
            evidenceMoodSnapshotIds = resolveMoodEvidence(keywords, snapshot),
        )
    }

    // ── Message: FTS5 trigram 搜索 ──

    private suspend fun resolveMessageEvidence(
        keywords: List<String>,
        snapshot: Snapshot,
        sessionId: String,
    ): List<String> {
        val ftsQuery = buildFtsQuery(keywords)
        if (ftsQuery.isBlank()) return emptyList()

        return runCatching {
            messageSearchDao.searchRecordsFts(
                sessionId = sessionId,
                matchQuery = ftsQuery,
                role = null,
                after = snapshot.rangeStart,
                before = snapshot.rangeEnd,
                hasImage = null,
                limit = MAX_EVIDENCE_PER_TYPE,
            ).map { it.id }
        }.getOrDefault(emptyList())
    }

    // ── Memory: LIKE 子串匹配 ──

    private suspend fun resolveMemoryEvidence(
        keywords: List<String>,
    ): List<String> {
        for (keyword in keywords.take(MAX_KEYWORDS_FOR_SEARCH)) {
            val results = runCatching {
                memoryDao.searchByContent(
                    pattern = "%$keyword%",
                    type = null,
                    limit = MAX_EVIDENCE_PER_TYPE,
                )
            }.getOrDefault(emptyList())
            if (results.isNotEmpty()) return results.map { it.id }
        }
        return emptyList()
    }

    // ── MoodSnapshot: 内存过滤（DAO 无搜索接口，直接用 snapshot 数据）──

    private fun resolveMoodEvidence(
        keywords: List<String>,
        snapshot: Snapshot,
    ): List<String> {
        if (snapshot.moodSnapshots.isEmpty()) return emptyList()

        // 策略 A: mood 标签同义词匹配
        val labelMatches = snapshot.moodSnapshots.filter { ms ->
            keywords.any { kw ->
                ms.mood.equals(kw, ignoreCase = true) ||
                    kw.contains(ms.mood, ignoreCase = true) ||
                    MOOD_SYNONYMS[kw]?.contains(ms.mood) == true
            }
        }

        // 策略 B: intensity 方向关键词匹配
        val intensityKeywords = keywords.filter { it in LOW_INTENSITY_WORDS || it in HIGH_INTENSITY_WORDS }
        val intensityMatches = if (intensityKeywords.isNotEmpty()) {
            val wantLow = intensityKeywords.any { it in LOW_INTENSITY_WORDS }
            snapshot.moodSnapshots.filter {
                if (wantLow) it.intensity < 0.45f else it.intensity > 0.65f
            }
        } else emptyList()

        return (labelMatches + intensityMatches)
            .distinctBy { it.id }
            .take(MAX_EVIDENCE_PER_TYPE)
            .map { it.id }
    }

    // ── 关键词提取 ──

    internal fun extractKeywords(headline: String, body: String): List<String> {
        val text = "$headline $body".lowercase()
        return text.split(WORD_SPLITTER)
            .map { it.trim() }
            .filter { it.length >= 2 }
            .filter { !STOP_WORDS.contains(it) }
            .filter { !NUMBER_PATTERN.matches(it) }
            .distinct()
            .take(MAX_KEYWORDS)
    }

    // ── FTS query 构造 ──

    private fun buildFtsQuery(keywords: List<String>): String =
        keywords
            .take(MAX_FTS_TERMS)
            .filter { kw -> kw.all { c -> c.isLetterOrDigit() || c == '_' } }
            .map { "\"$it\"" }
            .ifEmpty { return "" }
            .joinToString(" ")

    companion object {
        const val MAX_EVIDENCE_PER_TYPE = 3
        const val MAX_KEYWORDS = 8
        const val MAX_KEYWORDS_FOR_SEARCH = 4
        const val MAX_FTS_TERMS = 3

        private val WORD_SPLITTER = Regex("[\\s\\p{Punct}\\u3000-\\u303f\\uff00-\\uffef]+")
        private val NUMBER_PATTERN = Regex("\\d+")

        /** 中文/英文停用词 + 领域噪声词 */
        val STOP_WORDS = setOf(
            // 中文功能词
            "的", "了", "是", "在", "有", "和", "与", "或", "但", "而",
            "这", "那", "个", "上", "下", "中", "来", "去", "对", "把",
            "被", "让", "给", "从", "到", "向", "为", "以", "及", "等",
            "很", "都", "也", "会", "能", "可以", "可能", "应该", "需要",
            // 英文停用词
            "the", "a", "an", "is", "are", "was", "were", "be", "been",
            "being", "have", "has", "had", "do", "does", "did", "will",
            "would", "could", "should", "may", "might", "shall", "can",
            "to", "of", "in", "for", "on", "with", "at", "by", "from",
            // 领域噪声（insight 文本中常见但无检索价值）
            "近", "天", "周", "月", "用户", "你", "发现", "模式", "数据",
            "条", "次", "比较", "明显", "值得",
        )

        /** mood 标签 → 同义词集映射 */
        private val MOOD_SYNONYMS: Map<String, Set<String>> = mapOf(
            "低" to setOf("sad", "unhappy"),
            "低落" to setOf("sad", "unhappy"),
            "难过" to setOf("sad", "unhappy"),
            "伤心" to setOf("sad", "unhappy"),
            "不好" to setOf("sad", "unhappy"),
            "高" to setOf("happy", "joyful"),
            "好" to setOf("happy", "joyful"),
            "开心" to setOf("happy", "joyful"),
            "高兴" to setOf("happy", "joyful"),
            "愉快" to setOf("happy", "joyful"),
            "焦虑" to setOf("anxious", "worried"),
            "紧张" to setOf("anxious", "worried"),
            "担心" to setOf("anxious", "worried"),
            "平静" to setOf("calm", "peaceful", "neutral"),
            "安静" to setOf("calm", "peaceful", "neutral"),
            "淡定" to setOf("calm", "peaceful", "neutral"),
            "愤怒" to setOf("angry"),
            "生气" to setOf("angry"),
        )

        private val LOW_INTENSITY_WORDS = setOf("低", "偏低", "低落", "焦虑", "紧张", "难过", "伤心")
        private val HIGH_INTENSITY_WORDS = setOf("高", "偏高", "兴奋", "开心", "高兴", "愉快")
    }
}
