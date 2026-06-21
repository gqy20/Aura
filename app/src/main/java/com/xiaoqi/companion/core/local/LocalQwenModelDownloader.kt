package com.xiaoqi.companion.core.local

import android.content.Context
import android.os.SystemClock
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request

interface LocalQwenModelDownloader {
    fun observeStatus(modelName: String): Flow<LocalQwenModelDownloadState>
    fun download(modelName: String, force: Boolean = false): Flow<LocalQwenModelDownloadState>

    /**
     * 扫 `filesDir/models/` 找到第一个 LOCAL_QWEN catalog model 文件全齐的 modelName;
     * 都未装返回 null。UI 状态源 / 后台任务 fallback 共用。
     */
    fun findAnyInstalledModel(): String?
}

data class LocalQwenModelDownloadState(
    val modelName: String,
    val isInstalled: Boolean,
    val isDownloading: Boolean = false,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long? = null,
    val message: String? = null,
    val error: String? = null,
)

@Singleton
class ModelScopeLocalQwenModelDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
) : LocalQwenModelDownloader {

    private val downloadMutex = Mutex()

    override fun observeStatus(modelName: String): Flow<LocalQwenModelDownloadState> = flow {
        emit(status(modelName))
    }.flowOn(Dispatchers.IO)

    override fun findAnyInstalledModel(): String? {
        val modelsDir = File(context.filesDir, "models")
        return LocalQwenModelCatalog.models.firstOrNull { spec ->
            val dir = File(modelsDir, spec.modelName)
            LocalQwenModelCatalog.requiredFiles.all {
                val f = File(dir, it)
                f.isFile && f.length() > 0L
            }
        }?.modelName
    }

    override fun download(modelName: String, force: Boolean): Flow<LocalQwenModelDownloadState> = flow {
        downloadMutex.withLock {
            val spec = LocalQwenModelCatalog.requireSpec(modelName)
            val modelDir = File(context.filesDir, "models/${spec.modelName}")
            val partialDir = File(context.filesDir, "models/.${spec.modelName}.partial")
            AppLogger.info(
                LogTags.LocalModel,
                "local_model_download_started",
                "model" to modelName,
                "targetDir" to modelDir.absolutePath,
                "requiredFileCount" to LocalQwenModelCatalog.requiredFiles.size,
                "force" to force,
            )
            if (force) {
                modelDir.deleteRecursively()
                partialDir.deleteRecursively()
            }
            if (!partialDir.exists() && !partialDir.mkdirs() && !partialDir.isDirectory) {
                throw IOException("Cannot create model download directory: ${partialDir.absolutePath}")
            }

            emit(status(modelName).copy(isDownloading = true, message = "开始下载"))

            var completedFilesBytes = 0L
            var lastEmitTime = 0L
            var lastEmitProgress = -1f
            val fileCount = LocalQwenModelCatalog.requiredFiles.size

            LocalQwenModelCatalog.requiredFiles.forEachIndexed { index, fileName ->
                val target = File(partialDir, fileName)
                val existing = File(modelDir, fileName)

                // 复用 modelDir 里已完整的文件
                if (existing.isFile && existing.length() > 0L) {
                    AppLogger.debug(
                        LogTags.LocalModel,
                        "local_model_download_reusing_file",
                        "model" to modelName,
                        "file" to fileName,
                        "bytes" to existing.length(),
                    )
                    existing.copyTo(target, overwrite = true)
                    completedFilesBytes += existing.length()
                    emit(
                        LocalQwenModelDownloadState(
                            modelName = modelName,
                            isInstalled = false,
                            isDownloading = true,
                            progress = (index + 1).toFloat() / fileCount.toFloat(),
                            downloadedBytes = completedFilesBytes,
                            totalBytes = null,
                            message = "Reusing $fileName",
                        )
                    )
                    return@forEachIndexed
                }

                val url = spec.downloadUrl(fileName)
                val startByte = if (target.isFile && target.length() > 0L) target.length() else 0L
                AppLogger.debug(
                    LogTags.LocalModel,
                    "local_model_download_file_started",
                    "model" to modelName,
                    "file" to fileName,
                    "resumeByte" to startByte,
                )

                var retryFromScratch = false
                val request = Request.Builder().url(url).apply {
                    header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36")
                    if (startByte > 0L) header("Range", "bytes=$startByte-")
                }.build()

                httpClient.newCall(request).execute().use { response ->
                    // 416 = partial file 已完整
                    if (response.code == 416) {
                        AppLogger.info(
                            LogTags.LocalModel,
                            "local_model_download_file_already_complete",
                            "model" to modelName,
                            "file" to fileName,
                            "bytes" to target.length(),
                        )
                        completedFilesBytes += target.length()
                        return@use
                    }

                    // 404 + partial file: CDN 不支持 Range，删 partial 重试
                    if (response.code == 404 && startByte > 0L) {
                        AppLogger.info(
                            LogTags.LocalModel,
                            "local_model_download_file_range_fallback",
                            "model" to modelName,
                            "file" to fileName,
                            "resumeByte" to startByte,
                            "reason" to "CDN returned 404 for Range request",
                        )
                        target.delete()
                        retryFromScratch = true
                        return@use
                    }

                    if (!response.isSuccessful) {
                        AppLogger.warn(
                            LogTags.LocalModel,
                            "local_model_download_file_failed",
                            "model" to modelName,
                            "file" to fileName,
                            "code" to response.code,
                        )
                        throw IOException("ModelScope download failed (${response.code}): $fileName")
                    }

                    val body = response.body ?: throw IOException("Empty response body: $fileName")
                    // 206 Partial Content = 服务器支持 Range，追加写入；200 = 从头开始
                    val isResume = response.code == 206
                    val append = isResume && startByte > 0L
                    val fileTotalBytes = if (isResume) {
                        body.contentLength().takeIf { it > 0L }?.let { startByte + it }
                    } else {
                        body.contentLength().takeIf { it > 0L }
                    }

                    if (!isResume && startByte > 0L) {
                        target.delete()
                    }

                    var currentFileBytes = if (append) startByte else 0L
                    FileOutputStream(target, append).use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                currentFileBytes += read
                                throttledEmit(
                                    modelName = modelName,
                                    fileIndex = index,
                                    fileCount = fileCount,
                                    completedFilesBytes = completedFilesBytes,
                                    currentFileBytes = currentFileBytes,
                                    fileTotalBytes = fileTotalBytes,
                                    fileName = fileName,
                                    lastEmitTime = lastEmitTime,
                                    lastEmitProgress = lastEmitProgress,
                                )?.let { (time, progress) ->
                                    lastEmitTime = time
                                    lastEmitProgress = progress
                                }
                            }
                        }
                    }
                    completedFilesBytes += target.length()
                    AppLogger.info(
                        LogTags.LocalModel,
                        "local_model_download_file_completed",
                        "model" to modelName,
                        "file" to fileName,
                        "bytes" to target.length(),
                    )
                }

                // CDN 返回 404 且有 partial 文件时，删 partial 后从头重试
                if (retryFromScratch) {
                    val retryRequest = Request.Builder().url(url).apply {
                        header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36")
                    }.build()
                    httpClient.newCall(retryRequest).execute().use { retryResponse ->
                        if (!retryResponse.isSuccessful) {
                            throw IOException("ModelScope download failed (${retryResponse.code}) on retry: $fileName")
                        }
                        val retryBody = retryResponse.body ?: throw IOException("Empty response body on retry: $fileName")
                        FileOutputStream(target, false).use { output ->
                            retryBody.byteStream().use { input ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read == -1) break
                                    output.write(buffer, 0, read)
                                }
                            }
                        }
                        completedFilesBytes += target.length()
                        AppLogger.info(
                            LogTags.LocalModel,
                            "local_model_download_file_completed",
                            "model" to modelName,
                            "file" to fileName,
                            "bytes" to target.length(),
                            "retried" to true,
                        )
                    }
                }
            }

            validateInstall(partialDir)
            modelDir.deleteRecursively()
            if (!partialDir.renameTo(modelDir)) {
                partialDir.copyRecursively(modelDir, overwrite = true)
                partialDir.deleteRecursively()
            }
            AppLogger.info(
                LogTags.LocalModel,
                "local_model_download_completed",
                "model" to modelName,
                "targetDir" to modelDir.absolutePath,
            )
            emit(status(modelName).copy(message = "下载完成"))
        }
    }.flowOn(Dispatchers.IO)

    private fun status(modelName: String): LocalQwenModelDownloadState {
        val modelDir = File(context.filesDir, "models/$modelName")
        val installed = LocalQwenModelCatalog.requiredFiles.all {
            val f = File(modelDir, it)
            f.isFile && f.length() > 0L
        }
        return LocalQwenModelDownloadState(
            modelName = modelName,
            isInstalled = installed,
            progress = if (installed) 1f else 0f,
            message = null,
        )
    }

    private fun validateInstall(modelDir: File) {
        val invalid = LocalQwenModelCatalog.requiredFiles.filter { name ->
            val f = File(modelDir, name)
            !f.isFile || f.length() == 0L
        }
        if (invalid.isNotEmpty()) {
            throw IOException("Local Qwen model download incomplete: ${invalid.joinToString()}")
        }
    }

    /**
     * 节流 emit：每 300ms 或进度变化 ≥ 0.5% 才 emit 一次，
     * 返回 (emitTime, progress) 供调用方更新上次 emit 状态；null = 未 emit。
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<LocalQwenModelDownloadState>.throttledEmit(
        modelName: String,
        fileIndex: Int,
        fileCount: Int,
        completedFilesBytes: Long,
        currentFileBytes: Long,
        fileTotalBytes: Long?,
        fileName: String,
        lastEmitTime: Long,
        lastEmitProgress: Float,
    ): Pair<Long, Float>? {
        val now = SystemClock.elapsedRealtime()
        val totalDownloaded = completedFilesBytes + currentFileBytes
        val progress = if (fileTotalBytes != null && fileTotalBytes > 0L) {
            (completedFilesBytes + currentFileBytes).toFloat() /
                (completedFilesBytes + fileTotalBytes).toFloat()
        } else {
            (fileIndex.toFloat() + currentFileBytes / 100_000_000f) / fileCount.toFloat()
        }.coerceIn(0f, 0.99f)

        val timePassed = now - lastEmitTime >= EMIT_INTERVAL_MS
        val progressChanged = lastEmitProgress < 0f ||
            kotlin.math.abs(progress - lastEmitProgress) >= EMIT_PROGRESS_DELTA

        if (timePassed || progressChanged) {
            emit(
                LocalQwenModelDownloadState(
                    modelName = modelName,
                    isInstalled = false,
                    isDownloading = true,
                    progress = progress,
                    downloadedBytes = totalDownloaded,
                    totalBytes = if (fileTotalBytes != null) completedFilesBytes + fileTotalBytes else null,
                    message = "Downloading $fileName",
                )
            )
            return now to progress
        }
        return null
    }

    private companion object {
        const val EMIT_INTERVAL_MS = 300L
        const val EMIT_PROGRESS_DELTA = 0.005f
    }
}
