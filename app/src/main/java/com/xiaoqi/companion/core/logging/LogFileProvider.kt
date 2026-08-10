package com.xiaoqi.companion.core.logging

import android.content.Context
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 诊断日志的文件管理:固定容量环形缓冲 + crash dump 快照。
 *
 * - [logFile]:日常 ring buffer,append 写,超过 [maxBytes] 时保留尾部重建。
 * - crash 发生时由 [CrashLogger] 复制成独立 dump 文件,避免被后续日志滚出。
 * - 所有日志正文已由 [LogFieldSanitizer] 脱敏,文件内容不含原始 PII。
 *
 * 文件位于 app 私有目录(`filesDir/diagnostics/`),外部无法直接读取;
 * Debug 用户通过 Settings 的诊断包入口主动授权分享;release 不声明分享用 FileProvider。
 */
class LogFileProvider(
    context: Context,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) {
    private val dir: File = File(context.filesDir, DIR_NAME).apply { mkdirs() }

    val logFile: File = File(dir, LOG_FILE_NAME)

    val crashDir: File = File(dir, CRASH_DIR_NAME).apply { mkdirs() }

    /** 超过容量时保留尾部 [KEEP_FRACTION] 比例重建,丢弃最旧内容。 */
    fun trimIfNeeded() {
        try {
            if (!logFile.exists() || logFile.length() <= maxBytes) return
            val keepSize = (maxBytes * KEEP_FRACTION).toLong().coerceAtLeast(1)
            val tail = readTail(logFile, keepSize) ?: return
            logFile.writeText(tail)
        } catch (_: IOException) {
            // trim 失败不应影响后续日志写入,静默跳过
        }
    }

    /** 读取文件尾部大约 [targetBytes] 字节,按行对齐(不截断半行)。 */
    private fun readTail(file: File, targetBytes: Long): String? {
        val bytes = file.readBytes()
        if (bytes.size <= targetBytes) return String(bytes)
        var start = (bytes.size - targetBytes).toInt().coerceAtLeast(0)
        while (start < bytes.size && bytes[start] != '\n'.code.toByte()) start++
        if (start < bytes.size) start++ // 跳过该换行符
        return String(bytes, start, bytes.size - start)
    }

    /** 列出所有 crash dump 文件,按修改时间倒序(最新在前)。 */
    fun crashDumps(): List<File> =
        crashDir.listFiles { f -> f.isFile && f.name.endsWith(CRASH_FILE_SUFFIX) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    /** 为一次 crash 生成独立的 dump 文件:复制当前 ring buffer 并附 stack trace。 */
    @Throws(IOException::class)
    fun snapshotForCrash(timestamp: Long, stackTrace: String): File {
        val date = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date(timestamp))
        val dump = File(crashDir, "crash-$date$CRASH_FILE_SUFFIX")
        dump.outputStream().use { out ->
            if (logFile.exists()) logFile.inputStream().use { it.copyTo(out) }
            out.write("\n\n===== CRASH =====\n".toByteArray())
            out.write(stackTrace.toByteArray())
        }
        return dump
    }

    /** 清空日常 ring buffer(crash dump 保留)。导出后可选调用。 */
    fun clearLog() {
        try {
            logFile.writeText("")
        } catch (_: IOException) {
        }
    }

    companion object {
        private const val DIR_NAME = "diagnostics"
        private const val LOG_FILE_NAME = "app.log"
        private const val CRASH_DIR_NAME = "crashes"
        private const val CRASH_FILE_SUFFIX = ".log"
        private const val KEEP_FRACTION = 0.5
        const val DEFAULT_MAX_BYTES: Long = 256 * 1024L // 256 KB
    }
}
