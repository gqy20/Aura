package com.xiaoqi.companion.core.presence.runtime

import com.xiaoqi.companion.core.insight.InsightDraft
import com.xiaoqi.companion.core.insight.InsightPrompts
import com.xiaoqi.companion.core.local.LocalQwenEngine
import com.xiaoqi.companion.core.local.MnnInferenceConfig
import com.xiaoqi.companion.core.local.LocalQwenModelDownloader
import com.xiaoqi.companion.core.local.LocalQwenRequest
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.data.datastore.AppPreferences
import com.xiaoqi.companion.data.db.converter.LlmProvider
import com.xiaoqi.companion.data.repository.DefaultLlmValues
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 本地 LLM 抽象层(dual-mind 觉察面 §6)。
 *
 * 包装 [LocalQwenEngine.stream] 暴露:
 * - `execute(prompt, systemContext, maxTokens, temperature)` — 流式收完返回整段
 * - `parsePatternDetectOutput(raw)` — 解析 `[{headline, body, evidence_ids, confidence}]` JSON
 *
 * 当前实现**调真实的 [LocalQwenEngine]**(MNN + Qwen),但**优先路径**是 mock 测试 —
 * 真实接入留到 PoC 阶段。`execute` 用 `runCatching` 兜底,任何异常 → 空字符串,
 * 让 `DreamLoopWorker` 走 `Result.retry()`。
 */
@Singleton
class LocalQwenExecutor @Inject constructor(
    private val engine: LocalQwenEngine,
    private val appPreferences: AppPreferences,
    private val localQwenModelDownloader: LocalQwenModelDownloader,
) {

    data class Request(
        val systemPrompt: String,
        val userMessage: String,
        val maxTokens: Int = 300,
        val temperature: Float = 0.3f,
        val topK: Int? = null,
        val topP: Float? = null,
        val minP: Float? = null,
        val repetitionPenalty: Float? = null,
        val threadNum: Int? = null,
        val backendType: String? = null,
        /** null = 从 AppPreferences 读主对话选中的本地模型名 */
        val modelName: String? = null,
    )

    data class ExecutionResult(
        val text: String,
        val latencyMs: Long,
        val truncated: Boolean,
        /** null = 成功无错误;非 null = 异常 message,worker 据此走 failure/retry */
        val errorMessage: String? = null,
    )

    suspend fun execute(req: Request): ExecutionResult {
        val startedAt = System.currentTimeMillis()
        val resolvedModelName = req.modelName ?: resolveActiveLocalModelName()
        val defaultConfig = MnnInferenceConfig.forCurrentDevice()
        return runCatching {
            val chunks = engine.stream(
                LocalQwenRequest(
                    systemPrompt = req.systemPrompt,
                    userMessage = req.userMessage,
                    modelName = resolvedModelName,
                    allowTools = false,
                    inferenceConfig = defaultConfig.copy(
                        temperature = req.temperature,
                        topK = req.topK ?: defaultConfig.topK,
                        topP = req.topP ?: defaultConfig.topP,
                        minP = req.minP ?: defaultConfig.minP,
                        repetitionPenalty = req.repetitionPenalty ?: defaultConfig.repetitionPenalty,
                        maxNewTokens = req.maxTokens,
                        threadNum = req.threadNum ?: defaultConfig.threadNum,
                        backendType = req.backendType ?: defaultConfig.backendType,
                    ),
                ),
            ).toList()
            val joined = chunks.joinToString("")
            ExecutionResult(
                text = joined,
                latencyMs = System.currentTimeMillis() - startedAt,
                truncated = chunks.isEmpty(),
            )
        }.getOrElse { e ->
            AppLogger.warn(
                LogTags.Llm,
                "local_qwen_execute_failed",
                "model" to resolvedModelName,
                "cause" to (e.message ?: e::class.simpleName.orEmpty()),
                "latencyMs" to (System.currentTimeMillis() - startedAt),
            )
            ExecutionResult(
                text = "",
                latencyMs = System.currentTimeMillis() - startedAt,
                truncated = true,
                errorMessage = e.message ?: e::class.simpleName.orEmpty(),
            )
        }
    }

    /**
     * 解析"后台任务当前应使用的本地模型名",与 SettingsScreen "本地模型"区块的 UI 状态源**保持一致**:
     * 1. 优先用 AppPreferences.modelName(主对话 Provider = LOCAL_QWEN 时用户选的)
     * 2. 否则(主对话是云端)扫 `filesDir/models/` 找本地下好的那个 catalog model
     * 3. 都没装回退 0.8B 默认值,worker 会落到 Result.failure 路径
     */
    private suspend fun resolveActiveLocalModelName(): String {
        val stored = appPreferences.modelName.first()
        val localOptions = DefaultLlmValues.modelOptions(LlmProvider.LOCAL_QWEN)
        if (stored in localOptions) return stored
        val installed = localQwenModelDownloader.findAnyInstalledModel()
        return installed ?: DefaultLlmValues.LOCAL_QWEN_MODEL
    }

    /**
     * 解析 `patternDetect` prompt 的 JSON 输出(plan §3.2 草图):
     * ```
     * [{ "headline": "...", "body": "...", "evidence_ids": [...], "confidence": 0.0~1.0 }]
     * ```
     * - 输出是 JSON 数组(可能夹在 markdown ```json ... ``` 块里)
     * - 解析失败 → 空 list
     * - 单个 element 缺字段 → 跳过
     */
    fun parsePatternDetectOutput(raw: String): List<InsightDraft> {
        val cleaned = raw
            .replace(Regex("```json\\s*"), "")
            .replace(Regex("```\\s*"), "")
            .trim()
        if (cleaned.isBlank()) return emptyList()

        val parsed = runCatching { json.parseToJsonElement(cleaned) }
            .onFailure {
                AppLogger.warn(
                    LogTags.LocalModel,
                    "insight_json_parse_failed",
                    "rawLength" to raw.length,
                    "cleanedLength" to cleaned.length,
                    "error" to (it.message ?: it::class.simpleName.orEmpty()),
                )
            }
            .getOrNull() ?: return emptyList()
        val array = parsed as? JsonArray ?: return emptyList()

        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val headline = obj["headline"]?.jsonPrimitive?.contentOrNull?.trim() ?: return@mapNotNull null
            if (headline.isEmpty()) return@mapNotNull null
            val body = obj["body"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
            val evidenceIds = (obj["evidence_ids"] as? JsonArray)
                ?.mapNotNull { (it.jsonPrimitive.contentOrNull) }
                ?: emptyList()
            val confidence = obj["confidence"]?.jsonPrimitive?.floatOrZero() ?: 0f
            val relevanceWindow = obj["relevanceWindow"]?.jsonPrimitive?.contentOrNull?.trim()
                ?: "近 7 天"
            InsightDraft(
                triggerType = "PATTERN_DETECT",
                category = obj["category"]?.jsonPrimitive?.contentOrNull?.trim() ?: "情绪",
                headline = headline,
                bodyMarkdown = body,
                relevanceWindow = relevanceWindow,
                confidence = confidence.coerceIn(0f, 1f),
                // 暂不区分 evidenceMessageIds / memoryIds / moodSnapshotIds — M3 单一 bucket
                evidenceMessageIds = evidenceIds,
                evidenceMemoryIds = emptyList(),
                evidenceMoodSnapshotIds = emptyList(),
            )
        }
    }

    private fun kotlinx.serialization.json.JsonPrimitive.floatOrZero(): Float =
        runCatching { float }.getOrDefault(0f)

    private companion object {
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}

/** 提供默认的 Pattern Detect 执行入口(plan §3.2) */
suspend fun LocalQwenExecutor.executePatternDetect(
    dataSummary: String,
    systemContext: String = InsightPrompts.patternDetect,
): LocalQwenExecutor.ExecutionResult = execute(
    LocalQwenExecutor.Request(
        systemPrompt = systemContext,
        userMessage = dataSummary,
    ),
)
