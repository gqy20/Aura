package com.xiaoqi.companion

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.presence.runtime.DreamLoopScheduler
import com.xiaoqi.companion.core.prompt.templates.SystemPersona
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CompanionApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var dreamLoopScheduler: DreamLoopScheduler

    override fun onCreate() {
        super.onCreate()

        AppLogger.initialize(BuildConfig.DEBUG)
        SystemPersona.init(this)
        // 启动偏好监听 — 周期可配置后,start() 内部读取 DataStore 当前值(默认 6h)enqueue 一次,
        // 后续用户在 Settings 改档位会触发 UPDATE / cancel。
        dreamLoopScheduler.start()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
