package com.xiaoqi.companion.core.local

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
                val modelDir = modelLocator.findModelDir(request.modelName)
                    ?: throw IllegalStateException("Local Qwen model not found: ${request.modelName}")
                val configFile = File(modelDir, CONFIG_FILE_NAME)
                if (!configFile.isFile) {
                    throw IllegalStateException("Local Qwen model is missing config.json: ${modelDir.absolutePath}")
                }

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
                close()
            } catch (e: Throwable) {
                close(e)
            }
        }

        awaitClose {
            job.cancel()
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
