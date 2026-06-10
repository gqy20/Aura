package com.xiaoqi.companion.core.local

import android.content.Context
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

interface LocalQwenModelLocator {
    fun findModelDir(modelName: String): File?
}

@Singleton
class AppFilesLocalQwenModelLocator @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocalQwenModelLocator {

    override fun findModelDir(modelName: String): File? {
        val candidates = listOf(
            File(context.filesDir, "models/$modelName"),
            File(context.getExternalFilesDir(null), "models/$modelName"),
        )
        AppLogger.debug(
            LogTags.LocalModel,
            "local_model_lookup_started",
            "model" to modelName,
            "candidateCount" to candidates.size,
        )
        val found = candidates.firstOrNull { File(it, CONFIG_FILE_NAME).isFile }
        if (found == null) {
            AppLogger.warn(
                LogTags.LocalModel,
                "local_model_lookup_missing",
                "model" to modelName,
                "candidates" to candidates.joinToString(separator = "|") { it.absolutePath },
            )
        } else {
            AppLogger.info(
                LogTags.LocalModel,
                "local_model_lookup_found",
                "model" to modelName,
                "path" to found.absolutePath,
            )
        }
        return found
    }

    private companion object {
        const val CONFIG_FILE_NAME = "config.json"
    }
}
