package com.xiaoqi.companion.core.presence.runtime

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [WorkScheduler] 的 Android 实现 — 委托给 WorkManager 单例。
 *
 * 周期任务用 [ExistingPeriodicWorkPolicy.UPDATE]:冷启动时 unique 不存在等同于 KEEP;
 * 用户改档位时 WorkManager 内部更新参数,不需要重启 Worker。
 *
 * 一次性任务用 [ExistingWorkPolicy.REPLACE]:同一 unique 任务只保留最近一次,避免重复点击排队。
 */
@Singleton
class WorkManagerScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : WorkScheduler {

    override fun enqueuePeriodic(uniqueName: String, intervalMinutes: Long) {
        val request = PeriodicWorkRequestBuilder<DreamLoopWorker>(
            intervalMinutes, TimeUnit.MINUTES,
        ).setConstraints(BATTERY_CONSTRAINTS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            uniqueName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    override fun cancelPeriodic(uniqueName: String) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName)
    }

    override fun enqueueOneTime(uniqueName: String) {
        val request = OneTimeWorkRequestBuilder<DreamLoopWorker>()
            .setConstraints(BATTERY_CONSTRAINTS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueName,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    companion object {
        private val BATTERY_CONSTRAINTS = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()
    }
}
