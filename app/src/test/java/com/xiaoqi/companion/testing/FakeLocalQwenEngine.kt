package com.xiaoqi.companion.testing

import com.xiaoqi.companion.core.local.LocalQwenEngine
import com.xiaoqi.companion.core.local.LocalQwenRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 流式 LLM 引擎的 Fake 实现 —— 按构造时给的 [chunks] 依次 emit,记录最后一次请求到 [lastRequest]。
 *
 * 用于 KoogAgentFactoryImplTest、ReactiveCompanionTest、LocalQwenExecutorTest 等需要 LocalQwenEngine
 * 但不关心真实推理逻辑的场景。
 */
class FakeLocalQwenEngine(
    private val chunks: List<String>,
) : LocalQwenEngine {
    var lastRequest: LocalQwenRequest? = null

    override fun stream(request: LocalQwenRequest): Flow<String> = flow {
        lastRequest = request
        chunks.forEach { emit(it) }
    }
}
