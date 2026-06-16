package com.xiaoqi.companion.core.presence.runtime

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 跟踪"立即跑一次"按钮触发的 DreamLoopWorker 生命周期。
 *
 * 数据源 = `WorkManager.getWorkInfosForUniqueWorkFlow("dream_loop_now")`,
 * 把 [WorkInfo.State] 映射成 5 段 UI 状态(Idle/Queued/Running/Succeeded/Failed),
 * 并从 SUCCEEDED 的 outputData 抽出 savedCount 给 UI 显示"上次新增 N 条"。
 *
 * 与 [DreamLoopScheduler] 拆分的原因:Scheduler 只管调度,Observer 只管观察 —
 * 单元测试可以分别给两个 mock,不让 WorkManager 横切两层职责。
 */
@Singleton
class DreamRunObserver @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {

    enum class Status { IDLE, QUEUED, RUNNING, SUCCEEDED, FAILED }

    data class Snapshot(
        val status: Status,
        val savedCount: Int = 0,
        val draftsParsed: Int = 0,
    ) {
        companion object {
            val IDLE = Snapshot(Status.IDLE)
        }

        val isRunning: Boolean get() = status == Status.QUEUED || status == Status.RUNNING
    }

    private val lastRunAt = MutableStateFlow(0L)
    private val lastSavedCount = MutableStateFlow(0)

    val state: StateFlow<Snapshot> = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkFlow(DreamLoopScheduler.UNIQUE_TRIGGER_NAME)
        .map { infos -> infos.firstOrNull()?.toSnapshot() ?: Snapshot.IDLE }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = Snapshot.IDLE,
        )

    val lastSuccessAtMs: StateFlow<Long> = lastRunAt.asStateFlow()
    val lastSuccessSavedCount: StateFlow<Int> = lastSavedCount.asStateFlow()

    private fun WorkInfo.toSnapshot(): Snapshot = when (state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> Snapshot(Status.QUEUED)
        WorkInfo.State.RUNNING -> Snapshot(Status.RUNNING)
        WorkInfo.State.SUCCEEDED -> {
            val saved = outputData.getInt(DreamLoopWorker.KEY_SAVED_COUNT, 0)
            lastRunAt.value = System.currentTimeMillis()
            lastSavedCount.value = saved
            Snapshot(
                status = Status.SUCCEEDED,
                savedCount = saved,
                draftsParsed = outputData.getInt(DreamLoopWorker.KEY_DRAFTS_PARSED, 0),
            )
        }
        WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> Snapshot(Status.FAILED)
    }
}
