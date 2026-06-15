package com.xiaoqi.companion.core.presence.runtime

import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.data.datastore.AppPreferences
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Qualifier 标注 application 全局 CoroutineScope,供 long-lived collector 使用。
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * DreamLoop 调度器(7 档可配置周期 + 立即触发)。
 *
 * 必须在 [com.xiaoqi.companion.CompanionApplication.onCreate] 调一次 `start()`:
 * - 启动后台 coroutine 持续监听 [AppPreferences.dreamLoopInterval]
 * - 偏好变化时自动 enqueuePeriodic(UPDATE) / cancelPeriodic
 * - 默认 6h,与历史硬编码行为完全一致
 *
 * 立即触发 [triggerNow] 走独立唯一任务,适合用户主动验证或调参场景。
 *
 * 依赖:
 * - [AppPreferences] 读偏好
 * - [WorkScheduler] 调 WorkManager(测试用 fake 替换)
 * - [@ApplicationScope] CoroutineScope 跑长生命周期 collector(测试用 TestScope 替换)
 */
@Singleton
class DreamLoopScheduler @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val appPreferences: AppPreferences,
    private val workScheduler: WorkScheduler,
) {

    private var started = false

    /**
     * 启动偏好监听。幂等 — 重复调 [start] 不会创建新协程。
     * 第一次 collect 发射当前值(默认 H6)后 enqueue 一次;
     * 后续偏好变化触发 UPDATE / cancel。
     */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            appPreferences.dreamLoopInterval
                .distinctUntilChanged()
                .collect { interval -> applyPeriodic(interval) }
        }
    }

    /**
     * 立即跑一次 Dream Loop(OneTimeWorkRequest)。
     * 与周期任务互不干扰,周期到点时仍会正常跑。
     */
    fun triggerNow() {
        workScheduler.enqueueOneTime(UNIQUE_TRIGGER_NAME)
        AppLogger.info(
            LogTags.Config,
            "dream_loop_trigger_now_enqueued",
        )
    }

    private fun applyPeriodic(interval: DreamLoopInterval) {
        if (!interval.isEnabled) {
            workScheduler.cancelPeriodic(UNIQUE_NAME)
            AppLogger.info(
                LogTags.Config,
                "dream_loop_disabled",
                "previous" to interval.name,
            )
            return
        }
        workScheduler.enqueuePeriodic(UNIQUE_NAME, interval.minutes)
        AppLogger.info(
            LogTags.Config,
            "dream_loop_rescheduled",
            "interval" to interval.name,
            "minutes" to interval.minutes,
        )
    }

    companion object {
        const val UNIQUE_NAME = "dream_loop"
        const val UNIQUE_TRIGGER_NAME = "dream_loop_now"
    }
}
