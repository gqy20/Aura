package com.xiaoqi.companion.test

import android.app.Application
import com.xiaoqi.companion.BuildConfig
import com.xiaoqi.companion.core.logging.AppLogger

/**
 * 测试专用 Application。
 *
 * 故意不继承 [com.xiaoqi.companion.CompanionApplication]：跳过其 onCreate 里的
 * `dreamLoopScheduler.start()` / Health 同步 / 本地模型预加载 —— 这些会启动 WorkManager，
 * 而 WorkManager 的 Room Invalidation Tracker 线程会跨 Robolectric 测试泄漏，与
 * ShadowLegacySQLiteConnection 多线程冲突（Illegal connection pointer），随机撞翻
 * 任意一个 Room/Robolectric 测试。
 *
 * unit test 全部是非 Hilt（用 mock 依赖），不需要 CompanionApplication 的 @Inject 链路，
 * 只需 AppLogger 可用即可。经 `src/test/AndroidManifest.xml` 全局指定给所有 Robolectric 测试。
 */
class TestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.initialize(BuildConfig.DEBUG)
    }
}
