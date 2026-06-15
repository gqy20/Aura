package com.xiaoqi.companion.data.source

import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.data.datastore.AppPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Health Connect 同步管理器 — 把 [HealthConnectDataSource] 包成可观察的状态机。
 *
 * 设计原则:
 * - **防抖**:同进程内 [DEBOUNCE_MILLIS] 内多次触发只跑一次,避免回到桌面/重启应用反复拉取。
 * - **互斥**:多协程同时请求也只跑一次(用 [Mutex])。
 * - **状态对外暴露** [StateFlow],UI 只需 collect 就能拿到 loading / success / failure。
 * - **同步后回写** [AppPreferences.setHealthLastSyncAt],用于 UI 展示"上次同步:N 分钟前"。
 */
@Singleton
class HealthSyncManager @Inject constructor(
    private val dataSource: HealthConnectDataSource,
    private val appPreferences: AppPreferences,
) {

    sealed interface SyncState {
        data object Idle : SyncState
        data object Syncing : SyncState
        data class Skipped(val sinceLastMs: Long, val atMillis: Long) : SyncState
        data class Success(val daysWithData: Int, val atMillis: Long) : SyncState
        data class Failure(val reason: String, val atMillis: Long) : SyncState
    }

    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var inflight: Job? = null

    /**
     * 触发一次同步(带防抖)。由 [LifecycleObserver] / [Settings] 按钮 / 任何上层调用。
     * 防抖命中时返回 false,真正跑了返回 true。
     */
    fun requestSync(force: Boolean = false) {
        if (inflight?.isActive == true) {
            AppLogger.info(LogTags.HealthConnect, "sync_already_inflight")
            return
        }
        inflight = scope.launch {
            mutex.withLock {
                val now = System.currentTimeMillis()
                if (!force) {
                    val last = appPreferences.healthLastSyncAt.first()
                    if (now - last < DEBOUNCE_MILLIS && _state.value is SyncState.Success) {
                        AppLogger.info(LogTags.HealthConnect, "sync_debounced", "sinceLastMs" to (now - last))
                        _state.value = SyncState.Skipped(now - last, now)
                        return@withLock
                    }
                }
                _state.value = SyncState.Syncing
                val result = runCatching { dataSource.syncRecentDays() }
                _state.value = result.fold(
                    onSuccess = { days -> SyncState.Success(days, System.currentTimeMillis()) },
                    onFailure = { e -> SyncState.Failure(e.message ?: e::class.simpleName.orEmpty(), System.currentTimeMillis()) },
                )
                if (result.isSuccess) {
                    appPreferences.setHealthLastSyncAt(System.currentTimeMillis())
                }
            }
        }
    }

    companion object {
        /** 30 分钟防抖 — 避免每次回前台都重新同步,减少 HC 配额消耗。 */
        const val DEBOUNCE_MILLIS: Long = 30L * 60L * 1000L
    }
}
