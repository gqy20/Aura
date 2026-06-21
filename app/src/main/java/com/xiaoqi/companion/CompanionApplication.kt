package com.xiaoqi.companion

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.xiaoqi.companion.core.config.DebugConfigSeeder
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.local.LocalModelPreloader
import com.xiaoqi.companion.core.presence.runtime.DreamLoopScheduler
import com.xiaoqi.companion.core.prompt.templates.SystemPersona
import com.xiaoqi.companion.data.source.HealthSyncLifecycleObserver
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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

    @Inject
    lateinit var debugConfigSeeder: DebugConfigSeeder

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        AppLogger.initialize(BuildConfig.DEBUG)
        SystemPersona.init(this)
        // debug 构建：从 .env 经 BuildConfig 预填 LLM/MCP 配置到 DataStore，省去每次手填。
        // 异步执行不阻塞启动；release 无 ENV_* 真实值（空占位）且 DEBUG=false，不会执行。
        if (BuildConfig.DEBUG) {
            appScope.launch { debugConfigSeeder.seed() }
        }
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
