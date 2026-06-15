package com.xiaoqi.companion.core.presence.runtime

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.data.repository.InsightRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
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

        val result = executor.execute(
            LocalQwenExecutor.Request(
                systemPrompt = com.xiaoqi.companion.core.insight.InsightPrompts.patternDetect,
                userMessage = dataCollector.render(snapshot),
            ),
        )
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

        var savedCount = 0
        drafts.forEach { draft ->
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
        Result.success()
    }
}
