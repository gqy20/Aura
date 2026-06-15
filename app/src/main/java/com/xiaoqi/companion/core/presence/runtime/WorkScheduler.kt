package com.xiaoqi.companion.core.presence.runtime

/**
 * WorkManager 调度门面 — 让 [DreamLoopScheduler] 依赖接口而不是静态 [androidx.work.WorkManager]
 * 单例,便于单测替换为 fake。
 *
 * 三个方法对应 DreamLoop 的三种调度语义:
 * - [enqueuePeriodic] 周期任务(可被 UPDATE / cancel)
 * - [cancelPeriodic] 取消周期任务
 * - [enqueueOneTime] 一次性任务(用于"立即跑一次")
 */
interface WorkScheduler {
    fun enqueuePeriodic(uniqueName: String, intervalMinutes: Long)
    fun cancelPeriodic(uniqueName: String)
    fun enqueueOneTime(uniqueName: String)
}
