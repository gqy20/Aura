package com.xiaoqi.companion.feature.chat.usecase

import com.xiaoqi.companion.core.local.LocalQwenModelDownloader
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.core.mcp.CustomMcpServerPreset
import com.xiaoqi.companion.core.mcp.McpServerConfig
import com.xiaoqi.companion.core.mcp.McpServerPresets
import com.xiaoqi.companion.core.mcp.TemplatedMcpServerPreset
import com.xiaoqi.companion.data.datastore.AppPreferences
import com.xiaoqi.companion.data.repository.McpServerListRepository
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
    private val mcpServerListRepository: McpServerListRepository,
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
            update { copy(settingsMessage = "保存失败，请重试。") }
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

    /**
     * 打开 MCP settings 页面时初始化 editor:
     * - 如果 list 已有 server,默认编辑第一项
     * - 如果 list 为空,进入"新建"模式(amap 默认)
     *
     * 老数据 (单 server 字段) 由 [McpServerListRepository.readAll] 在 ViewModel combine
     * 触发时自动迁移,这里只读取迁移后的结果。
     */
    fun prepareMcpSettings(update: (ChatUiState.() -> ChatUiState) -> Unit) {
        update {
            val firstServer = toolCapabilitySettings.mcpServers.firstOrNull()
            if (firstServer != null) {
                loadServerIntoEditor(this, firstServer)
            } else {
                newServerEditorState(this)
            }
        }
    }

    /** 切换到"新建"editor 模式 — 清空所有 form field,默认 amap。 */
    fun startNewMcpServer(update: (ChatUiState.() -> ChatUiState) -> Unit) {
        update { newServerEditorState(this) }
    }

    /** 把指定 server 加载进 editor(用户点列表中的某一行)。 */
    fun loadMcpServerForEditing(
        serverId: String,
        update: (ChatUiState.() -> ChatUiState) -> Unit,
    ) {
        update {
            val server = toolCapabilitySettings.mcpServers.firstOrNull { it.id == serverId }
            server?.let { loadServerIntoEditor(this, it) } ?: this
        }
    }

    private fun loadServerIntoEditor(state: ChatUiState, server: McpServerConfig): ChatUiState =
        state.copy(
            mcpEditingServerId = server.id,
            mcpSettingsName = server.displayName,
            mcpSettingsProviderId = server.providerId,
            mcpSettingsApiKey = server.apiKey,
            mcpSettingsUrl = server.customUrl,
            mcpSettingsKeyVisible = false,
            mcpSettingsMessage = null,
        )

    private fun newServerEditorState(state: ChatUiState): ChatUiState = state.copy(
        mcpEditingServerId = null,
        mcpSettingsName = "",
        mcpSettingsProviderId = "amap",
        mcpSettingsApiKey = "",
        mcpSettingsUrl = "",
        mcpSettingsKeyVisible = false,
        mcpSettingsMessage = null,
    )

    fun updateMcpSettingsName(value: String, update: (ChatUiState.() -> ChatUiState) -> Unit) {
        update { copy(mcpSettingsName = value, mcpSettingsMessage = null) }
    }

    fun updateMcpSettingsUrl(value: String, update: (ChatUiState.() -> ChatUiState) -> Unit) {
        update { copy(mcpSettingsUrl = value, mcpSettingsMessage = null) }
    }

    fun updateMcpSettingsApiKey(value: String, update: (ChatUiState.() -> ChatUiState) -> Unit) {
        update { copy(mcpSettingsApiKey = value, mcpSettingsMessage = null) }
    }

    fun toggleMcpKeyVisibility(update: (ChatUiState.() -> ChatUiState) -> Unit) {
        update { copy(mcpSettingsKeyVisible = !mcpSettingsKeyVisible) }
    }

    /**
     * 切换 editor 中的服务商。会按目标 provider 重置无关字段:
     * - 切到 templated (amap): 清空 url(从 key 派生,不该手填)
     * - 切到 custom: 保留现有 url(用户可能已填)
     */
    fun selectMcpProvider(
        providerId: String,
        update: (ChatUiState.() -> ChatUiState) -> Unit,
    ) {
        val preset = McpServerPresets.byId(providerId)
        update {
            copy(
                mcpSettingsProviderId = preset.id,
                mcpSettingsMessage = null,
                mcpSettingsUrl = if (preset is CustomMcpServerPreset) mcpSettingsUrl else "",
            )
        }
    }

    /**
     * 把 editor 当前状态写回 server list:
     * - [ChatUiState.mcpEditingServerId] == null → 新增
     * - 非 null → 更新已有
     *
     * 校验按 provider 类型走不同分支 (amap 必须有 key,custom 必须有 url)。
     */
    suspend fun saveMcpSettings(
        state: ChatUiState,
        scope: CoroutineScope,
        update: (ChatUiState.() -> ChatUiState) -> Unit,
    ) {
        val provider = McpServerPresets.byId(state.mcpSettingsProviderId)
        val name = state.mcpSettingsName.trim()
        val apiKey = state.mcpSettingsApiKey.trim()
        val customUrl = state.mcpSettingsUrl.trim()

        when {
            provider is CustomMcpServerPreset -> {
                if (customUrl.isBlank()) {
                    update { copy(mcpSettingsMessage = "请填写 MCP URL") }
                    return
                }
                if (!customUrl.startsWith("http://") && !customUrl.startsWith("https://")) {
                    update { copy(mcpSettingsMessage = "MCP URL 必须以 http:// 或 https:// 开头") }
                    return
                }
            }
            provider is TemplatedMcpServerPreset -> {
                if (apiKey.isBlank()) {
                    update { copy(mcpSettingsMessage = "${provider.keyHint}不能为空") }
                    return
                }
            }
        }

        val config = McpServerConfig(
            id = state.mcpEditingServerId ?: java.util.UUID.randomUUID().toString(),
            displayName = name,
            providerId = provider.id,
            apiKey = apiKey,
            customUrl = if (provider is CustomMcpServerPreset) customUrl else "",
            enabled = true,
        )

        val startedAt = System.currentTimeMillis()
        try {
            AppLogger.info(
                LogTags.Config,
                "mcp_settings_save_started",
                "editingId" to (state.mcpEditingServerId ?: "new"),
                "providerId" to provider.id,
            )
            if (state.mcpEditingServerId == null) {
                mcpServerListRepository.add(config)
            } else {
                mcpServerListRepository.update(config)
            }
            update { copy(mcpSettingsMessage = null, mcpEditorJustSaved = mcpEditorJustSaved + 1) }
            AppLogger.info(
                LogTags.Config,
                "mcp_settings_save_completed",
                "serverId" to config.id,
                "providerId" to provider.id,
                "durationMs" to (System.currentTimeMillis() - startedAt),
            )
        } catch (e: Exception) {
            AppLogger.error(
                LogTags.Config,
                e,
                "mcp_settings_save_failed",
                "serverId" to config.id,
                "providerId" to provider.id,
                "durationMs" to (System.currentTimeMillis() - startedAt),
            )
            update { copy(mcpSettingsMessage = "保存失败,请重试。") }
        }
    }

    /** 删除一条 server。如果删的恰好是当前 editor 加载的,清空 editor。 */
    suspend fun removeMcpServer(
        serverId: String,
        update: (ChatUiState.() -> ChatUiState) -> Unit,
    ) {
        mcpServerListRepository.remove(serverId)
        update {
            if (mcpEditingServerId == serverId) newServerEditorState(this) else this
        }
    }

    /** 切换 enabled 软开关 — 立刻生效 (CompanionToolRegistry 重新 build 时会过滤)。 */
    suspend fun toggleMcpServerEnabled(
        serverId: String,
        update: (ChatUiState.() -> ChatUiState) -> Unit,
    ) {
        mcpServerListRepository.toggleEnabled(serverId)
        update { copy(mcpSettingsMessage = null) }
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
            update { copy(error = "更新设置失败，请重试。") }
        }
    }

    //endregion

    //region 辅助

    // (无 — 所有方法都接收显式 state 参数,避免侧信道读取)

    //endregion
}
