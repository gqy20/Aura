package com.xiaoqi.companion.core.local

import android.os.Build

/**
 * MNN inference runtime configuration.
 *
 * All parameters are optional and have sensible defaults.
 * The [toJson] output is merged into the native `set_config` call,
 * so any field left `null` falls back to MNN's built-in default.
 *
 * This is the single extension point for future hardware-adaptive logic:
 * a [DeviceCapability] detector can fill in optimal values per-device
 * (e.g. thread_num = big-core count, backend = opencl when available).
 */
data class MnnInferenceConfig(
    val threadNum: Int? = null,
    val precision: String? = null,
    val memory: String? = null,
    val backendType: String? = null,
    val samplerType: String? = null,
    val temperature: Float? = null,
    val topK: Int? = null,
    val topP: Float? = null,
    val minP: Float? = null,
    val repetitionPenalty: Float? = null,
    val maxNewTokens: Int? = null,
) {
    /**
     * Serialize to a JSON string suitable for MNN `set_config`.
     * Only non-null fields are included.
     *
     * Uses manual string building to avoid `org.json.JSONObject` which
     * is an Android stub and unavailable in JVM unit tests.
     */
    fun toJson(): String {
        val parts = mutableListOf<String>()
        threadNum?.let { parts += "\"thread_num\":$it" }
        precision?.let { parts += "\"precision\":\"$it\"" }
        memory?.let { parts += "\"memory\":\"$it\"" }
        backendType?.let { parts += "\"backend_type\":\"$it\"" }
        samplerType?.let { parts += "\"sampler_type\":\"$it\"" }
        temperature?.let { parts += "\"temperature\":$it" }
        topK?.let { parts += "\"top_k\":$it" }
        topP?.let { parts += "\"top_p\":$it" }
        minP?.let { parts += "\"min_p\":$it" }
        repetitionPenalty?.let { parts += "\"repetition_penalty\":$it" }
        maxNewTokens?.let { parts += "\"max_new_tokens\":$it" }
        if (parts.isEmpty()) return "{}"
        return "{${parts.joinToString(",")}}"
    }

    companion object {
        /** Empty config — all fields null, MNN uses its own defaults. */
        val DEFAULT = MnnInferenceConfig()

        /**
         * Recommended defaults for Dimensity 8200 (MT6895):
         * 4× Cortex-A78 big cores, ARM82 fp16, OpenCL available.
         */
        fun forDimensity8200(): MnnInferenceConfig = MnnInferenceConfig(
            threadNum = 4,
            precision = "low",
            memory = "low",
            backendType = "cpu",
            samplerType = "mixed",
            temperature = 0.7f,
            topK = 40,
            topP = 0.9f,
            minP = 0.05f,
            repetitionPenalty = 1.05f,
        )

        /**
         * Preset for flagship SoCs with SME2 (Dimensity 9300+, Snapdragon 8 Gen 3+).
         * Will be refined with real benchmark data.
         */
        fun forFlagshipSoc(): MnnInferenceConfig = MnnInferenceConfig(
            threadNum = 6,
            precision = "low",
            memory = "low",
            backendType = "cpu",
            samplerType = "mixed",
            temperature = 0.7f,
            topK = 40,
            topP = 0.9f,
            minP = 0.05f,
            repetitionPenalty = 1.05f,
        )

        fun forCurrentDevice(): MnnInferenceConfig {
            val hardware = runCatching { Build.HARDWARE }.getOrNull().orEmpty().lowercase()
            val socModel = runCatching { Build.SOC_MODEL }.getOrNull().orEmpty().lowercase()
            val supportedAbis = runCatching {
                Build.SUPPORTED_ABIS?.joinToString(separator = ",").orEmpty()
            }.getOrDefault("").lowercase()
            return when {
                "mt6895" in hardware || "mt6895" in socModel -> forDimensity8200()
                "dimensity 9300" in socModel || "dimensity 9400" in socModel -> forFlagshipSoc()
                "sm8650" in hardware || "sm8650" in socModel || "sm8750" in hardware || "sm8750" in socModel -> forFlagshipSoc()
                "arm64-v8a" in supportedAbis -> MnnInferenceConfig(
                    threadNum = 4,
                    precision = "low",
                    memory = "low",
                    backendType = "cpu",
                    samplerType = "mixed",
                    temperature = 0.7f,
                    topK = 40,
                    topP = 0.9f,
                    minP = 0.05f,
                    repetitionPenalty = 1.05f,
                )
                else -> DEFAULT
            }
        }
    }
}
