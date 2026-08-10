package com.xiaoqi.companion.feature.chat

import android.content.Context
import android.os.Build
import com.xiaoqi.companion.BuildConfig
import com.xiaoqi.companion.core.logging.AppLogger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class DebugDiagnosticArchive(
    val file: File,
    val includesAppLog: Boolean,
    val includesCrashLog: Boolean,
)

internal class DebugDiagnosticExporter(private val context: Context) {
    suspend fun export(): DebugDiagnosticArchive = withContext(Dispatchers.IO) {
        val provider = checkNotNull(AppLogger.fileProvider()) { "诊断日志尚未初始化" }
        val exportDir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
        pruneOldArchives(exportDir)

        val timestamp = SimpleDateFormat(FILE_DATE_FORMAT, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        val destination = File(exportDir, "aura-diagnostics-$timestamp.zip")
        DebugDiagnosticArchiveWriter().write(
            destination = destination,
            appLog = provider.logFile.takeIf(File::isFile),
            crashLog = provider.crashDumps().firstOrNull(),
            metadata = buildMetadata(),
        )
    }

    private fun buildMetadata(): Map<String, String> = linkedMapOf(
        "exportedAtUtc" to SimpleDateFormat(ISO_DATE_FORMAT, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date()),
        "applicationId" to context.packageName,
        "brand" to BuildConfig.BRAND_NAME,
        "versionName" to BuildConfig.VERSION_NAME,
        "versionCode" to BuildConfig.VERSION_CODE.toString(),
        "buildType" to BuildConfig.BUILD_TYPE,
        "manufacturer" to Build.MANUFACTURER,
        "model" to Build.MODEL,
        "androidSdk" to Build.VERSION.SDK_INT.toString(),
        "locale" to Locale.getDefault().toLanguageTag(),
        "timeZone" to TimeZone.getDefault().id,
    )

    private fun pruneOldArchives(exportDir: File) {
        exportDir.listFiles { file -> file.isFile && file.extension == "zip" }
            ?.sortedByDescending(File::lastModified)
            ?.drop(MAX_RETAINED_ARCHIVES - 1)
            ?.forEach(File::delete)
    }

    private companion object {
        const val EXPORT_DIR = "diagnostics"
        const val MAX_RETAINED_ARCHIVES = 3
        const val FILE_DATE_FORMAT = "yyyyMMdd-HHmmss'Z'"
        const val ISO_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'"
    }
}

internal class DebugDiagnosticArchiveWriter {
    fun write(
        destination: File,
        appLog: File?,
        crashLog: File?,
        metadata: Map<String, String>,
    ): DebugDiagnosticArchive {
        destination.parentFile?.mkdirs()
        ZipOutputStream(destination.outputStream().buffered()).use { zip ->
            zip.writeTextEntry("README.txt", README)
            zip.writeTextEntry("manifest.json", metadata.toJson())
            appLog?.takeIf(File::isFile)?.let {
                zip.writeTextEntry("logs/app.log", readBoundedAndSanitized(it))
            }
            crashLog?.takeIf(File::isFile)?.let {
                zip.writeTextEntry("crashes/latest.log", readBoundedAndSanitized(it))
            }
        }
        return DebugDiagnosticArchive(
            file = destination,
            includesAppLog = appLog?.isFile == true,
            includesCrashLog = crashLog?.isFile == true,
        )
    }

    private fun readBoundedAndSanitized(file: File): String {
        val text = file.bufferedReader().use { it.readText() }
        return DebugDiagnosticTextSanitizer.sanitize(text.takeLast(MAX_ENTRY_CHARS))
    }

    private fun ZipOutputStream.writeTextEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun Map<String, String>.toJson(): String = entries.joinToString(
        prefix = "{\n",
        postfix = "\n}\n",
        separator = ",\n",
    ) { (key, value) -> "  ${key.jsonQuoted()}: ${value.jsonQuoted()}" }

    private fun String.jsonQuoted(): String = buildString(length + 2) {
        append('"')
        this@jsonQuoted.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
        append('"')
    }

    private companion object {
        const val MAX_ENTRY_CHARS = 512 * 1024
        const val README = """Aura Debug 诊断包

此压缩包由 Debug APK 主动导出，用于定位功能、网络、LLM 与 MCP 工具问题。
它包含应用/设备版本摘要、脱敏运行日志，以及存在时的最近一次崩溃日志。
它不包含 API Key、完整聊天记录、记忆内容或图片。发送前仍建议自行检查文件内容。
"""
    }
}

internal object DebugDiagnosticTextSanitizer {
    private val jsonSecret = Regex(
        "(?i)(\\\"(?:api[_-]?key|authorization|token|secret|cookie)\\\"\\s*:\\s*\\\")[^\\\"]*(\\\")"
    )
    private val assignedSecret = Regex(
        "(?i)\\b(api[_-]?key|authorization|x-api-key|token|secret|cookie)\\s*[:=]\\s*(?:Bearer\\s+)?[^\\s,;}&]+"
    )
    private val bearerToken = Regex("(?i)\\bBearer\\s+[A-Za-z0-9._~+/-]{8,}")
    private val longEncodedValue = Regex("(?<![A-Za-z0-9+/])[A-Za-z0-9+/]{96,}={0,2}(?![A-Za-z0-9+/])")

    fun sanitize(text: String): String = text
        .replace(jsonSecret, "$1<redacted>$2")
        .replace(assignedSecret, "$1=<redacted>")
        .replace(bearerToken, "Bearer <redacted>")
        .replace(longEncodedValue, "<redacted:encoded-value>")
}
