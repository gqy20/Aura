package com.xiaoqi.companion.core.insight

import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.data.db.dao.InsightDao
import com.xiaoqi.companion.data.db.dao.MemoryDao
import com.xiaoqi.companion.data.db.dao.MessageDao
import com.xiaoqi.companion.data.db.dao.MoodSnapshotDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Insight 4 道校验(plan §3.3):
 *
 * 1. 缺 evidence → 丢
 * 2. 至少 50% evidence 真实存在(查 messages / memories / mood_snapshots)→ 否则丢
 * 3. confidence < 0.6 → 丢
 * 4. 与近 30 天 VISIBLE/DISMISSED insight heading Jaccard > 0.8 → 丢
 *
 * **纯输入校验, 不写库**。通过则返回同一个 draft,失败返回 null。
 */
@Singleton
class InsightValidator @Inject constructor(
    private val messageDao: MessageDao,
    private val memoryDao: MemoryDao,
    private val moodSnapshotDao: MoodSnapshotDao,
    private val insightDao: InsightDao,
) {

    suspend fun validate(draft: InsightDraft): InsightDraft? {
        if (hasNoEvidence(draft)) {
            AppLogger.debug(
                LogTags.Repo,
                "insight_rejected",
                "stage" to "no_evidence",
                "trigger" to draft.triggerType,
                "category" to draft.category,
            )
            return null
        }
        if (!passesEvidenceRealityCheck(draft)) {
            AppLogger.debug(
                LogTags.Repo,
                "insight_rejected",
                "stage" to "evidence_reality_check",
                "trigger" to draft.triggerType,
            )
            return null
        }
        if (draft.confidence < MIN_CONFIDENCE) {
            AppLogger.debug(
                LogTags.Repo,
                "insight_rejected",
                "stage" to "low_confidence",
                "trigger" to draft.triggerType,
                "confidence" to draft.confidence,
            )
            return null
        }
        if (isDuplicateHeading(draft)) {
            AppLogger.debug(
                LogTags.Repo,
                "insight_rejected",
                "stage" to "duplicate_heading",
                "trigger" to draft.triggerType,
            )
            return null
        }
        return draft
    }

    private fun hasNoEvidence(draft: InsightDraft): Boolean =
        draft.evidenceMessageIds.isEmpty() &&
            draft.evidenceMemoryIds.isEmpty() &&
            draft.evidenceMoodSnapshotIds.isEmpty()

    private suspend fun passesEvidenceRealityCheck(draft: InsightDraft): Boolean {
        val totalClaimed = draft.evidenceMessageIds.size +
            draft.evidenceMemoryIds.size +
            draft.evidenceMoodSnapshotIds.size
        if (totalClaimed == 0) return false

        val realCount = draft.evidenceMessageIds.count { messageDao.existsById(it) } +
            draft.evidenceMemoryIds.count { memoryDao.existsById(it) } +
            draft.evidenceMoodSnapshotIds.count { moodSnapshotDao.existsById(it) }

        return realCount.toDouble() / totalClaimed >= EVIDENCE_REALITY_THRESHOLD
    }

    private suspend fun isDuplicateHeading(draft: InsightDraft): Boolean {
        val since = System.currentTimeMillis() - THIRTY_DAYS_MS
        val recent = insightDao.observeRecentHeadings(since = since).first()
        if (recent.isEmpty()) return false
        val draftTokens = draft.headline.toTokenSet()
        return recent.any { existing ->
            val existingTokens = existing.toTokenSet()
            jaccard(draftTokens, existingTokens) >= HEADING_SIMILARITY_THRESHOLD
        }
    }

    private fun String.toTokenSet(): Set<String> =
        lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.length >= 2 }
            .toSet()

    private fun jaccard(left: Set<String>, right: Set<String>): Double {
        if (left.isEmpty() || right.isEmpty()) return 0.0
        val intersection = left.intersect(right).size.toDouble()
        val union = left.union(right).size.toDouble()
        return if (union == 0.0) 0.0 else intersection / union
    }

    private companion object {
        const val MIN_CONFIDENCE = 0.6f
        const val EVIDENCE_REALITY_THRESHOLD = 0.5
        const val HEADING_SIMILARITY_THRESHOLD = 0.8
        const val THIRTY_DAYS_MS = 30L * 24L * 60L * 60L * 1000L
    }
}
