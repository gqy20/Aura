package com.xiaoqi.companion.data.source

import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.data.datastore.AppPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Health 多源同步管理器 — 把多个 [HealthSource] 串成"HC 优先 + Sensor 兜底"的执行链,
 * 包成可观察的状态机。
 *
 * 设计原则:
 * - **多源链**:每个 source 独立 isAvailable,通过的 source 跑 syncRecentDays,**写同一张表** —
 *   写表是 upsert by date,后写的字段会覆盖;HC 写心率/睡眠,Sensor 写步数,**互不干扰**。
 * - **失败软降级**:一个 source 抛异常不影响其他 source;只要任意一个 source 写出
 *   "天有数据"就视为整体 Success。
 * - **防抖**:同进程内 [DEBOUNCE_MILLIS] 内多次触发只跑一次,避免回到桌面/重启应用反复拉取。
 * - **互斥**:多协程同时请求也只跑一次(用 [Mutex])。
 * - **状态对外暴露** [StateFlow],UI 只需 collect 就能拿到 loading / success / failure。
 * - **同步后回写** [AppPreferences.setHealthLastSyncAt],用于 UI 展示"上次同步:N 分钟前"。
 *
 * source 顺序含义:
 * - HC 在前(它能拉到心率/睡眠,数据维度最全)
 * - Sensor 在后(无 HC 时仍能给出今日步数,在 realme ColorOS 上是唯一能跑的健康源)
 * - 顺序是**优先级**,不是"二选一" — 两个都跑,但 Sensor 的步数会被 HC 同一天的步数覆盖
 *   (HC 优先于本机 sensor;Sensor 是 HC 不存在时的兜底)。
 *
 * 测试钩子:
 * - 生产用 Hilt `@Inject` 主构造,默认 `Dispatchers.IO`。
 * - 测试用 [testScopeOverride] 显式注入 `TestScope`,让 `runTest` 的 `advanceUntilIdle`
 *   能驱动到本类内部 `scope.launch` 里的 work。
 */
@Singleton
class HealthSyncManager @Inject constructor(
    private val sources: Set<@JvmSuppressWildcards HealthSource>,
    private val appPreferences: AppPreferences,
) {

    private var scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    /**
     * 测试专用 — 替换内部 scope 为 `TestScope`,使得 `runTest { advanceUntilIdle() }`
     * 能 wait 到本类内部 `scope.launch` 里的 work 完成。
     * 生产代码不会调到这里。
     */
    @Suppress("unused")
    fun testScopeOverride(newScope: CoroutineScope) {
        this.scope = newScope
    }

    sealed interface SyncState {
        data object Idle : SyncState
        data object Syncing : SyncState
        data class Skipped(val sinceLastMs: Long, val atMillis: Long) : SyncState
        data class Success(val daysWithData: Int, val atMillis: Long) : SyncState
        data class Failure(val reason: String, val atMillis: Long) : SyncState
    }

    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    /**
     * 触发一次同步(带防抖)。由 [HealthSyncLifecycleObserver] / Settings 按钮 / 任何上层调用。
     * 防抖命中时返回 false,真正跑了返回 true。
     */
    fun requestSync(force: Boolean = false) {
        scope.launch {
            mutex.withLock {
                val now = System.currentTimeMillis()
                if (!force) {
                    val last = appPreferences.healthLastSyncAt.first()
                    if (last > 0L && now - last < DEBOUNCE_MILLIS) {
                        AppLogger.info(LogTags.HealthConnect, "sync_debounced", "sinceLastMs" to (now - last))
                        _state.value = SyncState.Skipped(now - last, now)
                        return@withLock
                    }
                }
                _state.value = SyncState.Syncing
                val result = runMultiSourceSync()
                _state.value = result.toSyncState(now)
                if (result.totalDaysWithData > 0 || result.succeededSources.isNotEmpty()) {
                    appPreferences.setHealthLastSyncAt(System.currentTimeMillis())
                }
            }
        }
    }

    /**
     * 跑所有可用的 source,聚合各 source 写出的"天有数据"数。
     */
    private suspend fun runMultiSourceSync(): MultiSourceSyncResult {
        if (sources.isEmpty()) {
            AppLogger.warn(LogTags.HealthConnect, "sync_no_sources")
            return MultiSourceSyncResult(
                totalDaysWithData = 0,
                succeededSources = emptyList(),
                failureReasons = listOf("no HealthSource registered"),
            )
        }
        var totalDays = 0
        val succeeded = mutableListOf<String>()
        val failures = mutableListOf<String>()
        sources.forEach { source ->
            val sourceName = source::class.simpleName ?: "HealthSource"
            val available = runCatching { source.isAvailable() }
                .onFailure {
                    AppLogger.warn(
                        LogTags.HealthConnect,
                        "source_availability_failed",
                        "source" to sourceName,
                        "err" to (it.message ?: it::class.simpleName.orEmpty()),
                    )
                }
                .getOrDefault(false)
            if (!available) {
                AppLogger.info(LogTags.HealthConnect, "source_unavailable", "source" to sourceName)
                return@forEach
            }
            val perSourceDays = runCatching { source.syncRecentDays() }
                .fold(
                    onSuccess = { it },
                    onFailure = { e ->
                        AppLogger.warn(
                            LogTags.HealthConnect,
                            "source_sync_failed",
                            "source" to sourceName,
                            "err" to (e.message ?: e::class.simpleName.orEmpty()),
                        )
                        failures.add("$sourceName: ${e.message ?: e::class.simpleName.orEmpty()}")
                        0
                    },
                )
            AppLogger.info(
                LogTags.HealthConnect,
                "source_synced",
                "source" to sourceName,
                "daysWithData" to perSourceDays,
                "metrics" to source.supportedMetrics.map { it.name }.joinToString(","),
            )
            totalDays += perSourceDays
            if (perSourceDays > 0) succeeded.add(sourceName)
        }
        return MultiSourceSyncResult(
            totalDaysWithData = totalDays,
            succeededSources = succeeded,
            failureReasons = failures,
        )
    }

    private fun MultiSourceSyncResult.toSyncState(now: Long): SyncState = when {
        // 任何 source 跑成功 = 整体成功
        succeededSources.isNotEmpty() -> SyncState.Success(totalDaysWithData, now)
        failureReasons.isNotEmpty() -> SyncState.Failure(failureReasons.joinToString("; "), now)
        else -> SyncState.Failure("no source available", now)
    }

    private data class MultiSourceSyncResult(
        val totalDaysWithData: Int,
        val succeededSources: List<String>,
        val failureReasons: List<String>,
    )

    companion object {
        /** 30 分钟防抖 — 避免每次回前台都重新同步,减少 HC 配额消耗。 */
        const val DEBOUNCE_MILLIS: Long = 30L * 60L * 1000L
    }
}
