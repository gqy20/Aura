package com.xiaoqi.companion

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.local.LocalModelPreloader
import com.xiaoqi.companion.core.presence.runtime.DreamLoopScheduler
import com.xiaoqi.companion.core.prompt.templates.SystemPersona
import com.xiaoqi.companion.data.source.HealthSyncLifecycleObserver
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CompanionApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var dreamLoopScheduler: DreamLoopScheduler

    @Inject
    lateinit var healthSyncObserver: HealthSyncLifecycleObserver

    @Inject
    lateinit var localModelPreloader: LocalModelPreloader

    override fun onCreate() {
        super.onCreate()

        AppLogger.initialize(BuildConfig.DEBUG)
        SystemPersona.init(this)
        // 启动偏好监听 — 周期可配置后,start() 内部读取 DataStore 当前值(默认 6h)enqueue 一次,
        // 后续用户在 Settings 改档位会触发 UPDATE / cancel。
        dreamLoopScheduler.start()
        // M7 Health Connect: 冷启动 + 进入前台时自动同步(尊重 DataStore 偏好 + 30 分钟防抖)
        ProcessLifecycleOwner.get().lifecycle.addObserver(healthSyncObserver)
        healthSyncObserver.onColdStart()
        localModelPreloader.preloadIfNeeded()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
