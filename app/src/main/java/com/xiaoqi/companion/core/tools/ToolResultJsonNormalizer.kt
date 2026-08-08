package com.xiaoqi.companion.core.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/** 把 Koog 包成 JSON string literal 的工具结果还原为原始 JSON。 */
internal fun normalizeToolResultJson(raw: String): String {
    val trimmed = raw.trim()
    val looselyWrapped = when {
        trimmed.startsWith("\"{") && trimmed.endsWith("}\"") -> trimmed.substring(1, trimmed.length - 1)
        trimmed.startsWith("\"[") && trimmed.endsWith("]\"") -> trimmed.substring(1, trimmed.length - 1)
        else -> null
    }
    if (looselyWrapped != null && runCatching { Json.parseToJsonElement(looselyWrapped) }.isSuccess) {
        return looselyWrapped
    }

    val element = runCatching { Json.parseToJsonElement(trimmed) }.getOrNull()
    val primitive = element as? JsonPrimitive ?: return raw
    if (!primitive.isString) return raw

    val content = primitive.content.trim()
    if (content.firstOrNull() !in setOf('{', '[')) return raw
    return if (runCatching { Json.parseToJsonElement(content) }.isSuccess) content else raw
}
