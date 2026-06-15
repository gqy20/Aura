package com.xiaoqi.companion.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一日聚合的健康快照。
 *
 * 数据来源:Health Connect → 小米运动健康(国内版支持 HC,详见 docs/research/health-connect-mi-fitness.md)。
 * 不存原始 record(步数每分钟一个、心率每事件一个),只存当日聚合,避免表膨胀。
 *
 * 设计原则:
 * - `date` 用 YYYYMMDD 整数,自然唯一(一天一条)
 * - `sourcePackages` 是 JSON array 字符串(贡献数据的所有 App,例如 ["com.mi.health","com.xiaomi.hm.health"])
 * - `*_json` 字段保留原始分布(心率分桶、睡眠分期),便于 Dream pipeline 用
 */
@Entity(
    tableName = "health_snapshots",
    indices = [
        Index(value = ["date"], unique = true),
    ],
)
data class HealthSnapshotEntity(
    @PrimaryKey val date: Int,
    @ColumnInfo(name = "steps") val steps: Int = 0,
    @ColumnInfo(name = "distance_meters") val distanceMeters: Double = 0.0,
    @ColumnInfo(name = "calories_kcal") val caloriesKcal: Double = 0.0,
    @ColumnInfo(name = "avg_heart_rate") val avgHeartRate: Int? = null,
    @ColumnInfo(name = "resting_heart_rate") val restingHeartRate: Int? = null,
    @ColumnInfo(name = "min_heart_rate") val minHeartRate: Int? = null,
    @ColumnInfo(name = "max_heart_rate") val maxHeartRate: Int? = null,
    @ColumnInfo(name = "sleep_duration_minutes") val sleepDurationMinutes: Int? = null,
    @ColumnInfo(name = "sleep_stages_json") val sleepStagesJson: String = "[]",
    @ColumnInfo(name = "source_packages") val sourcePackages: String = "[]",
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
)
