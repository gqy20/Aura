package com.xiaoqi.companion.core.local

import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class MnnLocalQwenEngine @Inject constructor(
    private val modelLocator: LocalQwenModelLocator,
    private val bridgeFactory: MnnLlmBridgeFactory,
) : LocalQwenEngine {
    private val bridgeMutex = Mutex()
    private var bridge: MnnLlmBridge? = null
    private var loadedConfigPath: String? = null
    private var loadedRuntimeConfig: String? = null

    /**
     * Active inference config. Set via [setInferenceConfig] before first [stream] call,
     * or updated between calls (will trigger bridge reload on next request).
     */
    private var inferenceConfig: MnnInferenceConfig = MnnInferenceConfig.forCurrentDevice()

    fun setInferenceConfig(config: MnnInferenceConfig) {
        inferenceConfig = config
    }

    /**
     * 预加载模型权重到内存，让首次 [stream] 调用时跳过 4B 模型 ~11s 的 loadUs。
     * App 启动时由 LocalModelPreloader fire-and-forget 调用。
     */
    override suspend fun preload(modelName: String) {
        val modelDir = modelLocator.findModelDir(modelName) ?: run {
            AppLogger.warn(
                LogTags.LocalModel,
                "local_qwen_preload_skipped",
                "model" to modelName,
                "reason" to "model not found",
            )
            return
        }
        val configFile = File(modelDir, CONFIG_FILE_NAME)
        if (!configFile.isFile) {
            AppLogger.warn(
                LogTags.LocalModel,
                "local_qwen_preload_skipped",
                "model" to modelName,
                "reason" to "config.json missing",
            )
            return
        }
        val runtimeConfig = inferenceConfig.toJson()
        bridgeMutex.withLock {
            ensureBridgeLoaded(configFile.absolutePath, runtimeConfig)
        }
        AppLogger.info(
            LogTags.LocalModel,
            "local_qwen_preload_completed",
            "model" to modelName,
        )
    }

    override fun stream(request: LocalQwenRequest): Flow<String> = callbackFlow {
        val job = launch(Dispatchers.IO) {
            try {
                AppLogger.info(
                    LogTags.LocalModel,
                    "local_qwen_stream_started",
                    "model" to request.modelName,
                    "systemPromptLength" to request.systemPrompt.length,
                    "userMessageLength" to request.userMessage.length,
                    "hasImage" to (request.imageBase64 != null),
                )
                val modelDir = modelLocator.findModelDir(request.modelName)
                    ?: throw IllegalStateException("Local Qwen model not found: ${request.modelName}")
                val configFile = File(modelDir, CONFIG_FILE_NAME)
                if (!configFile.isFile) {
                    throw IllegalStateException("Local Qwen model is missing config.json: ${modelDir.absolutePath}")
                }
                AppLogger.info(
                    LogTags.LocalModel,
                    "local_qwen_model_ready",
                    "model" to request.modelName,
                    "modelDir" to modelDir.absolutePath,
                    "configBytes" to configFile.length(),
                )

                var emittedTokenCount = 0
                bridgeMutex.withLock {
                    val activeConfig = request.inferenceConfig ?: inferenceConfig
                    val runtimeConfigJson = activeConfig.toJson()
                    AppLogger.debug(
                        LogTags.LocalModel,
                        "local_qwen_runtime_config_selected",
                        "model" to request.modelName,
                        "runtimeConfig" to runtimeConfigJson,
                    )
                    val activeBridge = ensureBridgeLoaded(configFile.absolutePath, runtimeConfigJson)
                    withContext(Dispatchers.Default) {
                        if (request.imageBase64 != null) {
                            val imageBytes = android.util.Base64.decode(
                                request.imageBase64,
                                android.util.Base64.DEFAULT,
                            )
                            activeBridge.generateWithImage(
                                systemPrompt = request.systemPrompt,
                                userMessage = request.userMessage,
                                imageBytes = imageBytes,
                                imageMediaType = request.imageMediaType ?: "image/jpeg",
                            ) { token ->
                                if (token.isNotEmpty()) {
                                    emittedTokenCount++
                                    trySend(token)
                                }
                                false
                            }
                        } else {
                            activeBridge.generate(
                                systemPrompt = request.systemPrompt,
                                userMessage = request.userMessage,
                            ) { token ->
                                if (token.isNotEmpty()) {
                                    emittedTokenCount++
                                    trySend(token)
                                }
                                false
                            }
                        }
                    }
                }
                if (emittedTokenCount == 0) {
                    throw IllegalStateException("Local Qwen generated no tokens")
                }
                AppLogger.info(
                    LogTags.LocalModel,
                    "local_qwen_stream_completed",
                    "model" to request.modelName,
                    "tokenCount" to emittedTokenCount,
                )
                close()
            } catch (e: Throwable) {
                AppLogger.error(
                    LogTags.LocalModel,
                    e,
                    "local_qwen_stream_failed",
                    "model" to request.modelName,
                )
                close(e)
            }
        }

        awaitClose {
            job.cancel()
        }
    }

    private suspend fun ensureBridgeLoaded(configPath: String, runtimeConfig: String): MnnLlmBridge {
        val currentBridge = bridge
        if (currentBridge != null &&
            loadedConfigPath == configPath &&
            loadedRuntimeConfig == runtimeConfig
        ) {
            AppLogger.debug(
                LogTags.LocalModel,
                "mnn_bridge_reused",
                "configPath" to configPath,
            )
            return currentBridge
        }
        currentBridge?.release()
        bridge = null
        loadedConfigPath = null
        loadedRuntimeConfig = null
        return bridgeFactory.create().also { newBridge ->
            newBridge.load(configPath, runtimeConfig)
            bridge = newBridge
            loadedConfigPath = configPath
            loadedRuntimeConfig = runtimeConfig
        }
    }

    private companion object {
        const val CONFIG_FILE_NAME = "config.json"
    }
}
