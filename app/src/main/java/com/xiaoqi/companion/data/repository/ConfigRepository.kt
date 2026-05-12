package com.xiaoqi.companion.data.repository

import com.xiaoqi.companion.BuildConfig
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.data.datastore.AppPreferences
import com.xiaoqi.companion.data.db.converter.LlmProvider
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class LlmConfig(
    val provider: LlmProvider,
    val baseUrl: String,
    val apiKey: String,
    val modelName: String,
)

interface ConfigRepository {
    val apiKey: Flow<String?>
    val llmProvider: Flow<LlmProvider>
    val modelName: Flow<String>
    val themeMode: Flow<com.xiaoqi.companion.data.db.converter.ThemeMode>

    fun getCurrentLlmConfig(): Flow<LlmConfig>
    suspend fun setApiKey(key: String?)
    suspend fun setModelName(name: String)
}

class ConfigRepositoryImpl @Inject constructor(private val prefs: AppPreferences) : ConfigRepository {

    override val apiKey get() = prefs.apiKey
    override val llmProvider get() = prefs.llmProvider
    override val modelName get() = prefs.modelName
    override val themeMode get() = prefs.themeMode

    override fun getCurrentLlmConfig(): Flow<LlmConfig> =
        combine(prefs.llmProvider, prefs.apiKey, prefs.modelName) { provider, key, model ->
            val resolvedModel = model.ifBlank { BuildConfig.LLM_MODEL }
            val resolvedKey = key?.takeIf { it.isNotBlank() } ?: BuildConfig.LLM_API_KEY
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
                baseUrl = when (provider) {
                    LlmProvider.GLM -> BuildConfig.LLM_BASE_URL
                    LlmProvider.KIMI -> "https://api.moonshot.cn/v1"
                },
                apiKey = resolvedKey,
                modelName = resolvedModel,
            )
        }

    override suspend fun setApiKey(key: String?) {
        prefs.setApiKey(key)
    }

    override suspend fun setModelName(name: String) {
        prefs.setModelName(name)
    }
}
