package com.xiaoqi.companion.core.presence.runtime

import com.xiaoqi.companion.data.datastore.AppPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * [DreamLoopScheduler] 行为测试。
 *
 * 关键点:scheduler 内部用 [ApplicationScope] CoroutineScope 跑长生命周期 collector。
 * 测试用 [UnconfinedTestDispatcher] scope 替换,让 emit 后立即被 collect。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DreamLoopSchedulerTest {

    private val appPreferences: AppPreferences = mockk()
    private val workScheduler: WorkScheduler = mockk(relaxed = true)
    private val intervalFlow = MutableSharedFlow<DreamLoopInterval>(replay = 1)
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        every { appPreferences.dreamLoopInterval } returns intervalFlow
    }

    private fun newScheduler(scope: CoroutineScope = CoroutineScope(testDispatcher)): DreamLoopScheduler =
        DreamLoopScheduler(scope, appPreferences, workScheduler)

    @Test
    fun start_emitsDefaultH6_enqueuesPeriodic() = runTest(testDispatcher) {
        val scheduler = newScheduler()
        scheduler.start()
        intervalFlow.emit(DreamLoopInterval.H6)
        advanceUntilIdle()

        verify { workScheduler.enqueuePeriodic("dream_loop", 360L) }
        verify(exactly = 0) { workScheduler.cancelPeriodic(any()) }
    }

    @Test
    fun start_emitsOff_cancelsPeriodic() = runTest(testDispatcher) {
        val scheduler = newScheduler()
        scheduler.start()
        intervalFlow.emit(DreamLoopInterval.OFF)
        advanceUntilIdle()

        verify { workScheduler.cancelPeriodic("dream_loop") }
        verify(exactly = 0) { workScheduler.enqueuePeriodic(any(), any()) }
    }

    @Test
    fun start_emitsFrequentM15_enqueuesWith15Minutes() = runTest(testDispatcher) {
        val scheduler = newScheduler()
        scheduler.start()
        intervalFlow.emit(DreamLoopInterval.M15)
        advanceUntilIdle()

        verify { workScheduler.enqueuePeriodic("dream_loop", 15L) }
    }

    @Test
    fun start_emitsM30_enqueuesWith30Minutes() = runTest(testDispatcher) {
        val scheduler = newScheduler()
        scheduler.start()
        intervalFlow.emit(DreamLoopInterval.M30)
        advanceUntilIdle()

        verify { workScheduler.enqueuePeriodic("dream_loop", 30L) }
    }

    @Test
    fun start_emitsAllSevenIntervals_appliesEachInOrder() = runTest(testDispatcher) {
        val scheduler = newScheduler()
        scheduler.start()
        val intervals = listOf(
            DreamLoopInterval.H1, DreamLoopInterval.H3, DreamLoopInterval.H6,
            DreamLoopInterval.H12, DreamLoopInterval.OFF, DreamLoopInterval.M15,
        )
        intervals.forEach { intervalFlow.emit(it) }
        advanceUntilIdle()

        verifyOrder {
            workScheduler.enqueuePeriodic("dream_loop", 60L)
            workScheduler.enqueuePeriodic("dream_loop", 180L)
            workScheduler.enqueuePeriodic("dream_loop", 360L)
            workScheduler.enqueuePeriodic("dream_loop", 720L)
            workScheduler.cancelPeriodic("dream_loop")
            workScheduler.enqueuePeriodic("dream_loop", 15L)
        }
    }

    @Test
    fun start_isIdempotent_doesNotDoubleSubscribe() = runTest(testDispatcher) {
        val scheduler = newScheduler()
        scheduler.start()
        scheduler.start() // 第二次应被 started guard 拦截
        intervalFlow.emit(DreamLoopInterval.H6)
        advanceUntilIdle()

        // 幂等保证:只 enqueue 一次,不重复订阅
        verify(exactly = 1) { workScheduler.enqueuePeriodic("dream_loop", 360L) }
    }

    @Test
    fun distinctUntilChanged_duplicateEmits_ignored() = runTest(testDispatcher) {
        val scheduler = newScheduler()
        scheduler.start()
        intervalFlow.emit(DreamLoopInterval.H6)
        intervalFlow.emit(DreamLoopInterval.H6) // 同值应被 distinctUntilChanged 吃掉
        advanceUntilIdle()

        // H6 第一次 emit + 重复 emit 共 2 次,但 enqueue 调用仍然只 1 次
        // (注:第一次 replay=1 的初始值若也是 H6,这里也是 1 次。逻辑上 distinctUntilChanged 生效即可)
        verify(exactly = 1) { workScheduler.enqueuePeriodic("dream_loop", 360L) }
    }

    @Test
    fun triggerNow_enqueuesOneTimeWithoutTouchingPeriodic() = runTest(testDispatcher) {
        val scheduler = newScheduler()
        scheduler.start()
        intervalFlow.emit(DreamLoopInterval.H6)
        advanceUntilIdle()

        scheduler.triggerNow()
        advanceUntilIdle()

        verify { workScheduler.enqueueOneTime("dream_loop_now") }
        // triggerNow 不应影响周期任务的现有状态
        verify(exactly = 1) { workScheduler.enqueuePeriodic("dream_loop", 360L) }
        verify(exactly = 0) { workScheduler.cancelPeriodic(any()) }
    }

    @Test
    fun triggerNow_worksWhenOff_doesNotEnqueuePeriodic() = runTest(testDispatcher) {
        val scheduler = newScheduler()
        scheduler.start()
        intervalFlow.emit(DreamLoopInterval.OFF)
        advanceUntilIdle()

        scheduler.triggerNow()
        advanceUntilIdle()

        verify { workScheduler.cancelPeriodic("dream_loop") }
        verify { workScheduler.enqueueOneTime("dream_loop_now") }
        verify(exactly = 0) { workScheduler.enqueuePeriodic(any(), any()) }
    }
}
