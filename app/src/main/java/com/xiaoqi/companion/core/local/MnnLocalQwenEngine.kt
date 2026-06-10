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
import kotlinx.coroutines.withContext

@Singleton
class MnnLocalQwenEngine @Inject constructor(
    private val modelLocator: LocalQwenModelLocator,
    private val bridgeFactory: MnnLlmBridgeFactory,
) : LocalQwenEngine {

    override fun stream(request: LocalQwenRequest): Flow<String> = callbackFlow {
        val bridge = bridgeFactory.create()
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

                bridge.load(configFile.absolutePath)
                val prompt = request.toMnnPrompt()
                withContext(Dispatchers.Default) {
                    bridge.generate(prompt) { token ->
                        if (token.isNotEmpty()) {
                            trySend(token)
                        }
                        false
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
            bridge.release()
        }
    }

    private fun LocalQwenRequest.toMnnPrompt(): String =
        listOf(systemPrompt, userMessage)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")

    private companion object {
        const val CONFIG_FILE_NAME = "config.json"
    }
}
