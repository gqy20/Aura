package com.xiaoqi.companion.core.presence.runtime

import com.xiaoqi.companion.data.db.dao.MessageDao
import com.xiaoqi.companion.data.db.dao.MoodSnapshotDao
import com.xiaoqi.companion.data.db.entity.MessageEntity
import com.xiaoqi.companion.data.db.entity.MoodSnapshotEntity
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dream Loop 数据收集器(dual-mind §6.4)。
 *
 * 从 `mood_snapshots` / `messages` / `memories` 拉近 7 天数据,渲染成 prompt 输入。
 * MVP 阶段不解析、不做 LLM 二次处理;Collector 100% 离线 + SQL 聚合。
 */
@Singleton
class DreamDataCollector @Inject constructor(
    private val moodSnapshotDao: MoodSnapshotDao,
    private val messageDao: MessageDao,
    private val memoryDao: com.xiaoqi.companion.data.db.dao.MemoryDao,
) {

    data class Snapshot(
        val rangeStart: Long,
        val rangeEnd: Long,
        val moodSnapshots: List<MoodSnapshotEntity>,
        val messages: List<MessageEntity>,
        val memoryCount: Int,
        val topKeywords: List<String>,
    ) {
        val isEmpty: Boolean
            get() = moodSnapshots.isEmpty() && messages.isEmpty() && memoryCount == 0
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
        val msgs = messageDao.getRecentMessages(DEFAULT_SESSION_ID, RECENT_MESSAGE_LIMIT)
            .filter { it.timestamp in start..end }
        val memoryCount = memoryDao.countAll()
        val topKeywords = extractTopKeywords(msgs, KEYWORD_LIMIT)

        Snapshot(
            rangeStart = start,
            rangeEnd = end,
            moodSnapshots = moods,
            messages = msgs,
            memoryCount = memoryCount,
            topKeywords = topKeywords,
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
        if (snapshot.topKeywords.isNotEmpty()) {
            appendLine("## 高频关键词:${snapshot.topKeywords.joinToString(", ")}")
        }
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

    /**
     * 简易词频统计:小写化 + 去停用词 + 去标点 + 长度 ≥ 2 + 取 top N。
     * **不**做中文分词(MVP 阶段够用,真实"长期认识"留给 M3+ 接 jieba 等)。
     */
    private fun extractTopKeywords(messages: List<MessageEntity>, limit: Int): List<String> {
        val counts = HashMap<String, Int>()
        messages.forEach { msg ->
            msg.content.lowercase()
                .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
                .split(Regex("\\s+"))
                .map { it.trim() }
                .filter { it.length >= 2 && it !in STOPWORDS }
                .forEach { token -> counts[token] = (counts[token] ?: 0) + 1 }
        }
        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { it.key }
    }

    companion object {
        const val DEFAULT_COMPANION_ID = "default"
        const val DEFAULT_SESSION_ID = "default"
        const val RECENT_MESSAGE_LIMIT = 200
        const val KEYWORD_LIMIT = 10

        private val STOPWORDS = setOf(
            "the", "a", "an", "is", "are", "was", "were", "be", "been",
            "我", "你", "他", "她", "它", "的", "了", "在", "是", "和",
            "to", "of", "in", "on", "at", "for", "and", "or", "but",
        )
    }
}
