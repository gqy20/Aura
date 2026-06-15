package com.xiaoqi.companion.core.local

import com.xiaoqi.companion.core.companion.KoogAgentEvent
import com.xiaoqi.companion.core.companion.KoogAgentWrapper
import com.xiaoqi.companion.core.prompt.BuiltPrompt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.KSerializer

/**
 * 本地 LLM 路径(dual-mind 架构 §1.2 的"觉察面"前驱)。
 *
 * 命名背景:Phase 0 把原 `LocalQwenAgentWrapper` 重命名为 `ReactiveCompanion`,
 * 体现"对用户消息的本地觉察响应"语义 — 它**不是云端对话体的轻量替代**,
 * 而是"持续在场"那条线的载体。
 *
 * Phase 1 拆云端对话体 / 本地觉察面后,`ReactiveCompanion` 应实现独立接口(不再
 * 复用 Koog 的 `KoogAgentWrapper`),由 `LocalQwenExecutor` 直接调它。
 *
 * 当前实现仍是 `KoogAgentWrapper` 适配器,目的:不在 M3 期间破坏云端对话体调用链。
 * KoogAgentFactoryImpl 内的二选一分支已在 Phase 0 注释中标记为 `@Deprecated`。
 */
class ReactiveCompanion(
    private val engine: LocalQwenEngine,
    private val modelName: String = "",
) : KoogAgentWrapper {

    override suspend fun run(prompt: BuiltPrompt): String =
        engine.stream(prompt.toLocalRequest()).toList().joinToString("")

    override suspend fun <T> runStructured(
        prompt: BuiltPrompt,
        serializer: KSerializer<T>,
        examples: List<T>,
    ): T {
        throw UnsupportedOperationException("Local Qwen text MVP does not support structured output yet.")
    }

    override fun runStreaming(prompt: BuiltPrompt): Flow<String> =
        engine.stream(prompt.toLocalRequest())

    override fun runEvents(prompt: BuiltPrompt): Flow<KoogAgentEvent> = flow {
        engine.stream(prompt.toLocalRequest()).collect { emit(KoogAgentEvent.TextDelta(it)) }
    }

    private fun BuiltPrompt.toLocalRequest(): LocalQwenRequest {
        if (hasImage) {
            throw UnsupportedOperationException("Vision prompts are not supported by the local Qwen text MVP yet.")
        }
        return LocalQwenRequest(
            systemPrompt = systemPrompt,
            userMessage = userMessage,
            modelName = modelName,
            allowTools = false,
        )
    }
}
