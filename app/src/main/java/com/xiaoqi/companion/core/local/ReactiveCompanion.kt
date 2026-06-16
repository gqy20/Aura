package com.xiaoqi.companion.core.local

import com.xiaoqi.companion.core.companion.KoogAgentEvent
import com.xiaoqi.companion.core.companion.KoogAgentWrapper
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.core.prompt.BuiltPrompt
import com.xiaoqi.companion.core.tools.LocalToolPromptResult
import com.xiaoqi.companion.core.tools.ToolCallRecorder
import ai.koog.agents.core.tools.ToolRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * 本地 LLM 路径(dual-mind 架构 §1.2 的"觉察面"前驱)。
 *
 * 命名背景:Phase 0 把原 `LocalQwenAgentWrapper` 重命名为 `ReactiveCompanion`,
 * 体现"对用户消息的本地觉察响应"语义 — 它**不是云端对话体的轻量替代**,
 * 而是"持续在场"那条线的载体。
 *
 * 2026-06-16 PR A + PR B 行为契约:
 * - 走与云端 `KoogPromptExecutorWrapper` 一致的 `KoogAgentWrapper` 4 方法契约
 * - `runStreaming` 走 `runEvents` 单一入口(不再旁路 engine.stream)
 * - `runStructured` 走 `run()` + Json 解析(本地 LLM 不支持原生 function calling)
 * - `allowTools=true` 记 warn 日志,行为上仍 `allowTools=false`
 * - Vision 走 `LocalQwenRequest.imageBase64/imageMediaType` 字段,再由 native 转 `MultimodalPrompt`
 *   (Qwen3.5 模型目录已带 visual.mnn,见 `LocalQwenModelCatalog.requiredFiles`)
 */
class ReactiveCompanion(
    private val engine: LocalQwenEngine,
    private val modelName: String = "",
    private val toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    private val toolCallRecorder: ToolCallRecorder? = null,
) : KoogAgentWrapper {

    override suspend fun run(prompt: BuiltPrompt): String =
        runEvents(prompt)
            .mapNotNull { (it as? KoogAgentEvent.TextDelta)?.text }
            .toList()
            .joinToString("")

    override suspend fun <T> runStructured(
        prompt: BuiltPrompt,
        serializer: KSerializer<T>,
        examples: List<T>,
    ): T = StructuredLocalParser.parse(run(prompt), serializer, examples)

    override fun runStreaming(prompt: BuiltPrompt): Flow<String> =
        runEvents(prompt).mapNotNull { event -> (event as? KoogAgentEvent.TextDelta)?.text }

    override fun runEvents(prompt: BuiltPrompt): Flow<KoogAgentEvent> = flow {
        if (!prompt.allowTools || prompt.hasImage || toolRegistry.tools.isEmpty()) {
            engine.stream(prompt.toLocalRequest()).collect { emit(KoogAgentEvent.TextDelta(it)) }
            return@flow
        }

        val toolEnabledPrompt = prompt.copy(
            systemPrompt = buildString {
                append(prompt.systemPrompt)
                append("\n\n")
                append(LocalToolProtocol.buildToolInstructionBlock(toolRegistry))
            }
        )

        var currentPrompt = toolEnabledPrompt
        var round = 0
        val allToolResults = mutableListOf<LocalToolPromptResult>()
        while (round < MAX_TOOL_ROUNDS) {
            round++
            val responseText = engine.stream(currentPrompt.toLocalRequest())
                .toList()
                .joinToString("")
            val toolCalls = LocalToolProtocol.parseToolCalls(responseText)
            if (toolCalls.isEmpty()) {
                emit(KoogAgentEvent.TextDelta(responseText))
                return@flow
            }

            val execution = LocalToolExecutor(
                registry = toolRegistry,
                recorder = toolCallRecorder,
                sessionId = DEFAULT_SESSION_ID,
            ).execute(toolCalls)
            execution.events.forEach { emit(it) }
            allToolResults += execution.transcripts

            currentPrompt = toolEnabledPrompt.copy(
                userMessage = buildString {
                    append(prompt.userMessage)
                    append("\n\n")
                    append(LocalToolProtocol.buildToolContextBlock(allToolResults))
                }
            )
        }

        AppLogger.warn(
            LogTags.LocalModel,
            "local_tool_round_limit_reached",
            "maxRounds" to MAX_TOOL_ROUNDS,
        )
        emit(KoogAgentEvent.TextDelta(LocalToolProtocol.roundLimitFallbackMessage()))
    }

    private fun BuiltPrompt.toLocalRequest(): LocalQwenRequest {
        if (allowTools && toolRegistry.tools.isEmpty()) {
            AppLogger.warn(
                LogTags.LocalModel,
                "local_qwen_tool_request_skipped",
                "reason" to "No registered local tools; allowTools ignored",
            )
        }
        return LocalQwenRequest(
            systemPrompt = systemPrompt,
            userMessage = userMessage,
            modelName = modelName,
            allowTools = allowTools,
            imageBase64 = imageBase64,
            imageMediaType = imageMediaType,
        )
    }

    private companion object {
        const val DEFAULT_SESSION_ID = "default"
        const val MAX_TOOL_ROUNDS = 4
    }
}

/**
 * 本地 LLM 的结构化输出解析:从纯文本完成里抠 JSON block,再喂给 KSerializer。
 *
 * 解析顺序:
 * 1. 提取首个匹配的 `{ ... }` 或 `[ ... ]` block(跳过字符串字面量内的括号)
 * 2. 若上文未命中,尝试把整段文本(剥掉 ``` 围栏)当 JSON
 * 3. 全部失败时 fallback 到 `examples.firstOrNull()`(与 `ConversationReflection.parseExampleOrFallback` 同款兜底)
 *
 * 这是本地 LLM 不支持原生 function calling 的妥协方案;云端走 Koog `executeStructured` 不经过这里。
 */
internal object StructuredLocalParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun <T> parse(raw: String, serializer: KSerializer<T>, examples: List<T>): T {
        val stripped = raw.trim().removeSurrounding("```json").removeSurrounding("```").trim()
        val candidates = listOfNotNull(
            extractJsonBlock(stripped),
            stripped.takeIf { it.startsWith('{') || it.startsWith('[') },
        )
        for (candidate in candidates) {
            runCatching { json.decodeFromString(serializer, candidate) }
                .onSuccess { return it }
        }
        return examples.firstOrNull()
            ?: throw IllegalStateException(
                "Local Qwen structured parse failed and no fallback example was provided. rawLength=${raw.length}",
            )
    }

    private fun extractJsonBlock(text: String): String? {
        val start = text.indexOfFirst { it == '{' || it == '[' }
        if (start < 0) return null
        val open = text[start]
        val close = if (open == '{') '}' else ']'
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until text.length) {
            val c = text[i]
            if (escape) {
                escape = false
                continue
            }
            if (c == '\\') {
                escape = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (inString) continue
            when (c) {
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }
}
