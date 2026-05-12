package com.xiaoqi.companion.core.logging

import android.util.Log
import timber.log.Timber

object AppLogger {
    fun initialize(isDebug: Boolean) {
        Timber.uprootAll()
        if (isDebug) {
            Timber.plant(SafeDebugTree())
            info(LogTags.App, "logger_initialized", "mode" to "debug")
        } else {
            Timber.plant(SafeReleaseTree())
            info(LogTags.App, "logger_initialized", "mode" to "release")
        }
    }

    fun verbose(tag: String, event: String, vararg fields: Pair<String, Any?>) {
        log(Log.VERBOSE, tag, LogEventType.Diagnostic, event, null, fields.toMap())
    }

    fun debug(tag: String, event: String, vararg fields: Pair<String, Any?>) {
        log(Log.DEBUG, tag, LogEventType.Diagnostic, event, null, fields.toMap())
    }

    fun info(tag: String, event: String, vararg fields: Pair<String, Any?>) {
        log(Log.INFO, tag, LogEventType.Audit, event, null, fields.toMap())
    }

    fun warn(tag: String, event: String, vararg fields: Pair<String, Any?>) {
        log(Log.WARN, tag, LogEventType.Failure, event, null, fields.toMap())
    }

    fun warn(tag: String, throwable: Throwable, event: String, vararg fields: Pair<String, Any?>) {
        log(Log.WARN, tag, LogEventType.Failure, event, throwable, fields.toMap())
    }

    fun error(tag: String, throwable: Throwable, event: String, vararg fields: Pair<String, Any?>) {
        log(Log.ERROR, tag, LogEventType.Failure, event, throwable, fields.toMap())
    }

    private fun log(
        priority: Int,
        tag: String,
        type: LogEventType,
        event: String,
        throwable: Throwable?,
        fields: Map<String, Any?>,
    ) {
        val message = buildMessage(type, event, LogFieldSanitizer.sanitize(fields))
        Timber.tag(tag).log(priority, throwable, message)
    }

    private fun buildMessage(type: LogEventType, event: String, fields: Map<String, Any?>): String {
        val suffix = fields.entries
            .filter { it.value != null }
            .joinToString(separator = " ") { (key, value) -> "$key=$value" }
        return if (suffix.isBlank()) {
            "type=${type.name} event=$event"
        } else {
            "type=${type.name} event=$event $suffix"
        }
    }
}
