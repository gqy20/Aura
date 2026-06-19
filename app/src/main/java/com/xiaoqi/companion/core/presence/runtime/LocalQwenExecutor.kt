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
     * 解析 `patternDetect` prompt 的 JSON 输出 — 3 层容错:
     *
     * **Layer 1: 标准 JSON** — `kotlinx.serialization` 直接解析,处理 markdown code fence。
     * **Layer 2: 归一化修复** — 小模型(0.8B/2B)常输出单引号/中文逗号/尾逗号/trailing text,
     *   `tryNormalizeJson` 尝试修成合法 JSON 再解析。
     * **Layer 3: 正则提取** — 完全不是 JSON 时,用正则从自由文本中抠出 headline/body/confidence。
     *
     * 任何一层成功都返回 [InsightDraft] 列表;全部失败 → 空 list + warn 日志。
     */
    fun parsePatternDetectOutput(raw: String): List<InsightDraft> {
        val cleaned = raw
            .replace(Regex("```json\\s*"), "")
            .replace(Regex("```\\s*"), "")
            .trim()
        if (cleaned.isBlank()) return emptyList()

        // Layer 1: 标准 JSON 解析
        val layer1 = tryParseAsJsonArray(cleaned)
        if (layer1.isNotEmpty()) return layer1

        // Layer 2: 归一化后重试
        val normalized = tryNormalizeJson(cleaned)
        if (normalized != null) {
            val layer2 = tryParseAsJsonArray(normalized)
            if (layer2.isNotEmpty()) return layer2
        }

        // Layer 3: 正则兜底(Android ICU 正则兼容性有限,try-catch 保护)
        val layer3 = runCatching { regexExtractInsights(cleaned) }.getOrElse { e ->
            AppLogger.warn(LogTags.LocalModel, "insight_regex_layer_crashed",
                "cause" to (e.message ?: e::class.simpleName.orEmpty()))
            emptyList()
        }
        if (layer3.isNotEmpty()) return layer3

        AppLogger.warn(
            LogTags.LocalModel,
            "insight_all_parse_layers_failed",
            "rawLength" to raw.length,
            "rawPreview" to raw.take(300),
        )
        return emptyList()
    }

    /** Layer 1/2 共用: 把字符串当 JSON 数组解析,逐条转 InsightDraft */
    private fun tryParseAsJsonArray(text: String): List<InsightDraft> {
        val parsed = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return emptyList()
        val array = parsed as? JsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val headline = obj["headline"]?.jsonPrimitive?.contentOrNull?.trim() ?: return@mapNotNull null
            if (headline.isEmpty()) return@mapNotNull null
            val body = obj["body"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
            val evidenceIds = (obj["evidence_ids"] as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
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
                evidenceMessageIds = evidenceIds,
                evidenceMemoryIds = emptyList(),
                evidenceMoodSnapshotIds = emptyList(),
            )
        }
    }

    /**
     * Layer 2: 尝试修复小模型常见 JSON 格式问题:
     * - 单引号 → 双引号
     * - 中文逗号/句号/分号 → 英文逗号(仅在疑似 JSON 结构内)
     * - 尾逗号(`},]` / `},\n]`) → 去掉
     * - 前后非 JSON 文字截断(模型常在 JSON 后追加解释文字)
     * - 未加方括号的裸对象 `[...]` 包裹
     *
     * 返回 null 表示无法归一化(原样太离谱)。
     */
    private fun tryNormalizeJson(raw: String): String? {
        var s = raw.trim()

        // 截断: 找到第一个 `[` 和最后一个 `]`,取中间内容
        val firstBracket = s.indexOf('[')
        val lastBracket = s.lastIndexOf(']')
        if (firstBracket >= 0 && lastBracket > firstBracket) {
            s = s.substring(firstBracket, lastBracket + 1)
        } else if (firstBracket < 0 && lastBracket < 0) {
            // 连方括号都没有,尝试包裹裸对象
            if (s.contains("{")) {
                val firstBrace = s.indexOf('{')
                val lastBrace = s.lastIndexOf('}')
                if (firstBrace >= 0 && lastBrace > firstBrace) {
                    s = "[${s.substring(firstBrace, lastBrace + 1)}]"
                } else {
                    return null
                }
            } else {
                return null
            }
        }

        // 单引号 → 双引号(简单替换,不处理内嵌单引号 edge case)
        s = s.replace("'", "\"")

        // 中文标点 → 英文(仅替换值内的,键通常已是英文)
        s = s.replace("，", ",")
            .replace("：", ":")
            .replace("；", ";")

        // 尾逗号: `},]` 或 `},\s*]` 或数组末尾 `,]`
        s = s.replace(Regex(",\\s*([}\\]])"), "$1")

        // trailing text after final `]`
        val endBracket = s.lastIndexOf(']')
        if (endBracket >= 0 && endBracket < s.length - 1) {
            s = s.substring(0, endBracket + 1)
        }

        return s.ifBlank { null }
    }

    /**
     * Layer 3: 纯字符串提取 — Android ICU 正则兼容性极差,全部用 indexOf/substring 实现。
     *
     * 扫描文本中所有 `"headline"` / `"body"` / `"confidence"` key-value 对,
     * 每组凑成一条 InsightDraft。这是最后的兜底,质量最低但不会崩溃。
     */
    @Suppress("TooGenericExceptionCaught")
    private fun regexExtractInsights(text: String): List<InsightDraft> {
        val results = mutableListOf<InsightDraft>()
        val lines = text.lines()

        var currentHeadline: String? = null
        var currentBody = ""
        var currentConfidence = 0.5f

        fun flushDraft() {
            val hl = currentHeadline?.trim()?.takeIf { it.isNotBlank() } ?: return
            results.add(InsightDraft(
                triggerType = "PATTERN_DETECT",
                category = "情绪",
                headline = hl,
                bodyMarkdown = currentBody.trim(),
                relevanceWindow = "近 7 天",
                confidence = currentConfidence.coerceIn(0f, 1f),
                evidenceMessageIds = emptyList(),
                evidenceMemoryIds = emptyList(),
                evidenceMoodSnapshotIds = emptyList(),
            ))
            currentHeadline = null
            currentBody = ""
            currentConfidence = 0.5f
        }

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            // 尝试提取 key-value: "key": value 或 key: value（带引号优先）
            val hv = extractQuotedValue(line, "headline")
                ?: extractAfterColon(line, listOf("headline"))
            if (hv != null) { if (currentHeadline != null) flushDraft(); currentHeadline = hv; continue }
            val bv = extractQuotedValue(line, "body")
                ?: extractAfterColon(line, listOf("body"))
            if (bv != null) { currentBody = bv; continue }
            val cv = extractFloatValue(line, "confidence")
            if (cv != null) { currentConfidence = cv; continue }

            // 中文 key 别名
            val titleVal = extractAfterColon(line, listOf("标题", "title"))
            if (titleVal != null) { if (currentHeadline != null) flushDraft(); currentHeadline = titleVal; continue }
            val descVal = extractAfterColon(line, listOf("内容", "描述", "content"))
            if (descVal != null) { currentBody = descVal; continue }
        }

        // 最后一条还没 flush
        flushDraft()
        return results
    }

    /** 从一行中提取 "key": "quoted_value" 形式的值 */
    private fun extractQuotedValue(line: String, key: String): String? {
        val patterns = listOf(
            "\"$key\"\\s*:\\s*\"",
            "'$key'\\s*:\\s*'",
            "$key\\s*:\\s*\"",
        )
        for (p in patterns) {
            val idx = line.indexOf(p)
            if (idx < 0) continue
            val start = idx + p.length
            val endQuote = line.indexOf('"', start)
            if (endQuote > start) return line.substring(start, endQuote)
            val endSingle = line.indexOf('\'', start)
            if (endSingle > start) return line.substring(start, endSingle)
            // 无闭合引号 → 取到行尾或逗号
            val endComma = line.indexOf(',', start)
            if (endComma > start) return line.substring(start, endComma).trim()
            return line.substring(start).trim().takeIf { it.isNotEmpty() }
        }
        return null
    }

    /** 从一行中提取 key: float 值 */
    private fun extractFloatValue(line: String, key: String): Float? {
        val patterns = listOf("\"$key'\\s*:", "'$key'\\s*:", "$key\\s*:")
        for (p in patterns) {
            val idx = line.indexOf(p)
            if (idx < 0) continue
            val after = line.substring(idx + p.length).trim()
            val numStr = after.split(',', '}', ' ', '\t').firstOrNull()?.trim() ?: continue
            return numStr.toFloatOrNull()
        }
        return null
    }

    /** 从一行中提取中文/英文 key 后面的非引号值（到行尾或逗号） */
    private fun extractAfterColon(line: String, keys: List<String>): String? {
        for (key in keys) {
            val colonIdx = line.indexOf(key)
            if (colonIdx < 0) continue
            val afterKey = colonIdx + key.length
            // 找冒号（全角或半角）
            val colon = line.indexOfAny(charArrayOf(':', '：'), afterKey)
            if (colon < 0) continue
            val valueStart = colon + 1
            val trimmed = line.substring(valueStart).trim()
            // 去掉可能的引号前缀
            val unquoted = trimmed.removePrefix("\"").removePrefix("'").removePrefix("：").removePrefix(":")
            val end = unquoted.indexOfAny(charArrayOf(',', '\n', '"', '\''))
            val result = if (end > 0) unquoted.substring(0, end) else unquoted
            return result.trim().takeIf { it.isNotEmpty() }
        }
        return null
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
