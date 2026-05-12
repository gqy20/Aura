package com.xiaoqi.companion.core.logging

import java.security.MessageDigest

object LogFieldSanitizer {
    private val sensitiveKeyParts = listOf(
        "apikey",
        "api_key",
        "authorization",
        "base64",
        "body",
        "content",
        "image",
        "input",
        "message",
        "prompt",
        "response",
        "secret",
        "text",
        "token",
        "url",
    )

    fun sanitize(fields: Map<String, Any?>): Map<String, Any?> =
        fields.mapValues { (key, value) ->
            if (isSafeMetricKey(key)) {
                sanitizeValue(value)
            } else if (isSensitiveKey(key)) {
                redact(value)
            } else {
                sanitizeValue(value)
            }
        }

    fun hash(value: String?): String =
        if (value.isNullOrBlank()) {
            "none"
        } else {
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray())
                .joinToString("") { "%02x".format(it) }
                .take(12)
        }

    private fun isSensitiveKey(key: String): Boolean {
        val normalized = key.lowercase()
        return sensitiveKeyParts.any { normalized.contains(it) }
    }

    private fun isSafeMetricKey(key: String): Boolean {
        val normalized = key.lowercase()
        return normalized.endsWith("count") ||
            normalized.endsWith("hash") ||
            normalized.endsWith("length") ||
            normalized.endsWith("ms") ||
            normalized.startsWith("has") ||
            normalized == "duration" ||
            normalized == "durationms" ||
            normalized == "inputtokens" ||
            normalized == "outputtokens" ||
            normalized == "statuscode"
    }

    private fun sanitizeValue(value: Any?): Any? =
        when (value) {
            null -> null
            is Throwable -> value::class.simpleName ?: "Throwable"
            is CharSequence -> value.toString().take(MAX_FIELD_LENGTH)
            is Number, is Boolean -> value
            is Enum<*> -> value.name
            is Iterable<*> -> value.map { sanitizeValue(it) }.take(MAX_COLLECTION_SIZE)
            is Array<*> -> value.map { sanitizeValue(it) }.take(MAX_COLLECTION_SIZE)
            is Map<*, *> -> value.entries.take(MAX_COLLECTION_SIZE).associate { (key, itemValue) ->
                key.toString() to sanitizeValue(itemValue)
            }
            else -> value.toString().take(MAX_FIELD_LENGTH)
        }

    private fun redact(value: Any?): String =
        when (value) {
            null -> "redacted:null"
            is CharSequence -> "redacted:length=${value.length}"
            is Collection<*> -> "redacted:size=${value.size}"
            is Map<*, *> -> "redacted:size=${value.size}"
            else -> "redacted:${value::class.simpleName ?: "value"}"
        }

    private const val MAX_FIELD_LENGTH = 120
    private const val MAX_COLLECTION_SIZE = 12
}
