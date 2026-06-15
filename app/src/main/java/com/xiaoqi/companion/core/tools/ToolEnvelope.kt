package com.xiaoqi.companion.core.tools

import com.xiaoqi.companion.data.db.converter.MemoryType
import com.xiaoqi.companion.data.db.converter.SummaryType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * 工具执行结果在 prompt 里给 LLM 看的统一信封协议。
 *
 * ## 协议形状
 * - 成功:`{"status":"ok", "data":{...payload...}}`
 * - 失败:`{"status":"error", "reason":"<machine_code>", "hint":"<人类可读>", "details":{...}}`
 *
 * ## LLM 侧
 * - 读 `status` 区分成功/失败
 * - 失败时读 `reason` 决定下一步(参考 `docs/prompts/SystemPersona.yml` 中的协议说明)
 * - 失败时读 `hint` 知道怎么向用户表达
 *
 * ## 实现侧
 * - Tool 内部返回 [ToolEnvelope.Ok] 或 [ToolEnvelope.Error],**调用 [encode] 序列化**
 * - 解析路径用 [parseOrNull] —— 失败/空/null 都安全,不抛
 *
 * ## 与 Koog 的关系
 * - LLM 看到的文本(content 字段)就是 [encode] 的产物
 * - Koog 的 `Message.Tool.Result.isError` 通道由 `KoogResultKindPatcher.withErrorResultKind`
 *   单独维护 —— 信封与 Koog 标志位是两条独立通道,协同工作
 */
sealed class ToolEnvelope {
    abstract val status: String

    data class Ok(val data: JsonObject) : ToolEnvelope() {
        override val status: String get() = "ok"
    }

    data class Error(
        val reason: String,
        val hint: String,
        val details: Map<String, String> = emptyMap(),
    ) : ToolEnvelope() {
        override val status: String get() = "error"
    }
}

// --- 实际序列化的载体类(避开 sealed 多态的 discriminator 冲突问题)---

@Serializable
internal data class OkEnvelope(
    val status: String = "ok",
    val data: JsonObject,
)

@Serializable
internal data class ErrorEnvelope(
    val status: String = "error",
    val reason: String,
    val hint: String,
    val details: Map<String, String> = emptyMap(),
)

private val envelopeJson = Json {
    encodeDefaults = true
    explicitNulls = false
    prettyPrint = false
    ignoreUnknownKeys = true
}

/**
 * 工具结果的工厂集合。
 *
 * 所有工厂返回 [ToolEnvelope.Error] 子类型,集中在这一处的原因:
 * - `reason` 用机器可读码,LLM 用 [SystemPersona.yml] 里的协议解释
 * - `hint` 是给 LLM 翻译给用户的中文表达,**集中在这一处方便改文案**
 */
object ToolEnvelopeFactory {

    fun ok(payload: JsonObject): ToolEnvelope.Ok = ToolEnvelope.Ok(data = payload)

    fun err(reason: String, hint: String, details: Map<String, String> = emptyMap()): ToolEnvelope.Error =
        ToolEnvelope.Error(reason = reason, hint = hint, details = details)

    /** 通用:枚举类校验失败。kind 例:`memory_type` / `summary_type` / `message_role`。 */
    fun invalidEnum(provided: String, allowed: List<String>, kind: String): ToolEnvelope.Error {
        val enumName = allowed.joinToString(",")
        return ToolEnvelope.Error(
            reason = "invalid_${kind}",
            hint = "$kind 必须是 $enumName 之一,你传了 '$provided'。请换成允许的值。",
            details = mapOf("provided" to provided, "allowed" to enumName, "kind" to kind),
        )
    }

    fun invalidMemoryType(provided: String): ToolEnvelope.Error =
        invalidEnum(provided, MemoryType.entries.map { it.name }, kind = "memory_type")

    fun invalidSummaryType(provided: String): ToolEnvelope.Error =
        invalidEnum(provided, SummaryType.entries.map { it.name }, kind = "summary_type")

    fun invalidMessageRole(provided: String): ToolEnvelope.Error =
        invalidEnum(provided, listOf("USER", "ASSISTANT", "SYSTEM"), kind = "message_role")

    /** 子类:工具被用户在设置中关掉。`reason` 用机器码,`hint` 是给 LLM 的话术。 */
    fun disabled(reason: String, hint: String): ToolEnvelope.Error =
        ToolEnvelope.Error(reason = reason, hint = hint, details = emptyMap())

    /** 子类:权限缺失(运行时权限 / 精确闹钟 / 通知 / 位置 / 读取健康 等)。 */
    fun permissionMissing(permission: String, hint: String): ToolEnvelope.Error =
        ToolEnvelope.Error(
            reason = "permission_missing",
            hint = hint,
            details = mapOf("permission" to permission),
        )

    /** 子类:全文索引故障 —— LLM 应建议换关键词或换工具。 */
    fun ftsFailure(cause: String): ToolEnvelope.Error =
        ToolEnvelope.Error(
            reason = "fts_index_failure",
            hint = "底层全文检索失败: $cause。请把搜索请求简化为 1-2 个关键词,或换用 search_memory 试试。",
            details = mapOf("cause" to cause),
        )
}

/** 把 [ToolEnvelope] 序列化成 LLM 看到的字符串。 */
fun encode(envelope: ToolEnvelope): String = when (envelope) {
    is ToolEnvelope.Ok -> envelopeJson.encodeToString(
        OkEnvelope.serializer(),
        OkEnvelope(data = envelope.data),
    )
    is ToolEnvelope.Error -> envelopeJson.encodeToString(
        ErrorEnvelope.serializer(),
        ErrorEnvelope(reason = envelope.reason, hint = envelope.hint, details = envelope.details),
    )
}

/**
 * 解析 LLM 看到的字符串为 [ToolEnvelope];解析失败/空字符串/null 一律返回 null,**绝不抛**。
 *
 * UI 渲染层、Recorder 审计层都走这个 —— 信封之外的旧文本(自由 JSON)走 `ToolCallResultParser`
 * 兜底,但 envelope 解析自身不抛。
 */
fun parseOrNull(raw: String?): ToolEnvelope? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        // 先尝试 Ok 形态(默认 status="ok",所以原 data 字段必填)
        runCatching { envelopeJson.decodeFromString(OkEnvelope.serializer(), raw) }
            .getOrNull()
            ?.let { ToolEnvelope.Ok(data = it.data) }
        // 再尝试 Error 形态
            ?: runCatching { envelopeJson.decodeFromString(ErrorEnvelope.serializer(), raw) }
                .getOrNull()
                ?.let { ToolEnvelope.Error(reason = it.reason, hint = it.hint, details = it.details) }
    }.getOrNull()
}

/**
 * 快速判断一段文本是否是 envelope 失败格式(给 `KoogResultKindPatcher` 用)。
 * 不做完整解析,只看顶层 status。
 */
fun isError(raw: String?): Boolean {
    if (raw.isNullOrBlank()) return false
    val obj = runCatching { envelopeJson.parseToJsonElement(raw) }.getOrNull() as? JsonObject ?: return false
    val status = obj["status"] as? JsonPrimitive ?: return false
    return status.content == "error"
}

/** 从一段可能是 envelope 错误的文本里取 `reason`;取不到返 null。 */
fun parseErrorReason(raw: String?): String? =
    (parseOrNull(raw) as? ToolEnvelope.Error)?.reason

/** 从一段可能是 envelope 错误的文本里取 `hint`;取不到返 null。 */
fun parseErrorHint(raw: String?): String? =
    (parseOrNull(raw) as? ToolEnvelope.Error)?.hint

/** 给 `buildJsonObject { ... }` 的快捷 helper —— 已有数据想嵌进 Ok envelope.data 时用。 */
fun jsonObjectOf(vararg pairs: Pair<String, Any?>): JsonObject = buildJsonObject {
    pairs.forEach { (k, v) ->
        when (v) {
            null -> put(k, kotlinx.serialization.json.JsonNull)
            is JsonObject -> put(k, v)
            is JsonPrimitive -> put(k, v)
            else -> put(k, JsonPrimitive(v.toString()))
        }
    }
}
