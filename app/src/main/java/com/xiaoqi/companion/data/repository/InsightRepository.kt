package com.xiaoqi.companion.data.repository

import com.xiaoqi.companion.core.insight.InsightDraft
import com.xiaoqi.companion.core.insight.InsightValidator
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.data.db.converter.InsightStatus
import com.xiaoqi.companion.data.db.dao.InsightDao
import com.xiaoqi.companion.data.db.entity.InsightEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Insight 数据访问 + 静音过滤。
 *
 * **写路径必须经过 [saveIfValid]**,先校验再入库,保证幻觉 insight 不会进 DB。
 * **静音过滤** 集中在 [observeVisibleNotMuted] — `mutedUntil` 未过期则隐藏。
 */
@Singleton
class InsightRepository @Inject constructor(
    private val insightDao: InsightDao,
    private val validator: InsightValidator,
) {

    fun observeVisible(limit: Int): Flow<List<InsightEntity>> =
        insightDao.observeVisible(limit)

    /** 主页卡片用:VISIBLE 且未静音 */
    fun observeVisibleNotMuted(limit: Int): Flow<List<InsightEntity>> =
        insightDao.observeVisibleNotMuted(limit = limit, now = System.currentTimeMillis())

    fun observeByCategory(category: String, since: Long): Flow<List<InsightEntity>> =
        insightDao.observeByCategory(category, since)

    /** 校验通过 → 写入并返回 rowId;否则 null。 */
    suspend fun saveIfValid(draft: InsightDraft): Long? = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val validated = validator.validate(draft)
        if (validated == null) {
            AppLogger.info(
                LogTags.Repo,
                "insight_save_rejected_by_validator",
                "trigger" to draft.triggerType,
                "category" to draft.category,
            )
            return@withContext null
        }
        val entity = InsightEntity(
            createdAt = System.currentTimeMillis(),
            triggerType = validated.triggerType,
            category = validated.category,
            headline = validated.headline.trim(),
            bodyMarkdown = validated.bodyMarkdown.trim(),
            evidence = encodeEvidence(validated),
            confidence = validated.confidence.coerceIn(0f, 1f),
            relevanceWindow = validated.relevanceWindow,
            status = InsightStatus.VISIBLE,
        )
        val rowId = insightDao.insert(entity)
        AppLogger.info(
            LogTags.Repo,
            "insight_save_completed",
            "insightId" to rowId,
            "category" to validated.category,
            "confidence" to validated.confidence,
            "durationMs" to (System.currentTimeMillis() - startedAt),
        )
        rowId
    }

    suspend fun dismiss(id: Long): Unit = withContext(Dispatchers.IO) {
        insightDao.setStatus(id, InsightStatus.DISMISSED)
        AppLogger.info(LogTags.Repo, "insight_dismissed", "insightId" to id)
    }

    suspend fun archive(id: Long): Unit = withContext(Dispatchers.IO) {
        insightDao.setStatus(id, InsightStatus.ARCHIVED)
        AppLogger.info(LogTags.Repo, "insight_archived", "insightId" to id)
    }

    suspend fun muteCategory(id: Long, category: String, mutedUntilMillis: Long): Unit =
        withContext(Dispatchers.IO) {
            insightDao.setStatusWithMute(
                id = id,
                status = InsightStatus.MUTED_CATEGORY,
                mutedUntil = mutedUntilMillis,
            )
            AppLogger.info(
                LogTags.Repo,
                "insight_category_muted",
                "insightId" to id,
                "category" to category,
                "mutedUntil" to mutedUntilMillis,
            )
        }

    suspend fun markClicked(id: Long): Unit = withContext(Dispatchers.IO) {
        insightDao.setUserClickedAt(id, System.currentTimeMillis())
    }

    suspend fun setUserFeedback(id: Long, feedback: String): Unit = withContext(Dispatchers.IO) {
        insightDao.setUserFeedback(id, feedback)
    }

    suspend fun countAll(): Int = withContext(Dispatchers.IO) { insightDao.countAll() }
    suspend fun countVisible(): Int = withContext(Dispatchers.IO) { insightDao.countVisible() }

    suspend fun clearAll(): Int = withContext(Dispatchers.IO) {
        val before = insightDao.countAll()
        insightDao.clearAll()
        AppLogger.info(LogTags.Repo, "insight_clear_all", "beforeCount" to before)
        before
    }

    /**
     * 调试用:插 2-3 条占位 insight,状态 VISIBLE。
     * 仅在 ChatViewModel 启动时调用一次(检测 DB 为空时)。
     */
    suspend fun seedDemoInsights(): Int = withContext(Dispatchers.IO) {
        if (insightDao.countAll() > 0) return@withContext 0
        val now = System.currentTimeMillis()
        val seeds = listOf(
            InsightDraft(
                triggerType = "PATTERN_DETECT",
                category = "情绪",
                headline = "你最近 3 周都周日傍晚情绪偏低",
                bodyMarkdown = "mood_snapshots 里连续 3 个周日的 intensity 都低于 0.4。要不要聊聊周日一般会发生什么?",
                relevanceWindow = "近 21 天",
                confidence = 0.75f,
                evidenceMoodSnapshotIds = listOf("seed-1", "seed-2", "seed-3"),
            ),
            InsightDraft(
                triggerType = "ANNIVERSARY",
                category = "重要日期",
                headline = "下周三是妈妈的生日",
                bodyMarkdown = "你之前提过妈妈下个月生日。要不要我帮你查餐厅或写一段祝福?",
                relevanceWindow = "近 7 天",
                confidence = 0.82f,
                evidenceMemoryIds = listOf("seed-mom-bday"),
            ),
            InsightDraft(
                triggerType = "MOOD_TREND",
                category = "习惯",
                headline = "你最近 1 周开始提到「睡眠」",
                bodyMarkdown = "messages 关键词统计里「睡不着」出现 4 次,上周 0 次。",
                relevanceWindow = "近 7 天",
                confidence = 0.68f,
                evidenceMessageIds = listOf("seed-sleep-1", "seed-sleep-2"),
            ),
        )
        var inserted = 0
        seeds.forEach { draft ->
            val id = saveIfValid(draft)
            if (id != null) inserted++
        }
        AppLogger.info(
            LogTags.Repo,
            "insight_seed_completed",
            "requested" to seeds.size,
            "inserted" to inserted,
        )
        inserted
    }

    private fun encodeEvidence(draft: InsightDraft): String {
        val payload: JsonObject = buildJsonObject {
            putJsonArray("messageIds") {
                draft.evidenceMessageIds.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
            }
            putJsonArray("memoryIds") {
                draft.evidenceMemoryIds.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
            }
            putJsonArray("moodSnapshotIds") {
                draft.evidenceMoodSnapshotIds.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
            }
        }
        return json.encodeToString(JsonObject.serializer(), payload)
    }

    /**
     * 把 `insights.evidence` JSON 解出 evidence 列表(长按弹层 "查看依据" 用)。
     * 解析失败返回空列表(不让 UI 崩)。
     */
    fun decodeEvidence(insight: InsightEntity): InsightEvidenceView =
        decodeEvidenceStatic(insight)

    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    companion object {
        /**
         * 无状态的 evidence 解析入口(供 mapper 在 entity → ChatInsight 时调用)。
         * 失败兜底:返回空 `InsightEvidenceView`,绝不抛。
         */
        fun decodeEvidenceStatic(insight: InsightEntity): InsightEvidenceView {
            val json = Json { ignoreUnknownKeys = true }
            val parsed = runCatching { json.parseToJsonElement(insight.evidence) as? JsonObject }
                .getOrNull() ?: return InsightEvidenceView()
            val msgIds = (parsed["messageIds"] as? JsonArray)
                ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                ?: emptyList()
            val memIds = (parsed["memoryIds"] as? JsonArray)
                ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                ?: emptyList()
            val moodIds = (parsed["moodSnapshotIds"] as? JsonArray)
                ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                ?: emptyList()
            return InsightEvidenceView(msgIds, memIds, moodIds)
        }
    }
}

data class InsightEvidenceView(
    val messageIds: List<String> = emptyList(),
    val memoryIds: List<String> = emptyList(),
    val moodSnapshotIds: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = messageIds.isEmpty() && memoryIds.isEmpty() && moodSnapshotIds.isEmpty()
}
