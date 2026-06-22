package com.xiaoqi.companion.core.presence.runtime

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.data.datastore.AppPreferences
import com.xiaoqi.companion.core.insight.EvidenceResolver
import com.xiaoqi.companion.data.repository.InsightRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Dream Loop 主循环(plan §3 + §6.4):每天 1-2 次,跑 `patternDetect` prompt。
 *
 * 触发:由 [DreamLoopScheduler] 调度,WorkManager 周期 6h + 电量约束。
 *
 * 失败处理:
 * - 模型未下载 / MNN 加载失败 → `Result.retry()` 让 WorkManager backoff 重试
 * - 0 数据(新装 App 第一次) → `Result.success()` 跳过,不浪费 worker
 * - 解析失败/Validator 全拒 → `Result.success()`,数据已尽力
 */
@HiltWorker
class DreamLoopWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dataCollector: DreamDataCollector,
    private val executor: LocalQwenExecutor,
    private val insightRepository: InsightRepository,
    private val evidenceResolver: EvidenceResolver,
    private val appPreferences: AppPreferences,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        AppLogger.info(
            LogTags.Config,
            "dream_loop_started",
            "runAttemptCount" to runAttemptCount,
        )

        if (BatteryHelper.isLow(applicationContext)) {
            AppLogger.info(LogTags.Config, "dream_loop_skipped_low_battery")
            return@withContext Result.success()
        }

        val snapshot = runCatching { dataCollector.collectLast7Days() }
            .onFailure {
                AppLogger.error(
                    LogTags.Config,
                    it,
                    "dream_loop_data_collection_failed",
                )
            }
            .getOrNull()
        if (snapshot == null) return@withContext Result.retry()

        if (snapshot.isEmpty) {
            AppLogger.info(
                LogTags.Config,
                "dream_loop_skipped_empty_data",
                "durationMs" to (System.currentTimeMillis() - startedAt),
            )
            return@withContext Result.success()
        }

        val dreamModelName = appPreferences.dreamLoopModelName.first()
        val result = executor.execute(
            LocalQwenExecutor.Request(
                systemPrompt = com.xiaoqi.companion.core.insight.InsightPrompts.patternDetect,
                userMessage = dataCollector.render(snapshot),
                modelName = dreamModelName.takeIf<String> { it.isNotBlank() },
            ),
        )
        AppLogger.info(
            LogTags.Config,
            "dream_loop_raw_output",
            "textLength" to result.text.length,
            "raw0" to result.text.take(120),
            "model" to (dreamModelName.takeIf<String> { it.isNotBlank() } ?: "(follow)"),
        )
        if (result.errorMessage != null) {
            // 模型缺失是不该 retry 的硬错误(下个 6h 周期也不会自动下载模型),
            // 转成 failure 让 UI 上的 dream loop 状态自然落到 FAILED,而不是无限 backoff 转圈。
            val modelMissing = result.errorMessage.contains("model not found", ignoreCase = true)
                || result.errorMessage.contains("model is missing", ignoreCase = true)
                || result.errorMessage.contains("config.json", ignoreCase = true)
            AppLogger.warn(
                LogTags.Config,
                if (modelMissing) "dream_loop_model_missing" else "dream_loop_executor_error",
                "cause" to result.errorMessage,
                "latencyMs" to result.latencyMs,
            )
            return@withContext if (modelMissing) {
                Result.failure(workDataOf(KEY_ERROR to result.errorMessage))
            } else {
                Result.retry()
            }
        }
        if (result.text.isBlank()) {
            AppLogger.warn(
                LogTags.Config,
                "dream_loop_empty_model_output",
                "durationMs" to result.latencyMs,
            )
            return@withContext Result.retry()
        }

        val drafts = executor.parsePatternDetectOutput(result.text)
        if (drafts.isEmpty()) {
            AppLogger.warn(
                LogTags.Config,
                "dream_loop_no_drafts_parsed",
                "latencyMs" to result.latencyMs,
            )
            return@withContext Result.success()
        }

        // 小模型常输出 confidence=0,强制拉高到可用水位
        val boostedDrafts = drafts.map { draft ->
            if (draft.confidence < 0.5f) draft.copy(confidence = 0.5f) else draft
        }

        // Post-hoc evidence resolution: 从 LLM 文本反查真实 DB ID，解决小模型无法输出有效 evidence_ids 的问题
        val currentSessionId = appPreferences.currentSessionId.first()
        val resolvedDrafts = evidenceResolver.resolve(boostedDrafts, snapshot, currentSessionId)

        resolvedDrafts.forEachIndexed { index, draft ->
            AppLogger.info(
                LogTags.Config,
                "dream_loop_resolved_draft",
                "index" to index,
                "headline" to draft.headline.take(40),
                "msgEvidence" to draft.evidenceMessageIds.size,
                "memEvidence" to draft.evidenceMemoryIds.size,
                "moodEvidence" to draft.evidenceMoodSnapshotIds.size,
                "confidence" to draft.confidence,
            )
        }

        var savedCount = 0
        resolvedDrafts.forEach { draft ->
            val id = insightRepository.saveIfValid(draft)
            if (id != null) savedCount++
        }

        AppLogger.info(
            LogTags.Config,
            "dream_loop_completed",
            "draftsParsed" to drafts.size,
            "saved" to savedCount,
            "latencyMs" to result.latencyMs,
            "durationMs" to (System.currentTimeMillis() - startedAt),
        )
        Result.success(
            workDataOf(
                KEY_SAVED_COUNT to savedCount,
                KEY_DRAFTS_PARSED to drafts.size,
            ),
        )
    }

    companion object {
        const val KEY_SAVED_COUNT = "dream_loop_saved_count"
        const val KEY_DRAFTS_PARSED = "dream_loop_drafts_parsed"
        const val KEY_ERROR = "dream_loop_error"
    }
}
