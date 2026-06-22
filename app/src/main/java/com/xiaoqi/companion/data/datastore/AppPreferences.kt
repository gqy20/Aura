package com.xiaoqi.companion.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.xiaoqi.companion.core.presence.runtime.DreamLoopInterval
import com.xiaoqi.companion.data.repository.DefaultLlmValues
import com.xiaoqi.companion.data.db.converter.LlmProvider
import com.xiaoqi.companion.data.db.converter.ThemeMode
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

class AppPreferences @Inject constructor(private val dataStore: DataStore<Preferences>) {

    val apiKey: Flow<String?> = dataStore.data.map { it[Keys.apiKey] }
    /** per-provider API keys，JSON map 格式: {"GLM":"xxx","MODELSCOPE":"yyy"} */
    val apiKeysJson: Flow<String> = dataStore.data.map { it[Keys.apiKeysJson] ?: "{}" }
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
    /**
     * 本地模型工具调用开关。默认 false(0.8B JSON 质量不稳定 + 多轮推理慢)。
     * 开启后 ReactiveCompanion 会注入 AgentToolRegistry.create(),走 LocalToolProtocol 软协议。
     */
    val localToolsEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.localToolsEnabled] ?: false }
    /**
     * MCP 总开关。默认 true,关掉后所有 enabled 的 MCP server 工具都不会注册(系统内置工具不受影响)。
     * 与 per-server 的 McpServerConfig.enabled 是"总闸 vs 分闸"关系。
     */
    val mcpEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.mcpEnabled] ?: true }
    /**
     * 系统默认工具开关。默认 true,关掉后 11 个内置工具(记忆/时间/提醒/Health 等)都不注册,
     * 让用户能走"裸聊天"模式。
     */
    val systemToolsEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.systemToolsEnabled] ?: true }
    val mcpServerName: Flow<String> = dataStore.data.map { it[Keys.mcpServerName] ?: "" }
    val mcpHttpUrl: Flow<String> = dataStore.data.map { it[Keys.mcpHttpUrl] ?: "" }
    val mcpProviderId: Flow<String> = dataStore.data.map { it[Keys.mcpProviderId] ?: "" }
    val mcpApiKey: Flow<String> = dataStore.data.map { it[Keys.mcpApiKey] ?: "" }
    /** 多 server 模式下的列表 (JSON 字符串,见 [McpServerConfig] 序列化)。老字段保留作迁移用。 */
    val mcpServersJson: Flow<String> = dataStore.data.map { it[Keys.mcpServersJson] ?: "[]" }
    val llmProvider: Flow<LlmProvider> = dataStore.data.map {
        LlmProvider.valueOf(it[Keys.llmProvider] ?: defaultLlmProvider.name)
    }
    val modelName: Flow<String> = dataStore.data.map { it[Keys.modelName] ?: DefaultLlmValues.GLM_MODEL }
    val userPatternsJson: Flow<String> = dataStore.data.map { it[Keys.userPatternsJson] ?: "[]" }
    val recurringTopicsJson: Flow<String> = dataStore.data.map { it[Keys.recurringTopicsJson] ?: "[]" }
    val onboardingCompletedAt: Flow<String> = dataStore.data.map { it[Keys.onboardingCompletedAt] ?: "" }
    val healthLastSyncAt: Flow<Long> = dataStore.data.map { it[Keys.healthLastSyncAt] ?: 0L }
    val healthAutoSyncEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.healthAutoSyncEnabled] ?: true }

    val currentSessionId: Flow<String> = dataStore.data.map { it[Keys.currentSessionId] ?: DEFAULT_SESSION_ID }

    /**
     * Dream Loop 周期档位(分钟数)。缺失或未知值回退到 [DreamLoopInterval.DEFAULT]。
     * 存储为 Long minutes,OFF 用 0L 显式表示,落库数据可通过 [DreamLoopInterval.fromMinutesOrDefault] 反解。
     */
    val dreamLoopInterval: Flow<DreamLoopInterval> = dataStore.data.map {
        DreamLoopInterval.fromMinutesOrDefault(
            (it[Keys.dreamLoopIntervalMinutes]?.toLong()) ?: DreamLoopInterval.DEFAULT.minutes,
        )
    }

    /**
     * Dream Loop 独立模型选择。空字符串表示"跟随主聊天模型"（向后兼容），
     * 非 null/空则强制使用指定本地模型名，不受 Settings → MODEL 页影响。
     */
    val dreamLoopModelName: Flow<String> = dataStore.data.map { it[Keys.dreamLoopModelName] ?: "" }

    suspend fun setApiKey(value: String?) { dataStore.edit { if (value != null) it[Keys.apiKey] = value else it.remove(Keys.apiKey) } }
    suspend fun setApiKeysJson(value: String) { dataStore.edit { it[Keys.apiKeysJson] = value } }

    /** 为指定 provider 保存 key（读→改→写 JSON map）。 */
    suspend fun setApiKeyForProvider(provider: LlmProvider, key: String) {
        val current = apiKeysJson.first()
        val json = runCatching { JSONObject(current) }.getOrNull() ?: JSONObject()
        json.put(provider.name, key)
        setApiKeysJson(json.toString())
    }

    /** 从 JSON map 中取指定 provider 的 key，无则返回 null。 */
    suspend fun getApiKeyForProvider(provider: LlmProvider): String? {
        val current = apiKeysJson.first()
        val json = runCatching { JSONObject(current) }.getOrNull() ?: return null
        return if (json.has(provider.name)) json.getString(provider.name).takeIf { it.isNotBlank() } else null
    }
    suspend fun setBaseUrl(value: String) { dataStore.edit { it[Keys.baseUrl] = value } }
    suspend fun setCurrentCompanionId(value: String) { dataStore.edit { it[Keys.currentCompanionId] = value } }
    suspend fun setThemeMode(value: ThemeMode) { dataStore.edit { it[Keys.themeMode] = value.name } }
    suspend fun setVoiceEnabled(value: Boolean) { dataStore.edit { it[Keys.voiceEnabled] = value } }
    suspend fun setNotificationEnabled(value: Boolean) { dataStore.edit { it[Keys.notificationEnabled] = value } }
    suspend fun setDeviceStatusContextEnabled(value: Boolean) { dataStore.edit { it[Keys.deviceStatusContextEnabled] = value } }
    suspend fun setLocationContextEnabled(value: Boolean) { dataStore.edit { it[Keys.locationContextEnabled] = value } }
    suspend fun setWeatherContextEnabled(value: Boolean) { dataStore.edit { it[Keys.weatherContextEnabled] = value } }
    suspend fun setReminderToolEnabled(value: Boolean) { dataStore.edit { it[Keys.reminderToolEnabled] = value } }
    suspend fun setLocalToolsEnabled(value: Boolean) { dataStore.edit { it[Keys.localToolsEnabled] = value } }
    suspend fun setMcpEnabled(value: Boolean) { dataStore.edit { it[Keys.mcpEnabled] = value } }
    suspend fun setSystemToolsEnabled(value: Boolean) { dataStore.edit { it[Keys.systemToolsEnabled] = value } }
    suspend fun setMcpServerName(value: String) { dataStore.edit { it[Keys.mcpServerName] = value } }
    suspend fun setMcpHttpUrl(value: String) { dataStore.edit { it[Keys.mcpHttpUrl] = value } }
    suspend fun setMcpProviderId(value: String) { dataStore.edit { it[Keys.mcpProviderId] = value } }
    suspend fun setMcpApiKey(value: String) { dataStore.edit { it[Keys.mcpApiKey] = value } }
    suspend fun setMcpServersJson(value: String) { dataStore.edit { it[Keys.mcpServersJson] = value } }
    suspend fun setLlmProvider(value: LlmProvider) { dataStore.edit { it[Keys.llmProvider] = value.name } }
    suspend fun setModelName(value: String) { dataStore.edit { it[Keys.modelName] = value } }
    suspend fun setUserPatternsJson(value: String) { dataStore.edit { it[Keys.userPatternsJson] = value } }
    suspend fun setRecurringTopicsJson(value: String) { dataStore.edit { it[Keys.recurringTopicsJson] = value } }
    suspend fun setOnboardingCompletedAt(value: String) { dataStore.edit { it[Keys.onboardingCompletedAt] = value } }
    suspend fun setHealthLastSyncAt(value: Long) { dataStore.edit { it[Keys.healthLastSyncAt] = value } }
    suspend fun setHealthAutoSyncEnabled(value: Boolean) { dataStore.edit { it[Keys.healthAutoSyncEnabled] = value } }
    suspend fun setCurrentSessionId(value: String) { dataStore.edit { it[Keys.currentSessionId] = value } }
    suspend fun setDreamLoopInterval(value: DreamLoopInterval) {
        dataStore.edit { it[Keys.dreamLoopIntervalMinutes] = value.minutes.toInt() }
    }

    suspend fun setDreamLoopModelName(value: String) {
        dataStore.edit { it[Keys.dreamLoopModelName] = value }
    }

    object Keys {
        val apiKey = stringPreferencesKey("api_key")
        val apiKeysJson = stringPreferencesKey("api_keys_json")
        val baseUrl = stringPreferencesKey("base_url")
        val currentCompanionId = stringPreferencesKey("current_companion_id")
        val themeMode = stringPreferencesKey("theme_mode")
        val voiceEnabled = booleanPreferencesKey("voice_enabled")
        val notificationEnabled = booleanPreferencesKey("notification_enabled")
        val deviceStatusContextEnabled = booleanPreferencesKey("device_status_context_enabled")
        val locationContextEnabled = booleanPreferencesKey("location_context_enabled")
        val weatherContextEnabled = booleanPreferencesKey("weather_context_enabled")
        val reminderToolEnabled = booleanPreferencesKey("reminder_tool_enabled")
        val localToolsEnabled = booleanPreferencesKey("local_tools_enabled")
        val mcpEnabled = booleanPreferencesKey("mcp_enabled")
        val systemToolsEnabled = booleanPreferencesKey("system_tools_enabled")
        val mcpServerName = stringPreferencesKey("mcp_server_name")
        val mcpHttpUrl = stringPreferencesKey("mcp_http_url")
        val mcpProviderId = stringPreferencesKey("mcp_provider_id")
        val mcpApiKey = stringPreferencesKey("mcp_api_key")
        val mcpServersJson = stringPreferencesKey("mcp_servers_json")
        val llmProvider = stringPreferencesKey("llm_provider")
        val modelName = stringPreferencesKey("model_name")
        val userPatternsJson = stringPreferencesKey("user_patterns_json")
        val recurringTopicsJson = stringPreferencesKey("recurring_topics_json")
        val onboardingCompletedAt = stringPreferencesKey("onboarding_completed_at")
        val dreamLoopIntervalMinutes = intPreferencesKey("dream_loop_interval_minutes")
        val dreamLoopModelName = stringPreferencesKey("dream_loop_model_name")
        val healthLastSyncAt = androidx.datastore.preferences.core.longPreferencesKey("health_last_sync_at")
        val healthAutoSyncEnabled = booleanPreferencesKey("health_auto_sync_enabled")
        val currentSessionId = stringPreferencesKey("current_session_id")
    }

    companion object {
        val defaultThemeMode = ThemeMode.SYSTEM
        val defaultLlmProvider = LlmProvider.GLM
        const val DEFAULT_SESSION_ID = "default"
    }
}
