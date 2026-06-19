package com.xiaoqi.companion.core.presence.runtime

import com.xiaoqi.companion.core.local.LocalQwenEngine
import com.xiaoqi.companion.core.local.LocalQwenModelDownloader
import com.xiaoqi.companion.core.local.LocalQwenRequest
import com.xiaoqi.companion.data.datastore.AppPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalQwenExecutorTest {

    @Test
    fun execute_gluesSystemAndUserMessages() = runTest {
        val engine = StubLocalQwenEngine(flowOf("hello", " ", "world"))
        val executor = LocalQwenExecutor(engine, fakeAppPreferences(), fakeDownloader())

        val result = executor.execute(
            LocalQwenExecutor.Request(
                systemPrompt = "you are aura",
                userMessage = "summarize",
                maxTokens = 100,
                temperature = 0.5f,
            ),
        )

        assertEquals("hello world", result.text)
        assertEquals("you are aura", engine.lastRequest?.systemPrompt)
        assertEquals("summarize", engine.lastRequest?.userMessage)
        assertEquals(false, engine.lastRequest?.allowTools ?: true)
        assertNotNull(engine.lastRequest?.inferenceConfig)
        assertEquals(100, engine.lastRequest?.inferenceConfig?.maxNewTokens)
        assertEquals(0.5f, engine.lastRequest?.inferenceConfig?.temperature)
    }

    @Test
    fun execute_passesScenarioSpecificInferenceOverrides() = runTest {
        val engine = StubLocalQwenEngine(flowOf("done"))
        val executor = LocalQwenExecutor(engine, fakeAppPreferences(), fakeDownloader())

        executor.execute(
            LocalQwenExecutor.Request(
                systemPrompt = "detect",
                userMessage = "snapshot",
                maxTokens = 500,
                temperature = 0.2f,
                topK = 20,
                topP = 0.85f,
                minP = 0.03f,
                repetitionPenalty = 1.1f,
                threadNum = 3,
                backendType = "cpu",
            ),
        )

        val config = engine.lastRequest?.inferenceConfig
        assertNotNull(config)
        assertEquals(500, config?.maxNewTokens)
        assertEquals(0.2f, config?.temperature)
        assertEquals(20, config?.topK)
        assertEquals(0.85f, config?.topP)
        assertEquals(0.03f, config?.minP)
        assertEquals(1.1f, config?.repetitionPenalty)
        assertEquals(3, config?.threadNum)
        assertEquals("cpu", config?.backendType)
    }

    @Test
    fun execute_returnsEmptyOnException() = runTest {
        val engine = StubLocalQwenEngine(flow { throw IllegalStateException("MNN not loaded") })
        val executor = LocalQwenExecutor(engine, fakeAppPreferences(), fakeDownloader())

        val result = executor.execute(
            LocalQwenExecutor.Request("sys", "user"),
        )

        assertEquals("", result.text)
        assertTrue(result.truncated)
    }

    @Test
    fun parsePatternDetectOutput_validJson_returnsDrafts() {
        val executor = LocalQwenExecutor(StubLocalQwenEngine(flowOf("")), fakeAppPreferences(), fakeDownloader())
        val raw = """
            [
              { "headline": "周日下午情绪偏低", "body": "连续 3 周", "evidence_ids": ["m1", "m2"], "confidence": 0.78 },
              { "headline": "睡眠关键词上升", "body": "你最近提到 4 次", "evidence_ids": [], "confidence": 0.65 }
            ]
        """.trimIndent()

        val drafts = executor.parsePatternDetectOutput(raw)

        assertEquals(2, drafts.size)
        assertEquals("周日下午情绪偏低", drafts[0].headline)
        assertEquals(0.78f, drafts[0].confidence, 0.001f)
        assertEquals(listOf("m1", "m2"), drafts[0].evidenceMessageIds)
        assertEquals("近 7 天", drafts[0].relevanceWindow)
        assertEquals("PATTERN_DETECT", drafts[0].triggerType)
    }

    @Test
    fun parsePatternDetectOutput_invalidJson_returnsEmpty() {
        val executor = LocalQwenExecutor(StubLocalQwenEngine(flowOf("")), fakeAppPreferences(), fakeDownloader())
        val drafts = executor.parsePatternDetectOutput("not json at all")
        assertTrue(drafts.isEmpty())
    }

    @Test
    fun parsePatternDetectOutput_stripsMarkdownFences() {
        val executor = LocalQwenExecutor(StubLocalQwenEngine(flowOf("")), fakeAppPreferences(), fakeDownloader())
        val raw = """
            ```json
            [{ "headline": "X", "body": "Y", "evidence_ids": [], "confidence": 0.7 }]
            ```
        """.trimIndent()

        val drafts = executor.parsePatternDetectOutput(raw)

        assertEquals(1, drafts.size)
        assertEquals("X", drafts[0].headline)
    }

    @Test
    fun parsePatternDetectOutput_skipsEntriesWithoutHeadline() {
        val executor = LocalQwenExecutor(StubLocalQwenEngine(flowOf("")), fakeAppPreferences(), fakeDownloader())
        val raw = """
            [
              { "body": "no headline here" },
              { "headline": "valid", "body": "ok", "evidence_ids": [], "confidence": 0.5 }
            ]
        """.trimIndent()

        val drafts = executor.parsePatternDetectOutput(raw)

        assertEquals(1, drafts.size)
        assertEquals("valid", drafts[0].headline)
    }

    @Test
    fun parsePatternDetectOutput_confidenceClampedTo01() {
        val executor = LocalQwenExecutor(StubLocalQwenEngine(flowOf("")), fakeAppPreferences(), fakeDownloader())
        val raw = """
            [{ "headline": "X", "body": "Y", "evidence_ids": [], "confidence": 1.5 }]
        """.trimIndent()

        val drafts = executor.parsePatternDetectOutput(raw)

        assertEquals(1, drafts.size)
        assertEquals(1.0f, drafts[0].confidence, 0.001f)
    }

    // ── Layer 2: 归一化修复 ──

    @Test
    fun parsePatternDetectOutput_singleQuotes_normalized() {
        val executor = LocalQwenExecutor(StubLocalQwenEngine(flowOf("")), fakeAppPreferences(), fakeDownloader())
        // 0.8B 模型常见输出:单引号代替双引号
        val raw = """[{'headline': '周日下午情绪偏低', 'body': '连续3周', 'evidence_ids': ['m1','m2'], 'confidence': 0.78}]"""

        val drafts = executor.parsePatternDetectOutput(raw)

        assertEquals(1, drafts.size)
        assertEquals("周日下午情绪偏低", drafts[0].headline)
        assertEquals(0.78f, drafts[0].confidence, 0.001f)
    }

    @Test
    fun parsePatternDetectOutput_trailingComma_normalized() {
        val executor = LocalQwenExecutor(StubLocalQwenEngine(flowOf("")), fakeAppPreferences(), fakeDownloader())
        val raw = """[{"headline":"X","body":"Y","evidence_ids":[],"confidence":0.7,}]"""

        val drafts = executor.parsePatternDetectOutput(raw)

        assertEquals(1, drafts.size)
        assertEquals("X", drafts[0].headline)
    }

    @Test
    fun parsePatternDetectOutput_textAfterJsonArray_trimmed() {
        val executor = LocalQwenExecutor(StubLocalQwenEngine(flowOf("")), fakeAppPreferences(), fakeDownloader())
        // 模型常在 JSON 后追加解释文字
        val raw = """[{"headline":"X","body":"Y","evidence_ids":[],"confidence":0.7}]
以上是基于数据分析的结果。"""

        val drafts = executor.parsePatternDetectOutput(raw)

        assertEquals(1, drafts.size)
        assertEquals("X", drafts[0].headline)
    }

    @Test
    fun parsePatternDetectOutput_bareObjectWrapped() {
        val executor = LocalQwenExecutor(StubLocalQwenEngine(flowOf("")), fakeAppPreferences(), fakeDownloader())
        // 无方括号的裸对象
        val raw = """这里是分析结果:{"headline":"X","body":"Y","evidence_ids":[],"confidence":0.6}结束"""

        val drafts = executor.parsePatternDetectOutput(raw)

        assertEquals(1, drafts.size)
        assertEquals("X", drafts[0].headline)
    }

    @Test
    fun parsePatternDetectOutput_chinesePunctuation_normalized() {
        val executor = LocalQwenExecutor(StubLocalQwenEngine(flowOf("")), fakeAppPreferences(), fakeDownloader())
        // 中文逗号/冒号
        val raw = """[{"headline"："X"，"body"："Y"，"evidence_ids"：[]，"confidence"：0.7}]"""

        val drafts = executor.parsePatternDetectOutput(raw)

        assertEquals(1, drafts.size)
        assertEquals("X", drafts[0].headline)
    }

    @Test
    fun parsePatternDetectOutput_allLayer2IssuesCombined() {
        val executor = LocalQwenExecutor(StubLocalQwenEngine(flowOf("")), fakeAppPreferences(), fakeDownloader())
        // 单引号 + 尾逗号 + 后缀文字 + 中文标点 — 最坏情况
        val raw = """[{'headline'：'测试标题'，'body'：'描述内容'，'evidence_ids'：[]，'confidence'：0.75，}]
分析完成，共发现 1 条模式。
"""

        val drafts = executor.parsePatternDetectOutput(raw)

        assertEquals(1, drafts.size)
        assertEquals("测试标题", drafts[0].headline)
        assertEquals("描述内容", drafts[0].bodyMarkdown)
        assertEquals(0.75f, drafts[0].confidence, 0.001f)
    }

    // ── Layer 3: 正则兜底 ──

    @Test
    fun parsePatternDetectOutput_freeTextRegexExtractsHeadline() {
        val executor = LocalQwenExecutor(StubLocalQwenEngine(flowOf("")), fakeAppPreferences(), fakeDownloader())
        // 完全不是 JSON 的自由文本 — Layer 3 应该能抠出 headline
        val raw = """基于数据分析,我发现以下模式:
headline: 周末睡眠时间明显延长
body: 相比工作日平均多睡 1.5 小时
confidence: 0.72
这个趋势在最近两周比较明显。"""

        val drafts = executor.parsePatternDetectOutput(raw)

        assertTrue(drafts.isNotEmpty())
        assertEquals("周末睡眠时间明显延长", drafts[0].headline)
    }

    @Test
    fun parsePatternDetectOutput_completelyGarbage_returnsEmpty() {
        val executor = LocalQwenExecutor(StubLocalQwenEngine(flowOf("")), fakeAppPreferences(), fakeDownloader())
        val drafts = executor.parsePatternDetectOutput("今天天气真好，没什么特别的发现")
        assertTrue(drafts.isEmpty())
    }

    private fun fakeAppPreferences(modelName: String = "Qwen3.5-0.8B-MNN"): AppPreferences {
        val prefs = mockk<AppPreferences>(relaxed = true)
        every { prefs.modelName } returns flowOf(modelName)
        return prefs
    }

    private fun fakeDownloader(installedModel: String? = "Qwen3.5-0.8B-MNN"): LocalQwenModelDownloader =
        mockk<LocalQwenModelDownloader>(relaxed = true).also {
            every { it.findAnyInstalledModel() } returns installedModel
        }

    private class StubLocalQwenEngine(
        private val chunks: Flow<String>,
    ) : LocalQwenEngine {
        var lastRequest: LocalQwenRequest? = null

        override fun stream(request: LocalQwenRequest): Flow<String> = flow {
            lastRequest = request
            chunks.collect { emit(it) }
        }
    }
}
