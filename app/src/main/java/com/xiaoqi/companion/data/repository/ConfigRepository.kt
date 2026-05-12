package com.xiaoqi.companion.data.repository

import com.xiaoqi.companion.BuildConfig
import com.xiaoqi.companion.data.datastore.AppPreferences
import com.xiaoqi.companion.data.db.converter.LlmProvider
import timber.log.Timber
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

private const val TAG = "Companion-Config"

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
            val resolvedKey = key?.takeIf { it.isNotBlank() } ?: BuildConfig.LLM_API_KEY
            if (resolvedKey.isEmpty()) {
                Timber.tag(TAG).w("API key is not set — LLM calls will fail")
            }
            LlmConfig(
                provider = provider,
                baseUrl = when (provider) {
                    LlmProvider.GLM -> BuildConfig.LLM_BASE_URL
                    LlmProvider.KIMI -> "https://api.moonshot.cn/v1"
                },
                apiKey = resolvedKey,
                modelName = model.ifBlank { BuildConfig.LLM_MODEL },
            )
        }

    override suspend fun setApiKey(key: String?) { prefs.setApiKey(key) }
    override suspend fun setModelName(name: String) { prefs.setModelName(name) }
}
