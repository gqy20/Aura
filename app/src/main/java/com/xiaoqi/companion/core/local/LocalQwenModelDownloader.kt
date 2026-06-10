package com.xiaoqi.companion.core.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request

interface LocalQwenModelDownloader {
    fun observeStatus(modelName: String): Flow<LocalQwenModelDownloadState>
    fun download(modelName: String): Flow<LocalQwenModelDownloadState>
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

    override fun observeStatus(modelName: String): Flow<LocalQwenModelDownloadState> = flow {
        emit(status(modelName))
    }.flowOn(Dispatchers.IO)

    override fun download(modelName: String): Flow<LocalQwenModelDownloadState> = flow {
        val spec = LocalQwenModelCatalog.requireSpec(modelName)
        val modelDir = File(context.filesDir, "models/${spec.modelName}")
        val partialDir = File(context.filesDir, "models/.${spec.modelName}.partial")
        partialDir.deleteRecursively()
        if (!partialDir.mkdirs() && !partialDir.isDirectory) {
            throw IOException("Cannot create model download directory: ${partialDir.absolutePath}")
        }

        emit(status(modelName).copy(isDownloading = true, message = "Starting download"))

        var downloadedBytes = 0L
        var totalBytes = LocalQwenCatalogSizes.estimatedTotalBytes(modelName)
        LocalQwenModelCatalog.requiredFiles.forEachIndexed { index, fileName ->
            val target = File(partialDir, fileName)
            val existing = File(modelDir, fileName)
            if (existing.isFile && existing.length() > 0L) {
                existing.copyTo(target, overwrite = true)
                downloadedBytes += existing.length()
                emit(
                    LocalQwenModelDownloadState(
                        modelName = modelName,
                        isInstalled = false,
                        isDownloading = true,
                        progress = progress(index, downloadedBytes, totalBytes),
                        downloadedBytes = downloadedBytes,
                        totalBytes = totalBytes,
                        message = "Reusing $fileName",
                    )
                )
                return@forEachIndexed
            }
            val url = spec.downloadUrl(fileName)
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("ModelScope download failed (${response.code}): $fileName")
                }
                val body = response.body ?: throw IOException("Empty response body: $fileName")
                val contentLength = body.contentLength().takeIf { it > 0L }
                totalBytes = totalBytes ?: contentLength
                target.outputStream().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            emit(
                                LocalQwenModelDownloadState(
                                    modelName = modelName,
                                    isInstalled = false,
                                    isDownloading = true,
                                    progress = progress(index, downloadedBytes, totalBytes),
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes,
                                    message = "Downloading $fileName",
                                )
                            )
                        }
                    }
                }
            }
        }

        validateInstall(partialDir)
        modelDir.deleteRecursively()
        if (!partialDir.renameTo(modelDir)) {
            partialDir.copyRecursively(modelDir, overwrite = true)
            partialDir.deleteRecursively()
        }
        emit(status(modelName).copy(message = "Download complete"))
    }.flowOn(Dispatchers.IO)

    private fun status(modelName: String): LocalQwenModelDownloadState {
        val modelDir = File(context.filesDir, "models/$modelName")
        val installed = LocalQwenModelCatalog.requiredFiles.all { File(modelDir, it).isFile }
        return LocalQwenModelDownloadState(
            modelName = modelName,
            isInstalled = installed,
            progress = if (installed) 1f else 0f,
            message = if (installed) "Installed" else "Not installed",
        )
    }

    private fun validateInstall(modelDir: File) {
        val missing = LocalQwenModelCatalog.requiredFiles.filterNot { File(modelDir, it).isFile }
        if (missing.isNotEmpty()) {
            throw IOException("Local Qwen model download incomplete: ${missing.joinToString()}")
        }
    }

    private fun progress(
        fileIndex: Int,
        downloadedBytes: Long,
        totalBytes: Long?,
    ): Float {
        val byBytes = totalBytes?.takeIf { it > 0L }?.let { downloadedBytes.toFloat() / it.toFloat() }
        val byFiles = fileIndex.toFloat() / LocalQwenModelCatalog.requiredFiles.size.toFloat()
        return (byBytes ?: byFiles).coerceIn(0f, 0.99f)
    }

    private object LocalQwenCatalogSizes {
        fun estimatedTotalBytes(modelName: String): Long? =
            when (modelName) {
                "Qwen3.5-0.8B-MNN" -> 600L * 1024L * 1024L
                "Qwen3.5-2B-MNN" -> 1_600L * 1024L * 1024L
                "Qwen3.5-4B-MNN" -> 3_200L * 1024L * 1024L
                else -> null
            }
    }
}
