package com.xiaoqi.companion.core.local

import android.content.Context
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import java.io.File
import kotlin.math.roundToLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class LocalQwenBenchmarkRequest(
    val modelName: String? = null,
    val promptTokens: Int = 256,
    val decodeTokens: Int = 64,
    val warmupRuns: Int = 1,
    val measureRuns: Int = 3,
    val threadNum: Int? = null,
    val backendType: String? = null,
    val precision: String? = null,
    val memory: String? = null,
)

@Serializable
data class LocalQwenBenchmarkResult(
    val modelName: String,
    val modelDir: String,
    val runtimeConfig: LocalQwenBenchmarkRuntimeConfig,
    val promptTokens: Int,
    val decodeTokens: Int,
    val warmupRuns: List<LocalQwenBenchmarkRun>,
    val measuredRuns: List<LocalQwenBenchmarkRun>,
    val averages: LocalQwenBenchmarkAverages,
)

@Serializable
data class LocalQwenBenchmarkRun(
    val promptTokens: Long,
    val completionTokens: Long,
    val prefillUs: Long,
    val decodeUs: Long,
    val loadUs: Long,
    val outputChars: Int,
)

@Serializable
data class LocalQwenBenchmarkAverages(
    val promptTokens: Long,
    val completionTokens: Long,
    val prefillUs: Long,
    val decodeUs: Long,
    val loadUs: Long,
    val prefillTokensPerSecond: Double,
    val decodeTokensPerSecond: Double,
)

@Serializable
data class LocalQwenBenchmarkRuntimeConfig(
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
)

class LocalQwenBenchmarkRunner(
    private val context: Context,
    private val modelLocator: LocalQwenModelLocator = AppFilesLocalQwenModelLocator(context),
    private val bridgeFactory: MnnLlmBridgeFactory = NativeMnnLlmBridgeFactory(),
) {
    suspend fun run(request: LocalQwenBenchmarkRequest): LocalQwenBenchmarkResult = withContext(Dispatchers.IO) {
        val modelName = request.modelName?.takeIf { it.isNotBlank() }
            ?: modelLocator.findAnyInstalledModel()
            ?: error("Local Qwen model not installed")
        val modelDir = requireNotNull(modelLocator.findModelDir(modelName)) {
            "Local Qwen model not installed: $modelName"
        }
        val configFile = File(modelDir, CONFIG_FILE_NAME)
        require(configFile.isFile) { "Missing config.json for benchmark" }

        val defaultConfig = MnnInferenceConfig.forCurrentDevice()
        val runtimeConfig = defaultConfig.copy(
            threadNum = request.threadNum ?: defaultConfig.threadNum,
            backendType = request.backendType ?: defaultConfig.backendType,
            precision = request.precision ?: defaultConfig.precision,
            memory = request.memory ?: defaultConfig.memory,
            maxNewTokens = request.decodeTokens,
            temperature = 0.4f,
        )
        val bridge = bridgeFactory.create()
        val warmupStats = mutableListOf<LocalQwenBenchmarkRun>()
        val measuredStats = mutableListOf<LocalQwenBenchmarkRun>()
        try {
            AppLogger.info(
                LogTags.LocalModel,
                "local_qwen_benchmark_load_started",
                "model" to modelName,
                "threadNum" to runtimeConfig.threadNum,
                "backendType" to runtimeConfig.backendType,
                "precision" to runtimeConfig.precision,
                "memory" to runtimeConfig.memory,
                "maxNewTokens" to runtimeConfig.maxNewTokens,
            )
            bridge.load(configFile.absolutePath, runtimeConfig.toJson())
            AppLogger.info(
                LogTags.LocalModel,
                "local_qwen_benchmark_load_completed",
                "model" to modelName,
            )
            repeat(request.warmupRuns) {
                AppLogger.info(
                    LogTags.LocalModel,
                    "local_qwen_benchmark_warmup_started",
                    "index" to (it + 1),
                    "total" to request.warmupRuns,
                    "promptTokens" to request.promptTokens,
                )
                warmupStats += bridge.runBenchmark(request.promptTokens)
                AppLogger.info(
                    LogTags.LocalModel,
                    "local_qwen_benchmark_warmup_completed",
                    "index" to (it + 1),
                    "total" to request.warmupRuns,
                )
            }
            repeat(request.measureRuns) {
                AppLogger.info(
                    LogTags.LocalModel,
                    "local_qwen_benchmark_measure_started",
                    "index" to (it + 1),
                    "total" to request.measureRuns,
                    "promptTokens" to request.promptTokens,
                )
                measuredStats += bridge.runBenchmark(request.promptTokens)
                AppLogger.info(
                    LogTags.LocalModel,
                    "local_qwen_benchmark_measure_completed",
                    "index" to (it + 1),
                    "total" to request.measureRuns,
                )
            }
        } finally {
            bridge.release()
        }

        LocalQwenBenchmarkResult(
            modelName = modelName,
            modelDir = modelDir.absolutePath,
            runtimeConfig = runtimeConfig.toSnapshot(),
            promptTokens = request.promptTokens,
            decodeTokens = request.decodeTokens,
            warmupRuns = warmupStats,
            measuredRuns = measuredStats,
            averages = measuredStats.toAverages(),
        )
    }

    suspend fun writeResult(result: LocalQwenBenchmarkResult): File = withContext(Dispatchers.IO) {
        val outDir = File(context.filesDir, "benchmarks").apply { mkdirs() }
        val outFile = File(outDir, OUTPUT_FILE_NAME)
        outFile.writeText(Json { prettyPrint = true }.encodeToString(result), Charsets.UTF_8)
        AppLogger.info(
            LogTags.LocalModel,
            "local_qwen_benchmark_written",
            "path" to outFile.absolutePath,
            "model" to result.modelName,
        )
        outFile
    }

    suspend fun writeFailure(throwable: Throwable): File = withContext(Dispatchers.IO) {
        val outDir = File(context.filesDir, "benchmarks").apply { mkdirs() }
        val outFile = File(outDir, ERROR_FILE_NAME)
        outFile.writeText(
            buildString {
                appendLine(throwable::class.java.name)
                appendLine(throwable.message.orEmpty())
            },
            Charsets.UTF_8,
        )
        AppLogger.warn(
            LogTags.LocalModel,
            throwable,
            "local_qwen_benchmark_failed",
            "path" to outFile.absolutePath,
        )
        outFile
    }

    private fun MnnLlmBridge.runBenchmark(promptTokens: Int): LocalQwenBenchmarkRun {
        val prompt = buildPrompt(promptTokens)
        val collected = StringBuilder()
        val stats = generate(
            systemPrompt = SYSTEM_PROMPT,
            userMessage = prompt,
        ) { token ->
            collected.append(token)
            false
        }
        return LocalQwenBenchmarkRun(
            promptTokens = stats["prompt_tokens"].toLongOrZero(),
            completionTokens = stats["completion_tokens"].toLongOrZero(),
            prefillUs = stats["prefill_us"].toLongOrZero(),
            decodeUs = stats["decode_us"].toLongOrZero(),
            loadUs = stats["load_us"].toLongOrZero(),
            outputChars = collected.length,
        )
    }

    private fun buildPrompt(targetTokens: Int): String {
        val seed = "请用简洁中文总结下面的日记线索，并保持语义稳定。"
        return buildString {
            append("以下是用户过去几天的片段，请先理解再归纳：\n")
            while (length < targetTokens * CHARS_PER_TOKEN_ESTIMATE) {
                append(seed)
                append(' ')
            }
            append("\n请给出一段总结。")
        }
    }

    private fun Any?.toLongOrZero(): Long =
        when (this) {
            is Long -> this
            is Int -> toLong()
            is Number -> toLong()
            is String -> toLongOrNull() ?: 0L
            else -> 0L
        }

    private fun List<LocalQwenBenchmarkRun>.toAverages(): LocalQwenBenchmarkAverages {
        fun avg(selector: (LocalQwenBenchmarkRun) -> Long): Long =
            if (isEmpty()) 0L else map(selector).average().roundToLong()

        val prefillAvg = avg { it.prefillUs }
        val decodeAvg = avg { it.decodeUs }
        val promptAvg = avg { it.promptTokens }
        val completionAvg = avg { it.completionTokens }
        return LocalQwenBenchmarkAverages(
            promptTokens = promptAvg,
            completionTokens = completionAvg,
            prefillUs = prefillAvg,
            decodeUs = decodeAvg,
            loadUs = avg { it.loadUs },
            prefillTokensPerSecond = if (prefillAvg > 0) 1_000_000.0 * promptAvg / prefillAvg else 0.0,
            decodeTokensPerSecond = if (decodeAvg > 0) 1_000_000.0 * completionAvg / decodeAvg else 0.0,
        )
    }

    private fun MnnInferenceConfig.toSnapshot(): LocalQwenBenchmarkRuntimeConfig =
        LocalQwenBenchmarkRuntimeConfig(
            threadNum = threadNum,
            precision = precision,
            memory = memory,
            backendType = backendType,
            samplerType = samplerType,
            temperature = temperature,
            topK = topK,
            topP = topP,
            minP = minP,
            repetitionPenalty = repetitionPenalty,
            maxNewTokens = maxNewTokens,
        )

    private companion object {
        const val CONFIG_FILE_NAME = "config.json"
        const val OUTPUT_FILE_NAME = "local-qwen-benchmark.json"
        const val ERROR_FILE_NAME = "local-qwen-benchmark.error.txt"
        const val SYSTEM_PROMPT = "你是 Aura 的本地推理 benchmark 助手，只需稳定输出。"
        const val CHARS_PER_TOKEN_ESTIMATE = 3
    }
}
