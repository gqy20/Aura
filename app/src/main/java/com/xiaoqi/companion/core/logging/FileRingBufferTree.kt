package com.xiaoqi.companion.core.logging

import android.util.Log
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 把日志追加到 [LogFileProvider.logFile] 的环形缓冲 Tree。
 *
 * 与 SafeReleaseTree/SafeDebugTree 并行种植:它们负责 logcat,本类负责持久化。
 * 所有 priority 都落盘 —— ring buffer 是事后排查"非崩溃类问题"(空响应、状态错乱)的唯一手段,
 * debug/info 同样有诊断价值。容量由 [LogFileProvider] 限制,不膨胀。
 *
 * 写入路径:synchronized append → 每 [TRIM_CHECK_INTERVAL] 条检查一次容量并 trim。
 * trim 重建文件较重,通过频率限制摊薄,单条 append 仍是廉价的。
 */
class FileRingBufferTree(
    private val provider: LogFileProvider,
) : Timber.Tree() {

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private var writeSinceTrim = 0

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val line = formatLine(priority, tag, message, t)
        append(line)
    }

    private fun formatLine(priority: Int, tag: String?, message: String, t: Throwable?): String {
        val ts = timeFormat.format(Date())
        val level = priorityChar(priority)
        val tagPart = tag ?: LogTags.App
        val stack = if (t != null) "\n" + Log.getStackTraceString(t) else ""
        return "[$ts][$level][$tagPart] $message$stack\n"
    }

    private fun priorityChar(priority: Int): String = when (priority) {
        Log.VERBOSE -> "V"
        Log.DEBUG -> "D"
        Log.INFO -> "I"
        Log.WARN -> "W"
        Log.ERROR -> "E"
        Log.ASSERT -> "A"
        else -> "?"
    }

    @Synchronized
    private fun append(line: String) {
        try {
            provider.logFile.appendText(line)
            writeSinceTrim++
            if (writeSinceTrim >= TRIM_CHECK_INTERVAL) {
                writeSinceTrim = 0
                provider.trimIfNeeded()
            }
        } catch (_: IOException) {
            // 落盘失败不能影响业务,也不能递归打日志(会无限递归)
        }
    }

    companion object {
        // 每写 64 条检查一次容量,避免每次 append 都 stat 文件
        private const val TRIM_CHECK_INTERVAL = 64
    }
}
