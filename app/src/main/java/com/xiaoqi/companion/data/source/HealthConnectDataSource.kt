package com.xiaoqi.companion.data.source

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.data.db.dao.HealthSnapshotDao
import com.xiaoqi.companion.data.db.entity.HealthSnapshotEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Health Connect 数据源 — 从用户授权的数据源(典型:小米运动健康国内版,详见 docs/research/health-connect-mi-fitness.md)读取步数/心率/睡眠,聚合成按日快照写入 [HealthSnapshotDao]。
 *
 * 设计原则:
 * - 单例,所有读都走 IO dispatcher
 * - HC SDK 不可用 / 未授权时 [syncRecentDays] 返回 0(不抛异常,让上层优雅降级)
 * - 一次调用同步 N 天;上层(Dream / Worker / Tool)各自控制频率,避免在主线程跑 HC read
 */
@Singleton
class HealthConnectDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val healthSnapshotDao: HealthSnapshotDao,
) : HealthSource {

    override val supportedMetrics: Set<HealthMetric> = setOf(
        HealthMetric.STEPS,
        HealthMetric.HEART_RATE,
        HealthMetric.SLEEP,
    )

    /**
     * SDK 状态在 ColorOS / realme 上是个**误报**——controller APK 装着,但
     * `health_connect` system service 没注册到 `cmd`,也没有 launcher activity,
     * 这种"半装"状态 `getSdkStatus` 仍返回 `SDK_AVAILABLE = 3`,但所有 read
     * 都会被 `DataPermissionEnforcer` 拒。
     *
     * 加一层"service 真活着"校验:跑一次**真 readRecords** —— service 真的
     * 挂载时,即使没权限也只是返回空集合;没挂载 / 半装会直接抛
     * `IllegalStateException` 或 `SecurityException`。
     */
    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
            return@withContext false
        }
        val cli = runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull()
            ?: return@withContext false
        val now = Instant.now()
        val oneMinAgo = now.minusSeconds(60L)
        // 用 StepsRecord 探一次,1 分钟窗口。最快失败/成功路径
        runCatching {
            cli.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(oneMinAgo, now),
                ),
            )
        }.fold(
            onSuccess = { true },
            onFailure = { e ->
                AppLogger.warn(
                    LogTags.HealthConnect,
                    "hc_runtime_not_responsive",
                    "err" to (e.message ?: e::class.simpleName.orEmpty()),
                )
                false
            },
        )
    }

    suspend fun grantedPermissions(): Set<String> = withContext(Dispatchers.IO) {
        if (!isAvailable()) return@withContext emptySet()
        val client = HealthConnectClient.getOrCreate(context)
        runCatching { client.permissionController.getGrantedPermissions() }
            .getOrDefault(emptySet())
    }

    /**
     * 用真实 read 操作反推权限状态 ——
     * 某些厂商的 HC service (realme/ColorOS 等) 的 `getGrantedPermissions()`
     * 会在 `enforceValidPackage` 阶段拒绝 debug 包,即便用户在系统里已经授权。
     * 实际 `readRecords` 走的是另一条 AIDL 路径,不会卡这关,所以**用读得到数据来
     * 反证权限已开**才是真信号。
     *
     * 返回 `Set<READ_*>` —— 包含"今天能读到的指标对应的权限"。
     * 注意:列表非空只能证明"今天有数据"。要做严格的"权限是否开"判定,应改用
     * try-catch read 异常 — 这里用 [probePermission] 替代。
     */
    suspend fun probePermission(perm: String): Boolean = withContext(Dispatchers.IO) {
        if (!isAvailable()) return@withContext false
        val client = runCatching { HealthConnectClient.getOrCreate(context) }
            .getOrNull() ?: return@withContext false
        val zone = ZoneId.systemDefault()
        val endInstant = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant()
        val startInstant = endInstant.minusSeconds(60L) // 看 1 分钟窗口,够触发 perm 校验
        runCatching {
            when (perm) {
                READ_STEPS -> client.readRecords(
                    ReadRecordsRequest(
                        recordType = StepsRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startInstant, endInstant),
                    ),
                )
                READ_HEART_RATE -> client.readRecords(
                    ReadRecordsRequest(
                        recordType = HeartRateRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startInstant, endInstant),
                    ),
                )
                READ_SLEEP -> client.readRecords(
                    ReadRecordsRequest(
                        recordType = SleepSessionRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(startInstant, endInstant),
                    ),
                )
                else -> throw IllegalArgumentException("unknown perm: $perm")
            }
            true
        }.getOrDefault(false)
    }

    /**
     * 同步最近 [days] 天数据,写入 health_snapshots 表(REPLACE by date)。
     * @return 写入且包含至少 1 项指标的快照数量;HC 不可用或没权限时返回 0。
     */
    override suspend fun syncRecentDays(days: Int): Int = withContext(Dispatchers.IO) {
        if (!isAvailable()) {
            AppLogger.info(LogTags.HealthConnect, "sync_skipped", "reason" to "sdk_unavailable")
            return@withContext 0
        }
        val client = HealthConnectClient.getOrCreate(context)
        val granted = runCatching { client.permissionController.getGrantedPermissions() }
            .onFailure { AppLogger.warn(LogTags.HealthConnect, "perm_query_failed", "err" to it.message) }
            .getOrDefault(emptySet())
        if (granted.isEmpty()) {
            AppLogger.info(LogTags.HealthConnect, "sync_skipped", "reason" to "no_permission")
            return@withContext 0
        }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = today.minusDays((days - 1).toLong())
        val startInstant = start.atStartOfDay(zone).toInstant()
        val endInstant = today.plusDays(1).atStartOfDay(zone).toInstant()

        val steps = if (READ_STEPS in granted) {
            readSteps(client, startInstant, endInstant)
        } else emptyList()

        val heartRates = if (READ_HEART_RATE in granted) {
            readHeartRate(client, startInstant, endInstant)
        } else emptyList()

        val sleepSessions = if (READ_SLEEP in granted) {
            readSleep(client, startInstant, endInstant)
        } else emptyList()

        val now = System.currentTimeMillis()
        val snapshots = (0 until days).map { offset ->
            val date = today.minusDays(offset.toLong())
            buildSnapshot(date, zone, steps, heartRates, sleepSessions, now)
        }
        healthSnapshotDao.upsertAll(snapshots)
        AppLogger.info(
            LogTags.HealthConnect,
            "sync_done",
            "days" to days,
            "stepRecords" to steps.size,
            "heartRecords" to heartRates.size,
            "sleepSessions" to sleepSessions.size,
            "granted" to granted.size,
        )
        snapshots.count { it.hasData() }
    }

    private suspend fun readSteps(
        client: HealthConnectClient,
        start: Instant,
        end: Instant,
    ): List<StepsRecord> {
        val resp = client.readRecords(
            ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
            ),
        )
        return resp.records
    }

    private suspend fun readHeartRate(
        client: HealthConnectClient,
        start: Instant,
        end: Instant,
    ): List<HeartRateRecord> {
        val resp = client.readRecords(
            ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
            ),
        )
        return resp.records
    }

    private suspend fun readSleep(
        client: HealthConnectClient,
        start: Instant,
        end: Instant,
    ): List<SleepSessionRecord> {
        val resp = client.readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
            ),
        )
        return resp.records
    }

    /**
     * 把原始 HC records 聚合到 [date] 当天。
     *
     * - 步数 record.startTime 的自然日归属。StepsRecord.count 是 Long,可能超过 Int.MAX。
     * - 心率 record 一时段多次 sample,聚合 min/max/avg(beatsPerMinute: Long)。
     * - 睡眠 session 按 startTime 自然日归属,sleepDurationMinutes = sum(endTime - startTime)。
     * - sleepStagesJson 存 [{stage, minutes}, ...] 序列,stage 用 STAGE_TYPE_INT_TO_STRING_MAP 翻译成字符串。
     */
    private fun buildSnapshot(
        date: LocalDate,
        zone: ZoneId,
        steps: List<StepsRecord>,
        heartRates: List<HeartRateRecord>,
        sleepSessions: List<SleepSessionRecord>,
        fetchedAt: Long,
    ): HealthSnapshotEntity {
        val dayStart = date.atStartOfDay(zone).toInstant()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
        val dateInt = date.year * 10000 + date.monthValue * 100 + date.dayOfMonth

        val dailySteps = steps.filter { it.startTime >= dayStart && it.startTime < dayEnd }
        val stepsTotal = dailySteps.sumOf { it.count }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        val dailyHeart = heartRates.filter { it.startTime >= dayStart && it.startTime < dayEnd }
        val samples = dailyHeart.flatMap { it.samples }.map { it.beatsPerMinute }
        val avg: Int? = if (samples.isNotEmpty()) samples.average().toInt() else null
        val min: Int? = samples.minOrNull()?.toInt()
        val max: Int? = samples.maxOrNull()?.toInt()

        val dailySleep = sleepSessions.filter { it.startTime >= dayStart && it.startTime < dayEnd }
        val sleepMin: Int? = if (dailySleep.isNotEmpty()) {
            dailySleep.sumOf {
                java.time.Duration.between(it.startTime, it.endTime).toMinutes()
            }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        } else null
        val stagesJson = Json.encodeToString(
            dailySleep.flatMap { session ->
                session.stages.map { stage ->
                    mapOf(
                        "stage" to sleepStageName(stage.stage),
                        "startEpoch" to stage.startTime.toEpochMilli(),
                        "endEpoch" to stage.endTime.toEpochMilli(),
                    )
                }
            }
        )
        val sourcePackages = Json.encodeToString(
            (steps.map { it.metadata.dataOrigin.packageName } +
                heartRates.map { it.metadata.dataOrigin.packageName } +
                sleepSessions.map { it.metadata.dataOrigin.packageName })
                .distinct()
        )

        return HealthSnapshotEntity(
            date = dateInt,
            steps = stepsTotal,
            distanceMeters = 0.0, // Length Record 在 alpha07 暂不读(API 仍在变)
            caloriesKcal = 0.0,   // 同上
            avgHeartRate = avg,
            restingHeartRate = null, // RestingHeartRateRecord 未引入,未来可加
            minHeartRate = min,
            maxHeartRate = max,
            sleepDurationMinutes = sleepMin,
            sleepStagesJson = stagesJson,
            sourcePackages = sourcePackages,
            fetchedAt = fetchedAt,
        )
    }

    private fun HealthSnapshotEntity.hasData(): Boolean =
        steps > 0 || avgHeartRate != null || sleepDurationMinutes != null

    /**
     * Map [SleepSessionRecord] stage int → readable name.
     *
     * `SleepSessionRecord.STAGE_TYPE_INT_TO_STRING_MAP` would be the natural one-liner, but it is
     * marked `@RestrictTo(LIBRARY_GROUP)` on `androidx.health.connect:connect-client` and trips
     * lint `RestrictedApi` (CI gates fail on it). The public `STAGE_TYPE_*` Int constants are
     * not restricted, so we map them ourselves here.
     */
    private fun sleepStageName(stage: Int): String = when (stage) {
        SleepSessionRecord.STAGE_TYPE_AWAKE -> "AWAKE"
        SleepSessionRecord.STAGE_TYPE_SLEEPING -> "SLEEPING"
        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> "OUT_OF_BED"
        SleepSessionRecord.STAGE_TYPE_LIGHT -> "LIGHT"
        SleepSessionRecord.STAGE_TYPE_DEEP -> "DEEP"
        SleepSessionRecord.STAGE_TYPE_REM -> "REM"
        else -> "UNKNOWN"
    }

    companion object {
        const val DEFAULT_LOOKBACK_DAYS = 7

        const val READ_STEPS = "android.permission.health.READ_STEPS"
        const val READ_HEART_RATE = "android.permission.health.READ_HEART_RATE"
        const val READ_SLEEP = "android.permission.health.READ_SLEEP"
    }
}
