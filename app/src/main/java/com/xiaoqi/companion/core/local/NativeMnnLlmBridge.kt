package com.xiaoqi.companion.core.local

import javax.inject.Inject

class NativeMnnLlmBridgeFactory @Inject constructor() : MnnLlmBridgeFactory {
    override fun create(): MnnLlmBridge =
        NativeMnnLlmBridge()
}

class NativeMnnLlmBridge(
    private val native: NativeMnnLlmApi = JniNativeMnnLlmApi,
) : MnnLlmBridge {
    private var instanceId: Long = 0L

    override suspend fun load(configPath: String) {
        ensureNativeAvailable()
        instanceId = native.init(configPath)
        if (instanceId == 0L) {
            throw IllegalStateException("MNN native session failed to load: $configPath")
        }
    }

    override fun generate(prompt: String, onToken: (String) -> Boolean): Map<String, Any> {
        ensureLoaded()
        return native.submit(
            instanceId = instanceId,
            prompt = prompt,
            listener = object : NativeMnnProgressListener {
                override fun onProgress(token: String?): Boolean =
                    token?.let(onToken) ?: false
            },
        )
    }

    override fun release() {
        if (instanceId != 0L && native.loadLibrary()) {
            native.release(instanceId)
            instanceId = 0L
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
    fun init(configPath: String): Long
    fun submit(
        instanceId: Long,
        prompt: String,
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
                    libraryLoaded = runCatching {
                        System.loadLibrary("aura_mnn_llm")
                    }.isSuccess
                    libraryLoadAttempted = true
                }
            }
        }
        return libraryLoaded
    }

    override fun init(configPath: String): Long =
        initNative(configPath)

    override fun submit(
        instanceId: Long,
        prompt: String,
        listener: NativeMnnProgressListener,
    ): Map<String, Any> =
        submitNative(instanceId, prompt, listener)

    override fun release(instanceId: Long) {
        releaseNative(instanceId)
    }

    private external fun initNative(configPath: String): Long
    private external fun submitNative(
        instanceId: Long,
        prompt: String,
        listener: NativeMnnProgressListener,
    ): HashMap<String, Any>
    private external fun releaseNative(instanceId: Long)
}
