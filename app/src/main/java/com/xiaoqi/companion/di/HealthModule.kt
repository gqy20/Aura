package com.xiaoqi.companion.di

import com.xiaoqi.companion.data.source.HealthConnectDataSource
import com.xiaoqi.companion.data.source.HealthSource
import com.xiaoqi.companion.data.source.SensorManagerHealthSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Health 多源聚合 — Hilt multibinding 把所有 [HealthSource] 实现合并成 [Set],
 * [com.xiaoqi.companion.data.source.HealthSyncManager] 拿到这个 Set 后
 * 串行跑 isAvailable + syncRecentDays,任何一个 source 写出"天有数据"就视为成功。
 *
 * 顺序由 Set 插入顺序决定 — 同一 set 内顺序在 Hilt 中不保证,但**我们用 key 不重叠
 * 避免冲突**:HC 写 steps/心率/睡眠,Sensor 只写 steps 并通过 [merge-write] 保留
 * HC 写入的其他列(见 HealthSnapshotDao.updateStepsOnly),所以顺序不影响最终结果。
 *
 * 新增 source 时:在 [HealthModule] 加一个 `@Binds @IntoSet`,无需改 HealthSyncManager。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class HealthModule {

    @Binds
    @IntoSet
    abstract fun bindHealthConnectSource(impl: HealthConnectDataSource): HealthSource

    @Binds
    @IntoSet
    abstract fun bindSensorManagerSource(impl: SensorManagerHealthSource): HealthSource
}
