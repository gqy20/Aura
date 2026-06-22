package com.xiaoqi.companion.data.repository

import com.xiaoqi.companion.core.llm.ConnectivityResult
import com.xiaoqi.companion.core.llm.LlmConnectivityChecker
import com.xiaoqi.companion.data.datastore.AppPreferences
import com.xiaoqi.companion.data.db.converter.LlmProvider
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

data class LlmConfig(
    val provider: LlmProvider,
    val baseUrl: String,
    val apiKey: String,
    val modelName: String,
)

object DefaultLlmValues {
    const val GLM_BASE_URL = "https://open.bigmodel.cn/api/anthropic"
    const val GLM_MODEL = "glm-5v-turbo"
    const val KIMI_BASE_URL = "https://api.kimi.com/coding"
    const val KIMI_MODEL = "kimi-for-coding"
    // 魔搭 (ModelScope) Inference 端点,兼容 Anthropic Messages 协议:
    // 鉴权用 `x-api-key: <ms-token>`,请求路径 `${baseUrl}/v1/messages`。
    const val MODELSCOPE_BASE_URL = "https://api-inference.modelscope.cn"
    const val MODELSCOPE_MODEL = "Qwen/Qwen3.5-397B-A17B"
    const val LOCAL_QWEN_BASE_URL = ""
    const val LOCAL_QWEN_MODEL = "Qwen3.5-0.8B-MNN"
    const val LOCAL_QWEN_2B_MODEL = "Qwen3.5-2B-MNN"
    const val LOCAL_QWEN_4B_MODEL = "Qwen3.5-4B-MNN"

    fun defaultBaseUrl(provider: LlmProvider): String =
        when (provider) {
            LlmProvider.GLM -> GLM_BASE_URL
            LlmProvider.KIMI -> KIMI_BASE_URL
            LlmProvider.MODELSCOPE -> MODELSCOPE_BASE_URL
            LlmProvider.LOCAL_QWEN -> LOCAL_QWEN_BASE_URL
        }

    fun defaultModel(provider: LlmProvider): String =
        when (provider) {
            LlmProvider.GLM -> GLM_MODEL
            LlmProvider.KIMI -> KIMI_MODEL
            LlmProvider.MODELSCOPE -> MODELSCOPE_MODEL
            LlmProvider.LOCAL_QWEN -> LOCAL_QWEN_MODEL
        }

    fun modelOptions(provider: LlmProvider): List<String> =
        when (provider) {
            LlmProvider.GLM -> listOf(GLM_MODEL)
            LlmProvider.KIMI -> listOf(KIMI_MODEL)
            LlmProvider.MODELSCOPE -> listOf(MODELSCOPE_MODEL)
            LlmProvider.LOCAL_QWEN -> listOf(
                LOCAL_QWEN_MODEL,
                LOCAL_QWEN_2B_MODEL,
                LOCAL_QWEN_4B_MODEL,
            )
        }
}

data class LlmConfigStatus(
    val provider: LlmProvider,
    val modelName: String,
    val baseUrl: String,
    val hasApiKey: Boolean,
) {
    val isReady: Boolean =
        if (provider == LlmProvider.LOCAL_QWEN) {
            modelName.isNotBlank()
        } else {
            hasApiKey && baseUrl.isNotBlank() && modelName.isNotBlank()
        }

    val missingReason: String?
        get() = when {
            provider == LlmProvider.LOCAL_QWEN && modelName.isBlank() -> "Missing model name"
            provider == LlmProvider.LOCAL_QWEN -> null
            !hasApiKey -> missingText("API Key")
            baseUrl.isBlank() -> missingText("Base URL")
            modelName.isBlank() -> missingText(modelNameText())
            else -> null
        }
}

private fun missingText(subject: String): String = "缺失 $subject"

private fun modelNameText(): String = "模型名称"

interface ConfigRepository {
    val apiKey: Flow<String?>
    val apiKeysJson: Flow<String>
    val baseUrl: Flow<String>
    val llmProvider: Flow<LlmProvider>
    val modelName: Flow<String>
    val themeMode: Flow<com.xiaoqi.companion.data.db.converter.ThemeMode>

    fun getCurrentLlmConfig(): Flow<LlmConfig>
    fun observeLlmConfigStatus(): Flow<LlmConfigStatus>
    suspend fun setApiKey(key: String?)
    suspend fun setBaseUrl(url: String)
    suspend fun setLlmProvider(provider: LlmProvider)
    suspend fun setModelName(name: String)
    suspend fun checkConnectivity(): ConnectivityResult
}

class ConfigRepositoryImpl @Inject constructor(
    private val prefs: AppPreferences,
    private val connectivityChecker: LlmConnectivityChecker,
) : ConfigRepository {

    override val apiKey get() = prefs.apiKey
    override val apiKeysJson get() = prefs.apiKeysJson
    override val baseUrl get() = prefs.baseUrl
    override val llmProvider get() = prefs.llmProvider
    override val modelName get() = prefs.modelName
    override val themeMode get() = prefs.themeMode

    override fun getCurrentLlmConfig(): Flow<LlmConfig> =
        combine(prefs.llmProvider, prefs.apiKeysJson, prefs.apiKey, prefs.modelName) { provider, keysJson, legacyKey, model ->
            val resolvedModel = model.takeIf { it in DefaultLlmValues.modelOptions(provider) }
                ?: DefaultLlmValues.defaultModel(provider)
            val perProviderKey = runCatching { JSONObject(keysJson) }
                .getOrNull()
                ?.optString(provider.name, "")
                ?.takeIf { it.isNotBlank() }
            val resolvedKey = perProviderKey ?: legacyKey?.takeIf { it.isNotBlank() }.orEmpty()
            val resolvedBaseUrl = DefaultLlmValues.defaultBaseUrl(provider)
            LlmConfig(
                provider = provider,
                baseUrl = resolvedBaseUrl,
                apiKey = resolvedKey,
                modelName = resolvedModel,
            )
        }

    override fun observeLlmConfigStatus(): Flow<LlmConfigStatus> =
        getCurrentLlmConfig().map { config ->
            LlmConfigStatus(
                provider = config.provider,
                baseUrl = config.baseUrl,
                hasApiKey = config.apiKey.isNotBlank(),
                modelName = config.modelName,
            )
        }

    override suspend fun setApiKey(key: String?) {
        val provider = prefs.llmProvider.first()
        if (key != null && key.isNotBlank()) {
            prefs.setApiKeyForProvider(provider, key)
        }
        prefs.setApiKey(key)
    }

    override suspend fun setBaseUrl(url: String) {
        prefs.setBaseUrl(url)
    }

    override suspend fun setLlmProvider(provider: LlmProvider) {
        prefs.setLlmProvider(provider)
    }

    override suspend fun setModelName(name: String) {
        prefs.setModelName(name)
    }

    override suspend fun checkConnectivity(): ConnectivityResult {
        val config = getCurrentLlmConfig().first()
        return connectivityChecker.check(config)
    }
}
