package com.xiaoqi.companion.core.local

import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.core.presence.runtime.ApplicationScope
import com.xiaoqi.companion.data.db.converter.LlmProvider
import com.xiaoqi.companion.data.repository.ConfigRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * App 启动时预加载本地 LLM 模型权重，让首次发消息时跳过模型加载延迟。
 *
 * 4B Q4 模型加载需要 ~11s（2.45GB 权重从磁盘读到内存），
 * 预加载后首次对话只需 prefill + decode，无需等 load。
 *
 * 仅当 provider == LOCAL_QWEN 且模型已安装时才触发。
 * fire-and-forget：调用方不等待结果，失败仅 log。
 */
@Singleton
class LocalModelPreloader @Inject constructor(
    private val engine: LocalQwenEngine,
    private val configRepository: ConfigRepository,
    private val modelDownloader: LocalQwenModelDownloader,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun preloadIfNeeded() {
        scope.launch {
            runCatching {
                val config = configRepository.getCurrentLlmConfig().first()
                if (config.provider != LlmProvider.LOCAL_QWEN) {
                    AppLogger.debug(
                        LogTags.LocalModel,
                        "local_model_preload_skipped",
                        "provider" to config.provider,
                    )
                    return@runCatching
                }
                val modelName = config.modelName.takeIf { it.isNotBlank() }
                    ?: modelDownloader.findAnyInstalledModel()
                    ?: run {
                        AppLogger.debug(
                            LogTags.LocalModel,
                            "local_model_preload_skipped",
                            "reason" to "no model installed",
                        )
                        return@runCatching
                    }
                AppLogger.info(
                    LogTags.LocalModel,
                    "local_model_preload_started",
                    "model" to modelName,
                )
                engine.preload(modelName)
            }.onFailure { e ->
                AppLogger.warn(
                    LogTags.LocalModel,
                    e,
                    "local_model_preload_failed",
                )
            }
        }
    }
}
