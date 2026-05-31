package com.xiaoqi.companion.core.local

import android.content.Context
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
        return candidates.firstOrNull { File(it, CONFIG_FILE_NAME).isFile }
    }

    private companion object {
        const val CONFIG_FILE_NAME = "config.json"
    }
}
