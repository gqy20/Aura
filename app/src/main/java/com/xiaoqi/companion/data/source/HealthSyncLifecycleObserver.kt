package com.xiaoqi.companion.data.source

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.data.datastore.AppPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * App 进入前台时自动触发一次健康同步。
 *
 * 触发条件全部满足才会同步:
 * 1. [ProcessLifecycleOwner] 报告 ON_START(从后台 / 冷启动回到前台)
 * 2. 用户没在 DataStore 里关掉 auto-sync([AppPreferences.healthAutoSyncEnabled])
 * 3. 防抖由 [HealthSyncManager] 内部把控(默认 30 分钟一次)
 *
 * 实现用 [ProcessLifecycleOwner] 而不是 [LifecycleOwner] 单独观察 Activity —
 * - ProcessLifecycleOwner 聚合全 App 的 Activity,任意 Activity 启动时都计数
 * - 冷启动不算 "回到前台"(不会触发 ON_START after first ON_CREATE),
 *   所以"打开 App 就同步"还得靠 cold-start 兜底 — 由 [CompanionApplication] 的
 *   [onCreate] 调一次 `forceSync()` 处理。
 */
@Singleton
class HealthSyncLifecycleObserver @Inject constructor(
    private val healthSyncManager: HealthSyncManager,
    private val healthConnectDataSource: HealthConnectDataSource,
    private val appPreferences: AppPreferences,
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStart(owner: LifecycleOwner) {
        scope.launch { maybeSync(force = false, reason = "foreground") }
    }

    /** 由 Application.onCreate 调一次 — 冷启动也算"前台"。 */
    fun onColdStart() {
        scope.launch { maybeSync(force = true, reason = "cold_start") }
    }

    private suspend fun maybeSync(force: Boolean, reason: String) {
        // 三重门:SDK 不可用 / 用户关掉 / 30 分钟防抖。前两关直接 return,避免无意义 spam。
        val available = runCatching { healthConnectDataSource.isAvailable() }
            .onFailure { AppLogger.warn(LogTags.HealthConnect, "availability_check_failed", "err" to it.message) }
            .getOrDefault(false)
        if (!available) {
            AppLogger.info(LogTags.HealthConnect, "auto_sync_skipped_no_runtime", "reason" to reason)
            return
        }
        val enabled = runCatching { appPreferences.healthAutoSyncEnabled.first() }
            .getOrDefault(true)
        if (!enabled) {
            AppLogger.info(LogTags.HealthConnect, "auto_sync_disabled_by_pref")
            return
        }
        AppLogger.info(LogTags.HealthConnect, "auto_sync_trigger", "reason" to reason)
        healthSyncManager.requestSync(force = force)
    }
}
