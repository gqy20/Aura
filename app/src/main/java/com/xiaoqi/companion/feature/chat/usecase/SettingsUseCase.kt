package com.xiaoqi.companion.feature.chat.usecase

import com.xiaoqi.companion.core.local.LocalQwenModelDownloader
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.data.datastore.AppPreferences
import com.xiaoqi.companion.data.db.converter.LlmProvider
import com.xiaoqi.companion.data.repository.ConfigRepository
import com.xiaoqi.companion.data.repository.DefaultLlmValues
import com.xiaoqi.companion.feature.chat.ChatUiState
import com.xiaoqi.companion.feature.chat.mapper.defaultBaseUrl
import com.xiaoqi.companion.feature.chat.mapper.toChatConfigStatus
import com.xiaoqi.companion.feature.chat.mapper.toUiState
import com.xiaoqi.companion.feature.chat.mapper.withLocalQwenDownloadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 模型配置 + MCP 服务配置 + 5 个 boolean 偏好 + 本地模型下载的统一操作入口。
 *
 * 行为分组:
 * - **Settings 面板**:`prepareSettings` / `updateSettings*` / `saveSettings` / `downloadSelectedLocalQwenModel`
 * - **MCP 面板**:`prepareMcpSettings` / `updateMcpSettings*` / `saveMcpSettings`
 * - **Boolean 偏好**:`setDeviceStatusContextEnabled` / `setLocationContextEnabled` / `setWeatherContextEnabled` / `setReminderToolEnabled` / `setNotificationEnabled`
 *
 * 状态写入全部通过 [update] 回调,与 SendMessageUseCase 保持一致。
 */
class SettingsUseCase @Inject constructor(
    private val configRepository: ConfigRepository,
    private val appPreferences: AppPreferences,
    private val localQwenModelDownloader: LocalQwenModelDownloader,
) {
    private var localQwenDownloadJob: kotlinx.coroutines.Job? = null
    private var localQwenStatusJob: kotlinx.coroutines.Job? = null

    //region Settings 面板

    fun prepareSettings(
        state: ChatUiState,
        scope: CoroutineScope,
        update: (ChatUiState.() -> ChatUiState) -> Unit,
    ) {
        update {
            copy(
                settingsProvider = state.configStatus.provider,
                settingsModelName = state.configStatus.modelName,
                settingsBaseUrl = state.configStatus.baseUrl,
                settingsMessage = null,
            )
        }
        refreshLocalQwenModelStatus(state.configStatus.modelName, scope, update)
    }

    /**
     * 用户输入即写 DataStore — 不依赖 Save 按钮(Save 按钮之前被推到屏外时
     * 用户根本点不到,导致 api_key 等字段从没真写入)。key 非空才 setApiKey,
     * 沿用 "value 不空就持久化" 的契约。
     */
    fun updateSettingsApiKey(value: String, update: (ChatUiState.() -> ChatUiState) -> Unit) {
        update { copy(settingsApiKey = value, settingsMessage = null) }
        kotlinx.coroutines.GlobalScope.launch {  // 写路径不需要 viewModel scope
            if (value.isNotBlank()) {
                configRepository.setApiKey(value.trim())
            }
        }
    }

    fun updateSettingsProvider(
        value: LlmProvider,
        scope: CoroutineScope,
        update: (ChatUiState.() -> ChatUiState) -> Unit,
    ) {
        val defaultModel = DefaultLlmValues.defaultModel(value)
        update {
            copy(
                settingsProvider = value,
                settingsModelName = defaultModel,
                settingsBaseUrl = defaultBaseUrl(value),
                settingsMessage = null,
            )
        }
        if (value == LlmProvider.LOCAL_QWEN) {
            refreshLocalQwenModelStatus(defaultModel, scope, update)
        }
    }

    fun updateSettingsModelName(
        value: String,
        state: ChatUiState,
        scope: CoroutineScope,
        update: (ChatUiState.() -> ChatUiState) -> Unit,
    ) {
        update { copy(settingsModelName = value, settingsMessage = null) }
        if (state.settingsProvider == LlmProvider.LOCAL_QWEN) {
            refreshLocalQwenModelStatus(value, scope, update)
        }
    }

    fun updateSettingsBaseUrl(value: String, update: (ChatUiState.() -> ChatUiState) -> Unit) {
        update { copy(settingsBaseUrl = value, settingsMessage = null) }
    }

    suspend fun saveSettings(
        state: ChatUiState,
        scope: CoroutineScope,
        update: (ChatUiState.() -> ChatUiState) -> Unit,
    ) {
        val provider = state.settingsProvider
        val model = state.settingsModelName.trim()
            .takeIf { it in DefaultLlmValues.modelOptions(provider) }
            ?: DefaultLlmValues.defaultModel(provider)
        val baseUrl = DefaultLlmValues.defaultBaseUrl(provider)
        if (model.isBlank()) {
            update { copy(settingsMessage = "模型名称不能为空") }
            return
        }
        if (provider != LlmProvider.LOCAL_QWEN && baseUrl.isBlank()) {
            update { copy(settingsMessage = "Base URL 不能为空") }
            return
        }
        val startedAt = System.currentTimeMillis()
        try {
            AppLogger.info(LogTags.Config, "settings_save_started", "provider" to provider, "model" to model)
            configRepository.setLlmProvider(provider)
            configRepository.setModelName(model)
            configRepository.setBaseUrl(baseUrl)
            state.settingsApiKey.trim().takeIf { it.isNotEmpty() }?.let { apiKey ->
                configRepository.setApiKey(apiKey)
            }
            update {
                copy(
                    settingsApiKey = "",
                    settingsModelName = model,
                    settingsBaseUrl = baseUrl,
                    settingsMessage = null,
                )
            }
            AppLogger.info(
                LogTags.Config,
                "settings_save_completed",
                "provider" to provider,
                "model" to model,
                "durationMs" to (System.currentTimeMillis() - startedAt),
            )
        } catch (e: Exception) {
            AppLogger.error(
                LogTags.Config,
                e,
                "settings_save_failed",
                "provider" to provider,
                "model" to model,
                "durationMs" to (System.currentTimeMillis() - startedAt),
            )
            update { copy(settingsMessage = "Save settings failed. Please try again.") }
        }
    }

    fun downloadSelectedLocalQwenModel(
        state: ChatUiState,
        scope: CoroutineScope,
        update: (ChatUiState.() -> ChatUiState) -> Unit,
    ) {
        if (state.settingsProvider != LlmProvider.LOCAL_QWEN) return
        val modelName = state.settingsModelName
            .takeIf { it in DefaultLlmValues.modelOptions(LlmProvider.LOCAL_QWEN) }
            ?: DefaultLlmValues.LOCAL_QWEN_MODEL
        localQwenDownloadJob?.cancel()
        localQwenDownloadJob = scope.launch {
            try {
                localQwenModelDownloader.download(modelName).collect { downloadState ->
                    update {
                        copy(
                            configStatus = configStatus.withLocalQwenDownloadState(downloadState),
                            localQwenDownload = downloadState.toUiState(),
                        )
                    }
                }
            } catch (e: Exception) {
                AppLogger.error(
                    LogTags.Config,
                    e,
                    "local_qwen_download_failed",
                    "model" to modelName,
                )
                update {
                    copy(
                        localQwenDownload = localQwenDownload.copy(
                            modelName = modelName,
                            isDownloading = false,
                            error = e.message ?: "Download failed",
                        ),
                        settingsMessage = "Local Qwen download failed: ${e.message ?: "unknown error"}",
                    )
                }
            }
        }
    }

    fun refreshLocalQwenModelStatus(
        modelName: String,
        scope: CoroutineScope,
        update: (ChatUiState.() -> ChatUiState) -> Unit,
    ) {
        if (modelName !in DefaultLlmValues.modelOptions(LlmProvider.LOCAL_QWEN)) return
        if (localQwenDownloadJob?.isActive == true) return
        localQwenStatusJob?.cancel()
        localQwenStatusJob = scope.launch {
            localQwenModelDownloader.observeStatus(modelName).collect { downloadState ->
                update {
                    copy(
                        configStatus = configStatus.withLocalQwenDownloadState(downloadState),
                        localQwenDownload = downloadState.toUiState(),
                    )
                }
            }
        }
    }

    //endregion

    //region MCP 面板

    fun prepareMcpSettings(update: (ChatUiState.() -> ChatUiState) -> Unit) {
        update {
            copy(
                mcpSettingsName = toolCapabilitySettings.mcpServerName,
                mcpSettingsUrl = toolCapabilitySettings.mcpHttpUrl,
                mcpSettingsMessage = null,
            )
        }
    }

    fun updateMcpSettingsUrl(value: String, update: (ChatUiState.() -> ChatUiState) -> Unit) {
        update { copy(mcpSettingsUrl = value, mcpSettingsMessage = null) }
    }

    fun updateMcpSettingsName(value: String, update: (ChatUiState.() -> ChatUiState) -> Unit) {
        update { copy(mcpSettingsName = value, mcpSettingsMessage = null) }
    }

    suspend fun saveMcpSettings(
        state: ChatUiState,
        scope: CoroutineScope,
        update: (ChatUiState.() -> ChatUiState) -> Unit,
    ) {
        val name = state.mcpSettingsName.trim()
        val url = state.mcpSettingsUrl.trim()
        if (url.isNotBlank() && name.isBlank()) {
            update { copy(mcpSettingsMessage = "MCP name is required when a URL is set") }
            return
        }
        if (url.isNotBlank() && !url.startsWith("http://") && !url.startsWith("https://")) {
            update { copy(mcpSettingsMessage = "MCP URL must start with http:// or https://") }
            return
        }
        val startedAt = System.currentTimeMillis()
        try {
            AppLogger.info(LogTags.Config, "mcp_settings_save_started", "serverName" to name, "hasUrl" to url.isNotBlank())
            appPreferences.setMcpServerName(name)
            appPreferences.setMcpHttpUrl(url)
            update {
                copy(
                    mcpSettingsName = name,
                    mcpSettingsUrl = url,
                    mcpSettingsMessage = null,
                )
            }
            AppLogger.info(
                LogTags.Config,
                "mcp_settings_save_completed",
                "serverName" to name,
                "hasUrl" to url.isNotBlank(),
                "durationMs" to (System.currentTimeMillis() - startedAt),
            )
        } catch (e: Exception) {
            AppLogger.error(
                LogTags.Config,
                e,
                "mcp_settings_save_failed",
                "serverName" to name,
                "hasUrl" to url.isNotBlank(),
                "durationMs" to (System.currentTimeMillis() - startedAt),
            )
            update { copy(mcpSettingsMessage = "Save MCP settings failed. Please try again.") }
        }
    }

    //endregion

    //region Boolean 偏好

    suspend fun setDeviceStatusContextEnabled(
        value: Boolean,
        update: (ChatUiState.() -> ChatUiState) -> Unit,
    ) = updateBooleanPreference("device_status_context", value, update) {
        appPreferences.setDeviceStatusContextEnabled(value)
    }

    suspend fun setLocationContextEnabled(
        value: Boolean,
        update: (ChatUiState.() -> ChatUiState) -> Unit,
    ) = updateBooleanPreference("location_context", value, update) {
        appPreferences.setLocationContextEnabled(value)
    }

    suspend fun setWeatherContextEnabled(
        value: Boolean,
        update: (ChatUiState.() -> ChatUiState) -> Unit,
    ) = updateBooleanPreference("weather_context", value, update) {
        appPreferences.setWeatherContextEnabled(value)
    }

    suspend fun setReminderToolEnabled(
        value: Boolean,
        update: (ChatUiState.() -> ChatUiState) -> Unit,
    ) = updateBooleanPreference("reminder_tool", value, update) {
        appPreferences.setReminderToolEnabled(value)
    }

    suspend fun setNotificationEnabled(
        value: Boolean,
        update: (ChatUiState.() -> ChatUiState) -> Unit,
    ) = updateBooleanPreference("notification", value, update) {
        appPreferences.setNotificationEnabled(value)
    }

    private suspend fun updateBooleanPreference(
        name: String,
        value: Boolean,
        update: (ChatUiState.() -> ChatUiState) -> Unit,
        write: suspend () -> Unit,
    ) {
        try {
            AppLogger.info(LogTags.Config, "preference_update_started", "name" to name, "value" to value)
            write()
            AppLogger.info(LogTags.Config, "preference_update_completed", "name" to name, "value" to value)
        } catch (e: Exception) {
            AppLogger.error(LogTags.Config, e, "preference_update_failed", "name" to name, "value" to value)
            update { copy(error = "Update setting failed. Please try again.") }
        }
    }

    //endregion

    //region 辅助

    // (无 — 所有方法都接收显式 state 参数,避免侧信道读取)

    //endregion
}
