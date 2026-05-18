package com.xiaoqi.companion.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.xiaoqi.companion.data.db.converter.LlmProvider
import com.xiaoqi.companion.data.db.converter.ThemeMode
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppPreferences @Inject constructor(private val dataStore: DataStore<Preferences>) {

    val apiKey: Flow<String?> = dataStore.data.map { it[Keys.apiKey] }
    val baseUrl: Flow<String> = dataStore.data.map { it[Keys.baseUrl] ?: "" }
    val currentCompanionId: Flow<String> = dataStore.data.map { it[Keys.currentCompanionId] ?: "" }
    val themeMode: Flow<ThemeMode> = dataStore.data.map {
        ThemeMode.valueOf(it[Keys.themeMode] ?: defaultThemeMode.name)
    }
    val voiceEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.voiceEnabled] ?: true }
    val notificationEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.notificationEnabled] ?: true }
    val deviceStatusContextEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.deviceStatusContextEnabled] ?: true }
    val locationContextEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.locationContextEnabled] ?: true }
    val weatherContextEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.weatherContextEnabled] ?: true }
    val reminderToolEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.reminderToolEnabled] ?: true }
    val mcpServerName: Flow<String> = dataStore.data.map { it[Keys.mcpServerName] ?: "" }
    val mcpHttpUrl: Flow<String> = dataStore.data.map { it[Keys.mcpHttpUrl] ?: "" }
    val llmProvider: Flow<LlmProvider> = dataStore.data.map {
        LlmProvider.valueOf(it[Keys.llmProvider] ?: defaultLlmProvider.name)
    }
    val modelName: Flow<String> = dataStore.data.map { it[Keys.modelName] ?: "glm-5v-turbo" }

    suspend fun setApiKey(value: String?) { dataStore.edit { if (value != null) it[Keys.apiKey] = value else it.remove(Keys.apiKey) } }
    suspend fun setBaseUrl(value: String) { dataStore.edit { it[Keys.baseUrl] = value } }
    suspend fun setCurrentCompanionId(value: String) { dataStore.edit { it[Keys.currentCompanionId] = value } }
    suspend fun setThemeMode(value: ThemeMode) { dataStore.edit { it[Keys.themeMode] = value.name } }
    suspend fun setVoiceEnabled(value: Boolean) { dataStore.edit { it[Keys.voiceEnabled] = value } }
    suspend fun setNotificationEnabled(value: Boolean) { dataStore.edit { it[Keys.notificationEnabled] = value } }
    suspend fun setDeviceStatusContextEnabled(value: Boolean) { dataStore.edit { it[Keys.deviceStatusContextEnabled] = value } }
    suspend fun setLocationContextEnabled(value: Boolean) { dataStore.edit { it[Keys.locationContextEnabled] = value } }
    suspend fun setWeatherContextEnabled(value: Boolean) { dataStore.edit { it[Keys.weatherContextEnabled] = value } }
    suspend fun setReminderToolEnabled(value: Boolean) { dataStore.edit { it[Keys.reminderToolEnabled] = value } }
    suspend fun setMcpServerName(value: String) { dataStore.edit { it[Keys.mcpServerName] = value } }
    suspend fun setMcpHttpUrl(value: String) { dataStore.edit { it[Keys.mcpHttpUrl] = value } }
    suspend fun setLlmProvider(value: LlmProvider) { dataStore.edit { it[Keys.llmProvider] = value.name } }
    suspend fun setModelName(value: String) { dataStore.edit { it[Keys.modelName] = value } }

    object Keys {
        val apiKey = stringPreferencesKey("api_key")
        val baseUrl = stringPreferencesKey("base_url")
        val currentCompanionId = stringPreferencesKey("current_companion_id")
        val themeMode = stringPreferencesKey("theme_mode")
        val voiceEnabled = booleanPreferencesKey("voice_enabled")
        val notificationEnabled = booleanPreferencesKey("notification_enabled")
        val deviceStatusContextEnabled = booleanPreferencesKey("device_status_context_enabled")
        val locationContextEnabled = booleanPreferencesKey("location_context_enabled")
        val weatherContextEnabled = booleanPreferencesKey("weather_context_enabled")
        val reminderToolEnabled = booleanPreferencesKey("reminder_tool_enabled")
        val mcpServerName = stringPreferencesKey("mcp_server_name")
        val mcpHttpUrl = stringPreferencesKey("mcp_http_url")
        val llmProvider = stringPreferencesKey("llm_provider")
        val modelName = stringPreferencesKey("model_name")
    }

    companion object {
        val defaultThemeMode = ThemeMode.SYSTEM
        val defaultLlmProvider = LlmProvider.GLM
    }
}
