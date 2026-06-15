package com.xiaoqi.companion.core.local

import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import javax.inject.Inject

class NativeMnnLlmBridgeFactory @Inject constructor() : MnnLlmBridgeFactory {
    override fun create(): MnnLlmBridge =
        NativeMnnLlmBridge()
}

class NativeMnnLlmBridge(
    private val native: NativeMnnLlmApi = JniNativeMnnLlmApi,
) : MnnLlmBridge {
    private var instanceId: Long = 0L

    override suspend fun load(configPath: String, runtimeConfig: String) {
        AppLogger.info(
            LogTags.LocalModel,
            "mnn_bridge_load_started",
            "configPath" to configPath,
            "hasRuntimeConfig" to runtimeConfig.isNotEmpty(),
        )
        ensureNativeAvailable()
        instanceId = native.init(configPath, runtimeConfig)
        if (instanceId == 0L) {
            throw IllegalStateException("MNN native session failed to load: $configPath")
        }
        AppLogger.info(
            LogTags.LocalModel,
            "mnn_bridge_load_completed",
            "configPath" to configPath,
            "instanceLoaded" to (instanceId != 0L),
        )
    }

    override fun generate(
        systemPrompt: String,
        userMessage: String,
        onToken: (String) -> Boolean,
    ): Map<String, Any> {
        ensureLoaded()
        AppLogger.info(
            LogTags.LocalModel,
            "mnn_bridge_generate_started",
            "systemPromptLength" to systemPrompt.length,
            "userMessageLength" to userMessage.length,
        )
        return native.submit(
            instanceId = instanceId,
            systemPrompt = systemPrompt,
            userMessage = userMessage,
            listener = object : NativeMnnProgressListener {
                override fun onProgress(token: String?): Boolean =
                    token?.let(onToken) ?: false
            },
        ).also { stats ->
            AppLogger.info(
                LogTags.LocalModel,
                "mnn_bridge_generate_completed",
                "statKeys" to stats.keys.joinToString(separator = ","),
            )
        }
    }

    override fun release() {
        if (instanceId != 0L && native.loadLibrary()) {
            AppLogger.debug(
                LogTags.LocalModel,
                "mnn_bridge_release_started",
                "instanceLoaded" to true,
            )
            native.release(instanceId)
            instanceId = 0L
            AppLogger.debug(LogTags.LocalModel, "mnn_bridge_release_completed")
        }
    }

    private fun ensureNativeAvailable() {
        if (!native.loadLibrary()) {
            throw IllegalStateException(
                "MNN native library aura_mnn_llm is not available. " +
                    "Build or package the Aura MNN JNI library before using LOCAL_QWEN.",
            )
        }
    }

    private fun ensureLoaded() {
        ensureNativeAvailable()
        check(instanceId != 0L) { "MNN native session is not loaded." }
    }
}

interface NativeMnnProgressListener {
    fun onProgress(token: String?): Boolean
}

interface NativeMnnLlmApi {
    fun loadLibrary(): Boolean
    fun init(configPath: String, runtimeConfig: String = ""): Long
    fun submit(
        instanceId: Long,
        systemPrompt: String,
        userMessage: String,
        listener: NativeMnnProgressListener,
    ): Map<String, Any>
    fun release(instanceId: Long)
}

private object JniNativeMnnLlmApi : NativeMnnLlmApi {
    @Volatile
    private var libraryLoadAttempted = false
    @Volatile
    private var libraryLoaded = false

    override fun loadLibrary(): Boolean {
        if (!libraryLoadAttempted) {
            synchronized(this) {
                if (!libraryLoadAttempted) {
                    AppLogger.info(LogTags.LocalModel, "mnn_native_library_load_started")
                    libraryLoaded = runCatching {
                        System.loadLibrary("aura_mnn_llm")
                    }.onFailure { throwable ->
                        AppLogger.error(
                            LogTags.LocalModel,
                            throwable,
                            "mnn_native_library_load_failed",
                        )
                    }.isSuccess
                    AppLogger.info(
                        LogTags.LocalModel,
                        "mnn_native_library_load_result",
                        "loaded" to libraryLoaded,
                    )
                    libraryLoadAttempted = true
                }
            }
        }
        return libraryLoaded
    }

    override fun init(configPath: String, runtimeConfig: String): Long =
        initNative(configPath, runtimeConfig)

    override fun submit(
        instanceId: Long,
        systemPrompt: String,
        userMessage: String,
        listener: NativeMnnProgressListener,
    ): Map<String, Any> =
        submitNative(instanceId, systemPrompt, userMessage, listener)

    override fun release(instanceId: Long) {
        releaseNative(instanceId)
    }

    private external fun initNative(configPath: String, runtimeConfig: String): Long
    private external fun submitNative(
        instanceId: Long,
        systemPrompt: String,
        userMessage: String,
        listener: NativeMnnProgressListener,
    ): HashMap<String, Any>
    private external fun releaseNative(instanceId: Long)
}
