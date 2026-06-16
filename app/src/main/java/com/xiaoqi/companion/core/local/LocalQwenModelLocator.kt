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

    /**
     * 扫 `filesDir/models/` 找到第一个 [LocalQwenModelCatalog] 里"requiredFiles 全齐"的 modelName。
     * 用于后台任务 / UI 状态源的统一:不论主对话 Provider 是云端还是本地,
     * 永远以"本地实际装好的那个"作为 local model 单一真相,避免 UI 显示"未安装"但目录里其实是装好的。
     */
    fun findAnyInstalledModel(): String?
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

    override fun findAnyInstalledModel(): String? {
        val modelsDir = File(context.filesDir, "models")
        return LocalQwenModelCatalog.models.firstOrNull { spec ->
            val dir = File(modelsDir, spec.modelName)
            LocalQwenModelCatalog.requiredFiles.all { File(dir, it).isFile }
        }?.modelName
    }

    private companion object {
        const val CONFIG_FILE_NAME = "config.json"
    }
}
