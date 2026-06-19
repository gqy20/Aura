package com.xiaoqi.companion.core.presence.runtime

import android.content.Context
import androidx.work.WorkerParameters
import com.xiaoqi.companion.core.local.LocalQwenEngine
import com.xiaoqi.companion.core.local.LocalQwenModelDownloader
import com.xiaoqi.companion.data.datastore.AppPreferences
import com.xiaoqi.companion.data.repository.InsightRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * DreamLoopWorker 构造 + 依赖注入的 smoke test。
 *
 * **集成行为**(model 调用 / 解析 / saveIfValid 链路)留到真机 PoC 阶段验证 —
 * `withContext(Dispatchers.IO)` 跨线程后 mockk 跨线程 mock 不稳,test 收益不高。
 * 实际解析 / Validator / saveIfValid 单元测试已分别覆盖:
 * - `LocalQwenExecutorTest` 覆盖 parsePatternDetectOutput 6 case
 * - `InsightValidatorTest` 覆盖 4 道规则 8 case
 * - `InsightRepositoryTest` 覆盖 saveIfValid
 */
class DreamLoopWorkerTest {

    @Test
    fun worker_canBeConstructedWithAllDependencies() {
        val ctx = mockk<Context>(relaxed = true)
        val params = mockk<WorkerParameters>(relaxed = true)
        val dataCollector = mockk<DreamDataCollector>(relaxed = true)
        val engine = mockk<LocalQwenEngine>(relaxed = true)
        val prefs = mockk<AppPreferences>(relaxed = true)
        every { prefs.modelName } returns flowOf("Qwen3.5-0.8B-MNN")
        val downloader = mockk<LocalQwenModelDownloader>(relaxed = true)
        every { downloader.findAnyInstalledModel() } returns "Qwen3.5-0.8B-MNN"
        val executor = LocalQwenExecutor(engine, prefs, downloader)
        val insightRepo = mockk<InsightRepository>(relaxed = true)

        val worker = DreamLoopWorker(ctx, params, dataCollector, executor, insightRepo, prefs)

        assertNotNull(worker)
    }
}
