package com.xiaoqi.companion.core.presence.runtime

import com.xiaoqi.companion.data.datastore.AppPreferences
import com.xiaoqi.companion.data.db.dao.HealthSnapshotDao
import com.xiaoqi.companion.data.db.dao.MessageDao
import com.xiaoqi.companion.data.db.dao.MoodSnapshotDao
import com.xiaoqi.companion.data.db.entity.HealthSnapshotEntity
import com.xiaoqi.companion.data.db.entity.MessageEntity
import com.xiaoqi.companion.data.db.entity.MoodSnapshotEntity
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Dream Loop 数据收集器(dual-mind §6.4)。
 *
 * 从 `mood_snapshots` / `messages` / `memories` / `health_snapshots` 拉近 7 天数据,渲染成 prompt 输入。
 * MVP 阶段不解析、不做 LLM 二次处理;Collector 100% 离线 + SQL 聚合。
 */
@Singleton
class DreamDataCollector @Inject constructor(
    private val moodSnapshotDao: MoodSnapshotDao,
    private val messageDao: MessageDao,
    private val memoryDao: com.xiaoqi.companion.data.db.dao.MemoryDao,
    private val healthSnapshotDao: HealthSnapshotDao,
    private val appPreferences: AppPreferences,
) {

    /**
     * M4 跨模态 evidence:只暴露元数据(id/content/timestamp/importance),不含 base64。
     *
     * **绝不能**把 `imageBase64` / `imageMediaType` 加进 summary —— DreamPrompt 走的是本地 Qwen
     * 纯文本路径,base64 进去会爆 token 预算,模型也看不懂。
     * `render_doesNotLeakBase64` 测试是这个约束的安全护栏。
     */
    data class ImageMemorySummary(
        val id: String,
        val content: String,
        val timestamp: Long,
        val importance: Float,
    )

    data class Snapshot(
        val rangeStart: Long,
        val rangeEnd: Long,
        val moodSnapshots: List<MoodSnapshotEntity>,
        val messages: List<MessageEntity>,
        val memoryCount: Int,
        val imageMemories: List<ImageMemorySummary> = emptyList(),
        val healthSnapshots: List<HealthSnapshotEntity> = emptyList(),
    ) {
        val isEmpty: Boolean
            get() = moodSnapshots.isEmpty() &&
                messages.isEmpty() &&
                memoryCount == 0 &&
                imageMemories.isEmpty() &&
                healthSnapshots.isEmpty()
    }

    suspend fun collectLast7Days(
        companionId: String = DEFAULT_COMPANION_ID,
        now: Long = System.currentTimeMillis(),
    ): Snapshot = withContext(Dispatchers.IO) {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        cal.add(Calendar.DAY_OF_YEAR, -7)
        val start = cal.timeInMillis
        val end = now

        val moods = moodSnapshotDao.findInRange(companionId, start, end)
        val sessionId = appPreferences.currentSessionId.first()
        val msgs = messageDao.getRecentMessages(sessionId, RECENT_MESSAGE_LIMIT)
            .filter { it.timestamp in start..end }
        val memoryCount = memoryDao.countAll()

        // M4:近 N 张有图 memory,过滤 7 天窗口,只取元数据(content 本身是 "[图片] 摘要" 不再裁剪)。
        val imageMemories = memoryDao.getRecentImages(IMAGE_MEMORY_LIMIT)
            .filter { it.timestamp in start..end }
            .map {
                ImageMemorySummary(
                    id = it.id,
                    content = it.content,
                    timestamp = it.timestamp,
                    importance = it.importance,
                )
            }

        // 健康快照:近 7 天,date 形如 20260615
        val today = Calendar.getInstance().apply { timeInMillis = end }
        val endDate = today.get(Calendar.YEAR) * 10000 +
            (today.get(Calendar.MONTH) + 1) * 100 +
            today.get(Calendar.DAY_OF_MONTH)
        val startDate = endDate - HEALTH_LOOKBACK_DAYS * 100  // 简单按月内回退,跨月会被 Dream 端的 SQL 兜住
        val healthSnapshots = healthSnapshotDao.findInRange(startDate.coerceAtLeast(0), endDate)

        Snapshot(
            rangeStart = start,
            rangeEnd = end,
            moodSnapshots = moods,
            messages = msgs,
            memoryCount = memoryCount,
            imageMemories = imageMemories,
            healthSnapshots = healthSnapshots,
        )
    }

    /**
     * 把 Snapshot 渲染成 `LocalQwenExecutor.executePatternDetect` 用的 `userMessage` 字符串。
     */
    fun render(snapshot: Snapshot): String = buildString {
        appendLine("近 7 天(从 ${formatTimestamp(snapshot.rangeStart)} 到 ${formatTimestamp(snapshot.rangeEnd)}) Aura 看到的数据:")
        appendLine()
        appendLine("## 情绪快照(${snapshot.moodSnapshots.size} 条)")
        val byDay = snapshot.moodSnapshots.groupBy { dayOfWeek(it.timestamp) }
        byDay.entries.sortedBy { it.key }.forEach { (day, list) ->
            val avgIntensity = list.map { it.intensity }.average().toFloat()
            val moods = list.groupingBy { it.mood.ifBlank { "neutral" } }.eachCount()
                .entries.joinToString(", ") { "${it.key}×${it.value}" }
            appendLine("- $day: 平均 intensity=$avgIntensity, 分布=$moods")
        }
        appendLine()
        appendLine("## 消息(${snapshot.messages.size} 条)")
        snapshot.messages.take(20).forEach { msg ->
            appendLine("- [${msg.role}] ${msg.content.take(80)}")
        }
        if (snapshot.messages.size > 20) {
            appendLine("... 还有 ${snapshot.messages.size - 20} 条")
        }
        appendLine()
        appendLine("## 长期记忆总数:${snapshot.memoryCount}")
        // M4:视觉证据 — 本地 LLM 看不到图,只能从元数据推测用户视觉节奏/兴趣。
        if (snapshot.imageMemories.isNotEmpty()) {
            appendLine()
            appendLine("## 视觉证据(${snapshot.imageMemories.size} 张)")
            snapshot.imageMemories.forEach { img ->
                appendLine("- ${formatTimestamp(img.timestamp)} ${img.content}")
            }
        }
        // Health Connect 健康快照(小米运动健康等)
        if (snapshot.healthSnapshots.isNotEmpty()) {
            appendLine()
            appendLine("## 健康快照(${snapshot.healthSnapshots.size} 天)")
            snapshot.healthSnapshots.sortedBy { it.date }.forEach { h ->
                val parts = mutableListOf<String>()
                if (h.steps > 0) parts += "步数=${h.steps}"
                if (h.avgHeartRate != null) parts += "平均心率=${h.avgHeartRate}bpm(${h.minHeartRate ?: "-"}-${h.maxHeartRate ?: "-"})"
                if (h.sleepDurationMinutes != null) {
                    val h2 = h.sleepDurationMinutes / 60
                    val m2 = h.sleepDurationMinutes % 60
                    parts += "睡眠=${h2}h${m2}m"
                }
                if (parts.isNotEmpty()) {
                    appendLine("- ${formatDate(h.date)}: ${parts.joinToString(", ")}")
                }
            }
        }
    }

    private fun formatDate(dateInt: Int): String {
        val year = dateInt / 10000
        val month = (dateInt % 10000) / 100
        val day = dateInt % 100
        return "${month}/${day}"
    }

    private fun dayOfWeek(timestamp: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> "周日"
            Calendar.MONDAY -> "周一"
            Calendar.TUESDAY -> "周二"
            Calendar.WEDNESDAY -> "周三"
            Calendar.THURSDAY -> "周四"
            Calendar.FRIDAY -> "周五"
            Calendar.SATURDAY -> "周六"
            else -> "?"
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return "${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.DAY_OF_MONTH)}"
    }

    companion object {
        const val DEFAULT_COMPANION_ID = "default"
        const val DEFAULT_SESSION_ID = "default"
        const val RECENT_MESSAGE_LIMIT = 200
        // M4 视觉证据上限:5 张图 metadata ≈ 500 字符,避免 prompt 膨胀。
        const val IMAGE_MEMORY_LIMIT = 5
        const val HEALTH_LOOKBACK_DAYS = 7
    }
}
