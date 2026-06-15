package com.xiaoqi.companion.data.source

/**
 * 多源适配层 — 把"从哪儿读健康数据"抽成可插拔策略。
 *
 * 设计动机:
 * - Health Connect (Google): Pixel / Samsung / 部分小米可用,realme ColorOS / 多数国产 ROM 装了 APK 但 service 死
 * - SensorManager (内置): 步数 TYPE_STEP_COUNTER 通用,只需 ACTIVITY_RECOGNITION,所有 9+ 设备都能跑
 * - 小米健康云开放平台: 需合作伙伴,目前封闭
 * - Mi Fitness "设备授权管理" 通道: 需白名单,商业谈判周期
 *
 * 当前实现是 [HealthConnectDataSource] (Health Connect);后续可以加
 * [SensorManagerHealthSource] (本地 sensor) 作为兜底/主源,数据聚合层
 * ([HealthSyncManager] / [com.xiaoqi.companion.core.tools.QueryHealthDataTool])
 * 通过本接口透明切换,UI 不感知。
 *
 * 聚合策略建议:
 * - **availability 优先级**:先查 [HealthConnectDataSource],不可用再 fallback Sensor
 * - **数据合并**:Health Connect 拿到的步数 + Sensor 拿到的步数,去重(同一天的同一指标)
 * - **指标差异**:Health Connect 有心率/睡眠,Sensor 暂时只能给步数 — 各自负责自己的指标集
 */
interface HealthSource {
    /**
     * 设备上此数据源是否**真的能用**——controller APK 装着但 service 死的,
     * 不算可用。深检查,suspend 因为 HC SDK 需要 IO dispatcher。
     */
    suspend fun isAvailable(): Boolean

    /**
     * 同步最近 [days] 天。返回写入的"天有数据"快照数(同 [HealthConnectDataSource.syncRecentDays])。
     * 0 = 不可用或没数据,**不抛**异常,让上层优雅降级。
     */
    suspend fun syncRecentDays(days: Int = 7): Int

    /**
     * 此源负责的指标集(用于 UI 解释"为什么心率空着但步数有数据")。
     * 例如 Health Connect 是 STEPS / HEART_RATE / SLEEP,SensorManager 只 STEPS。
     */
    val supportedMetrics: Set<HealthMetric>
}

enum class HealthMetric(val displayName: String) {
    STEPS("步数"),
    HEART_RATE("心率"),
    SLEEP("睡眠"),
}
