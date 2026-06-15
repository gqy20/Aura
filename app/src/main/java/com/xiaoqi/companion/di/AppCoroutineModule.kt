package com.xiaoqi.companion.di

import com.xiaoqi.companion.core.presence.runtime.ApplicationScope
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 提供 application 级长生命周期 CoroutineScope。
 * 用于 DreamLoopScheduler 等需要在 Hilt Singleton 生命周期内持续跑的 collector。
 *
 * SupervisorJob 防止某个子 job 失败牵连其他子 job;Default dispatcher 适合纯 CPU/IO 协调。
 */
@Module
@InstallIn(SingletonComponent::class)
object AppCoroutineModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
