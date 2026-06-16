package com.xiaoqi.companion.core.companion

import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.data.db.dao.MoodSnapshotDao
import com.xiaoqi.companion.data.db.entity.MoodSnapshotEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmotionStateMachineImpl @Inject constructor(
    private val moodSnapshotDao: MoodSnapshotDao,
) : EmotionStateMachine {

    override var currentMood: String = "neutral"
        private set

    override var latestIntensity: Float = 0.5f
        private set

    private val recentHistory = ArrayDeque<MoodEntry>(MAX_HISTORY)

    override suspend fun record(mood: String, intensity: Float, reason: String) {
        val normalized = mood.ifBlank { "neutral" }
        val previous = currentMood
        currentMood = normalized
        latestIntensity = intensity.coerceIn(0f, 1f)

        recentHistory.addLast(MoodEntry(normalized, latestIntensity, reason, System.currentTimeMillis()))
        while (recentHistory.size > MAX_HISTORY) recentHistory.removeFirst()

        // 持久化到 mood_snapshots
        moodSnapshotDao.insert(
            MoodSnapshotEntity(
                id = UUID.randomUUID().toString(),
                companionId = DEFAULT_COMPANION_ID,
                mood = normalized,
                trigger = reason.ifBlank { null },
                intensity = latestIntensity,
                timestamp = System.currentTimeMillis(),
            ),
        )

        if (previous != normalized) {
            AppLogger.debug(
                LogTags.Emotion,
                "mood_changed",
                "previousMood" to previous,
                "currentMood" to normalized,
                "intensity" to latestIntensity,
                "reason" to reason,
            )
        }
    }

    override fun getContext(): String {
        if (recentHistory.isEmpty()) return ""
        val recent = recentHistory.takeLast(CONTEXT_WINDOW)
        return buildString {
            append("最近情绪：")
            recent.forEachIndexed { i, entry ->
                if (i > 0) append(" → ")
                append(entry.mood)
                if (entry.reason.isNotBlank()) append("(${entry.reason})")
            }
        }
    }

    private data class MoodEntry(
        val mood: String,
        val intensity: Float,
        val reason: String,
        val timestamp: Long,
    )

    private companion object {
        const val MAX_HISTORY = 20
        const val CONTEXT_WINDOW = 5
        const val DEFAULT_COMPANION_ID = "default"
    }
}
