package com.xiaoqi.companion.data.repository

import com.xiaoqi.companion.BuildConfig
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.data.datastore.AppPreferences
import com.xiaoqi.companion.data.db.converter.LlmProvider
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

data class LlmConfig(
    val provider: LlmProvider,
    val baseUrl: String,
    val apiKey: String,
    val modelName: String,
)

data class LlmConfigStatus(
    val provider: LlmProvider,
    val modelName: String,
    val baseUrl: String,
    val hasApiKey: Boolean,
) {
    val isReady: Boolean = hasApiKey && baseUrl.isNotBlank() && modelName.isNotBlank()
    val missingReason: String?
        get() = when {
            !hasApiKey -> "缺少 API Key"
            baseUrl.isBlank() -> "缺少 Base URL"
            modelName.isBlank() -> "缺少模型名称"
            else -> null
        }
}

interface ConfigRepository {
    val apiKey: Flow<String?>
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
}

class ConfigRepositoryImpl @Inject constructor(private val prefs: AppPreferences) : ConfigRepository {

    override val apiKey get() = prefs.apiKey
    override val baseUrl get() = prefs.baseUrl
    override val llmProvider get() = prefs.llmProvider
    override val modelName get() = prefs.modelName
    override val themeMode get() = prefs.themeMode

    override fun getCurrentLlmConfig(): Flow<LlmConfig> =
        combine(prefs.llmProvider, prefs.apiKey, prefs.modelName, prefs.baseUrl) { provider, key, model, baseUrl ->
            val resolvedModel = model.ifBlank { BuildConfig.LLM_MODEL }
            val resolvedKey = key?.takeIf { it.isNotBlank() } ?: BuildConfig.LLM_API_KEY
            val resolvedBaseUrl = baseUrl.ifBlank { provider.defaultBaseUrl() }
            if (resolvedKey.isEmpty()) {
                AppLogger.warn(
                    LogTags.Config,
                    "api_key_missing",
                    "provider" to provider,
                    "model" to resolvedModel,
                )
            }
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

    private fun LlmProvider.defaultBaseUrl(): String =
        when (this) {
            LlmProvider.GLM -> BuildConfig.LLM_BASE_URL
            LlmProvider.KIMI -> "https://api.moonshot.cn/v1"
        }
}
