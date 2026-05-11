package com.xiaoqi.companion.data.repository

import com.xiaoqi.companion.data.datastore.AppPreferences
import com.xiaoqi.companion.data.db.converter.LlmProvider
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

class ConfigRepositoryImpl(private val prefs: AppPreferences) : ConfigRepository {

    override val apiKey get() = prefs.apiKey
    override val llmProvider get() = prefs.llmProvider
    override val modelName get() = prefs.modelName
    override val themeMode get() = prefs.themeMode

    override fun getCurrentLlmConfig(): Flow<LlmConfig> =
        combine(prefs.llmProvider, prefs.apiKey, prefs.modelName) { provider, key, model ->
            LlmConfig(
                provider = provider,
                baseUrl = when (provider) {
                    LlmProvider.GLM -> "https://open.bigmodel.cn/api/paas/v1"
                    LlmProvider.KIMI -> "https://api.moonshot.cn/v1"
                },
                apiKey = key ?: "",
                modelName = model,
            )
        }

    override suspend fun setApiKey(key: String?) { prefs.setApiKey(key) }
    override suspend fun setModelName(name: String) { prefs.setModelName(name) }
}
