package com.xiaoqi.companion.core.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.data.db.dao.HealthSnapshotDao
import com.xiaoqi.companion.data.db.entity.HealthSnapshotEntity
import com.xiaoqi.companion.data.source.HealthConnectDataSource
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 让 LLM 主动查健康数据(步数/心率/睡眠)。
 *
 * 设计选择:
 * - 工具名 `query_health_data`,与 `query_*` 家族保持一致(search_memory / search_records / search_summaries)
 * - `sync=true` 时先调 [HealthConnectDataSource.syncRecentDays] 拉最新数据再返回(适用于用户刚运动完的场景)
 * - `sync=false` 时只读本地缓存(适用于离线 / 不愿弹 HC 权限)
 * - 默认 7 天,与 Dream 窗口对齐
 */
class QueryHealthDataTool @Inject constructor(
    private val healthSnapshotDao: HealthSnapshotDao,
    private val healthConnectDataSource: HealthConnectDataSource,
) : SimpleTool<QueryHealthDataTool.Args>(
    typeToken<Args>(),
    name = "query_health_data",
    description = "Query recent health data (steps, heart rate, sleep) synced from Health Connect sources like 小米运动健康. " +
        "Returns aggregated daily snapshots. Pass sync=true to pull fresh data from Health Connect first.",
) {

    @Serializable
    data class Args(
        @param:LLMDescription("Number of days to look back from today. Default 7, max 30.")
        val days: Int = 7,
        @param:LLMDescription("If true, pull fresh data from Health Connect before reading local cache. Default false.")
        val sync: Boolean = false,
    )

    override suspend fun execute(args: Args): String = withContext(Dispatchers.IO) {
        val days = args.days.coerceIn(1, MAX_LOOKBACK_DAYS)
        val syncedDays = if (args.sync) {
            runCatching { healthConnectDataSource.syncRecentDays(days) }
                .onFailure { e ->
                    AppLogger.warn(
                        LogTags.HealthConnect,
                        "tool_sync_failed",
                        "message" to (e.message ?: e::class.simpleName.orEmpty()),
                    )
                }
                .getOrDefault(0)
        } else 0

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val endDate = today.year * 10000 + today.monthValue * 100 + today.dayOfMonth
        val startDate = today.minusDays((days - 1).toLong()).year * 10000 +
            today.minusDays((days - 1).toLong()).monthValue * 100 +
            today.minusDays((days - 1).toLong()).dayOfMonth

        val snapshots = healthSnapshotDao.findInRange(startDate, endDate)
        val result = QueryHealthDataResult(
            days = days,
            startDate = startDate,
            endDate = endDate,
            count = snapshots.size,
            syncedDays = syncedDays,
            snapshots = snapshots.map { it.toItem() },
        )
        json.encodeToString(result)
    }

    private fun HealthSnapshotEntity.toItem(): HealthSnapshotItem =
        HealthSnapshotItem(
            date = date,
            steps = steps,
            avgHeartRate = avgHeartRate,
            minHeartRate = minHeartRate,
            maxHeartRate = maxHeartRate,
            sleepDurationMinutes = sleepDurationMinutes,
            sourcePackages = sourcePackages,
        )

    @Serializable
    private data class QueryHealthDataResult(
        val days: Int,
        val startDate: Int,
        val endDate: Int,
        val count: Int,
        val syncedDays: Int,
        val snapshots: List<HealthSnapshotItem>,
    )

    @Serializable
    private data class HealthSnapshotItem(
        val date: Int,
        val steps: Int,
        val avgHeartRate: Int?,
        val minHeartRate: Int?,
        val maxHeartRate: Int?,
        val sleepDurationMinutes: Int?,
        val sourcePackages: String,
    )

    private companion object {
        const val MAX_LOOKBACK_DAYS = 30
        val json = Json { encodeDefaults = true }
    }
}
