package com.xiaoqi.companion.core.presence.runtime

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DreamLoop 调度器:每天跑 1-2 次(6h 周期) + 电量约束。
 *
 * 必须在 [com.xiaoqi.companion.CompanionApplication.onCreate] 调一次 `schedule(context)`,
 * 由 `enqueueUniquePeriodicWork("dream_loop", KEEP, request)` 保证幂等。
 */
@Singleton
class DreamLoopScheduler @Inject constructor() {

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<DreamLoopWorker>(
            INTERVAL_HOURS, TimeUnit.HOURS,
        ).setConstraints(constraints).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    companion object {
        const val UNIQUE_NAME = "dream_loop"
        const val INTERVAL_HOURS = 6L
    }
}
