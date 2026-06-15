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
    private var inferenceConfig: MnnInferenceConfig = MnnInferenceConfig.DEFAULT

    fun setInferenceConfig(config: MnnInferenceConfig) {
        inferenceConfig = config
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

                bridgeMutex.withLock {
                    val runtimeConfigJson = inferenceConfig.toJson()
                    val activeBridge = ensureBridgeLoaded(configFile.absolutePath, runtimeConfigJson)
                    withContext(Dispatchers.Default) {
                        activeBridge.generate(
                            systemPrompt = request.systemPrompt,
                            userMessage = request.userMessage,
                        ) { token ->
                            if (token.isNotEmpty()) {
                                trySend(token)
                            }
                            false
                        }
                    }
                }
                AppLogger.info(
                    LogTags.LocalModel,
                    "local_qwen_stream_completed",
                    "model" to request.modelName,
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
