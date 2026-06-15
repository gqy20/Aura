package com.xiaoqi.companion.data.source

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.ContextCompat
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.data.db.dao.HealthSnapshotDao
import com.xiaoqi.companion.data.db.entity.HealthSnapshotEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 本机传感器步数源 — 任何 Android 10+ 设备都能跑,**不依赖** Health Connect / 小米 SDK / OEM 合作。
 *
 * 实现要点:
 * - **TYPE_STEP_COUNTER** 是系统级 hardware sensor,数值是"自上次重启以来的累计步数"。
 *   我们用 **当日 baseline** 写入"今天步数"。
 * - 由于 sensor 回调是异步的,我们 `register` → 等待首次 event 拿到 baseline → `unregister`。
 * - 如果 [timeoutMs] 内拿不到回调(sensor 关 / 权限拒 / ROM 拦截),返回 0 不抛。
 * - 写入**同一个** `health_snapshots` 表,但**只更新 `steps` / `sourcePackages` / `fetchedAt` 三列**
 *   ([HealthSnapshotDao.updateStepsOnly]) — 不抹掉 HC 已经写入的心率/睡眠。
 *
 * 历史窗口策略:
 * - step_counter 不记录任何历史,只能拿到"现在累计"和"刚才累计"。要做历史 7 天的
 *   diff,需要每天 0 点读一次 baseline 持久化。
 * - **当前 v1 实现**:只写"今天"一行(用 [updateStepsOnly],行不存在时回退 [upsert]);
 *   历史 7 天的步数留 0;若 HC 源能拉到历史的,会自动 cover。
 * - **未来 v2**:加 `LastStepBaseline` 实体,每天 0 点由 Worker 读一次 baseline 入库;
 *   就能算"昨日步数 = today_baseline - yesterday_baseline"。
 *
 * 已知边界:
 * - 心率/睡眠无本地 sensor(只在手环/手表上),仍然需要 HC 或小米合作。
 *   故 [supportedMetrics] 只声明 STEPS,跟 HC 源是**互补**关系。
 * - SensorManager 在 MIUI / ColorOS 上**通常**对三方应用开放 step_counter;
 *   但部分 ROM 会要求"始终允许 ACTIVITY_RECOGNITION",否则回调收不到。
 */
@Singleton
class SensorManagerHealthSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val healthSnapshotDao: HealthSnapshotDao,
) : HealthSource {

    override val supportedMetrics: Set<HealthMetric> = setOf(HealthMetric.STEPS)

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        if (!hasActivityRecognitionPermission()) return@withContext false
        val sm = context.getSystemService(SensorManager::class.java) ?: return@withContext false
        sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
    }

    override suspend fun syncRecentDays(days: Int): Int = withContext(Dispatchers.IO) {
        if (!isAvailable()) {
            AppLogger.info(LogTags.HealthConnect, "sensor_skipped", "reason" to "no_perm_or_no_sensor")
            return@withContext 0
        }
        val sm = context.getSystemService(SensorManager::class.java) ?: return@withContext 0
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return@withContext 0

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val todayDateInt = today.year * 10000 + today.monthValue * 100 + today.dayOfMonth
        val now = System.currentTimeMillis()

        val baseline = readBaselineWithTimeout(sm, sensor, timeoutMs = 1_500L)
        if (baseline == null) {
            AppLogger.info(LogTags.HealthConnect, "sensor_skipped", "reason" to "no_callback")
            return@withContext 0
        }
        val todaySteps = baseline.steps.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val sourcePackages = "[\"android.sensor\"]"

        // 优先 UPDATE 三列;若该日期无现存 row(HCO 也没写过),回退完整 upsert 创建占位行
        val updated = healthSnapshotDao.updateStepsOnly(
            date = todayDateInt,
            steps = todaySteps,
            sourcePackages = sourcePackages,
            fetchedAt = now,
        )
        if (updated == 0) {
            healthSnapshotDao.upsert(
                HealthSnapshotEntity(
                    date = todayDateInt,
                    steps = todaySteps,
                    sourcePackages = sourcePackages,
                    fetchedAt = now,
                ),
            )
        }
        // _days 暂保留 — 未来 v2 baseline 持久化后,会写 7 天的 diff
        @Suppress("UNUSED_VARIABLE")
        val unused = days
        AppLogger.info(
            LogTags.HealthConnect,
            "sensor_sync_done",
            "date" to todayDateInt,
            "todaySteps" to todaySteps,
            "mergedExistingRow" to (updated > 0),
        )
        1
    }

    private fun hasActivityRecognitionPermission(): Boolean {
        // Android 10 (API 29) 之前不需要这个权限;之后必须运行时申请
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACTIVITY_RECOGNITION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private suspend fun readBaselineWithTimeout(
        sm: SensorManager,
        sensor: Sensor,
        timeoutMs: Long,
    ): StepBaseline? {
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                var received = false
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return
                        if (received) return
                        received = true
                        val steps = event.values.getOrNull(0)?.toLong() ?: 0L
                        cont.resumeWith(Result.success(StepBaseline(steps)))
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }
                sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                cont.invokeOnCancellation { sm.unregisterListener(listener) }
            }
        }
    }

    private data class StepBaseline(val steps: Long)
}
