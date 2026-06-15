package com.xiaoqi.companion.data.source

import com.xiaoqi.companion.data.datastore.AppPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M7 Health 多源链 — 用 mock 验证 [HealthSyncManager] 的状态机。
 *
 * 覆盖:
 * - 任一 source 写出"天有数据" → 整体 Success
 * - 全部 source 失败 → Failure,reason 拼接
 * - 全部 source 不可用 → Failure("no source available")
 * - 全部 source 都 throw → 单个 throw 不影响其他 source
 * - 30 分钟防抖 vs force=true 绕过
 * - setHealthLastSyncAt 仅在成功时持久化
 *
 * 测试关键:把内部 IO scope 用 [HealthSyncManager.testScopeOverride] 换成
 * `TestScope`,这样 `runTest` 的 `advanceUntilIdle` 才能驱动到 manager 内部
 * `scope.launch` 里的 work。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HealthSyncManagerTest {

    private fun makeManager(
        sources: Set<HealthSource>,
        healthLastSyncAt: Long = 0L,
        testScope: TestScope,
    ): Pair<HealthSyncManager, AppPreferences> {
        val prefs = mockk<AppPreferences>(relaxed = true).also {
            every { it.healthLastSyncAt } returns flowOf(healthLastSyncAt)
            coEvery { it.setHealthLastSyncAt(any()) } returns Unit
        }
        return HealthSyncManager(sources = sources, appPreferences = prefs).also {
            it.testScopeOverride(testScope)
        } to prefs
    }

    @Test fun `one source succeeds, whole sync is Success`() = runTest {
        val hc = mockk<HealthSource>().also {
            every { it.supportedMetrics } returns setOf(HealthMetric.STEPS, HealthMetric.HEART_RATE)
            coEvery { it.isAvailable() } returns true
            coEvery { it.syncRecentDays() } returns 7
        }
        val sensor = mockk<HealthSource>().also {
            every { it.supportedMetrics } returns setOf(HealthMetric.STEPS)
            coEvery { it.isAvailable() } returns true
            coEvery { it.syncRecentDays() } returns 1
        }
        val (manager, _) = makeManager(setOf(hc, sensor), testScope = this)
        manager.requestSync(force = true)
        advanceUntilIdle()
        val state = manager.state.value
        assertTrue("expected Success but was $state", state is HealthSyncManager.SyncState.Success)
        val success = state as HealthSyncManager.SyncState.Success
        assertEquals(8, success.daysWithData) // 7 (HC) + 1 (Sensor)
    }

    @Test fun `all sources unavailable yields Failure no source available`() = runTest {
        val a = mockk<HealthSource>().also {
            coEvery { it.isAvailable() } returns false
        }
        val b = mockk<HealthSource>().also {
            coEvery { it.isAvailable() } returns false
        }
        val (manager, _) = makeManager(setOf(a, b), testScope = this)
        manager.requestSync(force = true)
        advanceUntilIdle()
        val state = manager.state.value
        assertTrue("expected Failure but was $state", state is HealthSyncManager.SyncState.Failure)
        val failure = state as HealthSyncManager.SyncState.Failure
        assertTrue(
            "reason should mention no source, was: ${failure.reason}",
            failure.reason.contains("no source available"),
        )
    }

    @Test fun `sources throwing does not abort the chain`() = runTest {
        val throws = mockk<HealthSource>(relaxed = true).also {
            coEvery { it.isAvailable() } returns true
            coEvery { it.syncRecentDays() } throws IllegalStateException("simulated")
        }
        val ok = mockk<HealthSource>(relaxed = true).also {
            coEvery { it.isAvailable() } returns true
            coEvery { it.syncRecentDays() } returns 2
        }
        val (manager, _) = makeManager(setOf(throws, ok), testScope = this)
        manager.requestSync(force = true)
        advanceUntilIdle()
        val state = manager.state.value
        assertTrue("expected Success even when one source throws, was $state", state is HealthSyncManager.SyncState.Success)
        val success = state as HealthSyncManager.SyncState.Success
        assertEquals(2, success.daysWithData)
    }

    @Test fun `availability check failure does not abort the chain`() = runTest {
        val broken = mockk<HealthSource>(relaxed = true).also {
            coEvery { it.isAvailable() } throws RuntimeException("availability boom")
        }
        val ok = mockk<HealthSource>(relaxed = true).also {
            coEvery { it.isAvailable() } returns true
            coEvery { it.syncRecentDays() } returns 1
        }
        val (manager, _) = makeManager(setOf(broken, ok), testScope = this)
        manager.requestSync(force = true)
        advanceUntilIdle()
        val state = manager.state.value
        assertTrue("expected Success, was $state", state is HealthSyncManager.SyncState.Success)
    }

    @Test fun `empty source set yields Failure with no source available`() = runTest {
        val (manager, _) = makeManager(emptySet(), testScope = this)
        manager.requestSync(force = true)
        advanceUntilIdle()
        val state = manager.state.value
        assertTrue(state is HealthSyncManager.SyncState.Failure)
    }

    @Test fun `setHealthLastSyncAt is persisted only on success`() = runTest {
        val ok = mockk<HealthSource>(relaxed = true).also {
            coEvery { it.isAvailable() } returns true
            coEvery { it.syncRecentDays() } returns 3
        }
        val (manager, prefs) = makeManager(setOf(ok), testScope = this)
        manager.requestSync(force = true)
        advanceUntilIdle()
        coVerify(exactly = 1) { prefs.setHealthLastSyncAt(any()) }
    }

    @Test fun `unsuccessful sync does not persist healthLastSyncAt`() = runTest {
        val dead = mockk<HealthSource>(relaxed = true).also {
            coEvery { it.isAvailable() } returns false
        }
        val (manager, prefs) = makeManager(setOf(dead), testScope = this)
        manager.requestSync(force = true)
        advanceUntilIdle()
        coVerify(exactly = 0) { prefs.setHealthLastSyncAt(any()) }
    }

    @Test fun `debounce skips resync within 30 minutes`() = runTest {
        val ok = mockk<HealthSource>(relaxed = true).also {
            coEvery { it.isAvailable() } returns true
            coEvery { it.syncRecentDays() } returns 5
        }
        val (manager, _) = makeManager(
            sources = setOf(ok),
            healthLastSyncAt = System.currentTimeMillis() - 1_000L,
            testScope = this,
        )
        manager.requestSync(force = false)
        advanceUntilIdle()
        val state = manager.state.value
        assertTrue(
            "expected Skipped within debounce window, was $state",
            state is HealthSyncManager.SyncState.Skipped,
        )
        // syncRecentDays should NOT have been called
        coVerify(exactly = 0) { ok.syncRecentDays() }
    }

    @Test fun `force=true bypasses debounce`() = runTest {
        val ok = mockk<HealthSource>(relaxed = true).also {
            coEvery { it.isAvailable() } returns true
            coEvery { it.syncRecentDays() } returns 1
        }
        val (manager, _) = makeManager(
            sources = setOf(ok),
            healthLastSyncAt = System.currentTimeMillis() - 1_000L,
            testScope = this,
        )
        manager.requestSync(force = true)
        advanceUntilIdle()
        val state = manager.state.value
        assertTrue("expected Success, was $state", state is HealthSyncManager.SyncState.Success)
        coVerify(exactly = 1) { ok.syncRecentDays() }
    }
}
