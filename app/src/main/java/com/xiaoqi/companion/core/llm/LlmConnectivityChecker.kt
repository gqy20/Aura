package com.xiaoqi.companion.core.llm

import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.data.db.converter.LlmProvider
import com.xiaoqi.companion.data.repository.LlmConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 模型连通性检查结果。
 *
 * - [Success]: 端点可达 + 鉴权有效(200/204)。携带延迟和模型名。
 * - [AuthFailure]: 端点可达但鉴权失败(401/403)。区分"配置问题"与"网络问题"。
 * - [Unreachable]: 端点不可达(IOException、timeout、URL 解析失败等)。
 */
sealed class ConnectivityResult {
    data class Success(
        val latencyMs: Long,
        val modelName: String,
    ) : ConnectivityResult()

    data class AuthFailure(
        val statusCode: Int,
    ) : ConnectivityResult()

    data class Unreachable(
        val cause: String,
    ) : ConnectivityResult()

    val isHealthy: Boolean
        get() = this is Success
}

/**
 * 轻量模型连通性检查器:不依赖 Koog / Tool Registry,直接用 OkHttp 探测 baseUrl。
 *
 * 探测策略:
 * - GLM/Kimi 都兼容 Anthropic Messages 协议,统一 `GET {baseUrl}/v1/models`
 * - LOCAL_QWEN 走本地 MNN,不需要 HTTP 探测 — 直接返回 [ConnectivityResult.Success]
 *   (延迟记 0,模型名取自 config.modelName)。
 *
 * 超时配置:connectTimeout 5s + readTimeout 8s — 必须快速失败,避免阻塞设置 UI。
 *
 * 线程:挂起 + `withContext(Dispatchers.IO)`,调用方在 ViewModel scope 中 launch。
 */
@Singleton
class LlmConnectivityChecker @Inject constructor() {

    // 测试可通过次构造注入 mock OkHttpClient;生产主构造走默认 OkHttp。
    @JvmOverloads
    constructor(injectedClient: OkHttpClient) : this() {
        this.injectedClient = injectedClient
    }

    private var injectedClient: OkHttpClient? = null

    private val client: OkHttpClient
        get() = injectedClient ?: OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    suspend fun check(config: LlmConfig): ConnectivityResult {
        if (config.provider == LlmProvider.LOCAL_QWEN) {
            // 本地模型走 MNN,无网络依赖,直接视为可达
            return ConnectivityResult.Success(
                latencyMs = 0L,
                modelName = config.modelName,
            )
        }

        val probeUrl = buildProbeUrl(config.baseUrl)
            ?: return ConnectivityResult.Unreachable("Base URL 解析失败: ${config.baseUrl}")

        return withContext(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            val request = Request.Builder()
                .url(probeUrl)
                .header("x-api-key", config.apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .get()
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val latency = System.currentTimeMillis() - startedAt
                    val result = when (response.code) {
                        200, 204 -> ConnectivityResult.Success(
                            latencyMs = latency,
                            modelName = config.modelName,
                        )
                        401, 403 -> ConnectivityResult.AuthFailure(statusCode = response.code)
                        else -> ConnectivityResult.Unreachable(
                            cause = "HTTP ${response.code}",
                        )
                    }
                    AppLogger.info(
                        LogTags.Config,
                        "llm_connectivity_check_completed",
                        "provider" to config.provider,
                        "model" to config.modelName,
                        "latencyMs" to latency,
                        "outcome" to result::class.simpleName,
                    )
                    result
                }
            } catch (e: IOException) {
                val cause = e.message ?: e::class.simpleName.orEmpty()
                AppLogger.warn(
                    LogTags.Config,
                    "llm_connectivity_check_unreachable",
                    "provider" to config.provider,
                    "cause" to cause,
                )
                ConnectivityResult.Unreachable(cause = cause)
            }
        }
    }

    /**
     * 把 Anthropic 兼容 baseUrl 拼成 `/v1/models` 探测端点。
     *
     * 统一拼成 `{scheme}://{host}:{port}/v1/models`,忽略原 path 上的 `v1` 后缀。
     * 解析失败 → null(返回 Unreachable)。
     */
    private fun buildProbeUrl(baseUrl: String): String? {
        val trimmed = baseUrl.trim().trimEnd('/')
        if (trimmed.isBlank()) return null
        val httpUrl = trimmed.toHttpUrlOrNull() ?: return null
        return "${httpUrl.scheme}://${httpUrl.host}:${httpUrl.port}/v1/models"
    }

    private companion object {
        const val CONNECT_TIMEOUT_SECONDS = 5L
        const val READ_TIMEOUT_SECONDS = 8L
        const val ANTHROPIC_VERSION = "2023-06-01"
    }
}
