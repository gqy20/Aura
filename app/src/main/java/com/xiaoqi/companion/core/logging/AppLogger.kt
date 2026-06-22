package com.xiaoqi.companion.core.logging

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import timber.log.Timber

object AppLogger {

    // 双重过滤第一道:release 模式下 priority 低于此阈值的调用在入口直接返回,
    // 避免执行 LogFieldSanitizer.sanitize + buildMessage 的无谓开销。
    // proguard 的 -assumenosideeffects 只能剥 Timber.d/v 方法名,匹配不到 log(priority,...) 重载,
    // 所以必须由 AppLogger 自己短路。SafeReleaseTree.isLoggable 仍保留作为最后防线。
    @Volatile
    private var minPriority: Int = Log.VERBOSE

    private var fileProvider: LogFileProvider? = null

    /**
     * 生产入口:种 logcat Tree + 持久化 ring buffer Tree + 安装 crash 捕获。
     * 由 [com.xiaoqi.companion.CompanionApplication.onCreate] 调用。
     */
    fun initialize(context: Context, isDebug: Boolean) {
        val provider = LogFileProvider(context.applicationContext)
        fileProvider = provider
        initialize(isDebug)
        // ring buffer 与 logcat tree 并行:logcat 给实时调试,文件给事后排查
        Timber.plant(FileRingBufferTree(provider))
        CrashLogger.install(provider)
        info(LogTags.App, "ring_buffer_planted")
    }

    /**
     * 测试入口 / 无文件依赖场景:只种 logcat Tree,不落盘、不捕获 crash。
     */
    fun initialize(isDebug: Boolean) {
        minPriority = if (isDebug) Log.VERBOSE else Log.WARN
        Timber.uprootAll()
        if (isDebug) {
            Timber.plant(SafeDebugTree())
            info(LogTags.App, "logger_initialized", "mode" to "debug")
        } else {
            Timber.plant(SafeReleaseTree())
            info(LogTags.App, "logger_initialized", "mode" to "release")
        }
    }

    /** 暴露给 Settings 的"导出诊断日志"使用;测试或无文件模式返回 null。 */
    fun fileProvider(): LogFileProvider? = fileProvider

    /** 短路阈值,用于测试验证 release 模式确实抬高到 WARN。 */
    @VisibleForTesting
    fun minPriority(): Int = minPriority

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
        if (priority < minPriority) return
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
