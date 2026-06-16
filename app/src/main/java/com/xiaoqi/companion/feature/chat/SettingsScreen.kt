package com.xiaoqi.companion.feature.chat

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaoqi.companion.R
import com.xiaoqi.companion.core.llm.ConnectivityResult
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.core.presence.runtime.DreamLoopInterval
import com.xiaoqi.companion.core.presence.runtime.DreamRunObserver
import com.xiaoqi.companion.feature.chat.humanizeDuration
import kotlinx.coroutines.launch
import com.xiaoqi.companion.data.db.converter.LlmProvider
import com.xiaoqi.companion.data.repository.DefaultLlmValues
import com.xiaoqi.companion.data.source.HealthConnectDataSource
import com.xiaoqi.companion.data.source.SensorManagerHealthSource
import com.xiaoqi.companion.ui.theme.ChatCardSurface
import com.xiaoqi.companion.ui.theme.ChatColors
import com.xiaoqi.companion.ui.theme.ChatStatusColors
import java.util.Locale
import java.util.concurrent.TimeUnit

internal data class SettingsScreenState(
    val apiKey: String,
    val provider: LlmProvider,
    val modelName: String,
    val baseUrl: String,
    val message: String?,
    val localQwenDownload: LocalQwenDownloadUiState,
    val toolSettings: ChatToolCapabilitySettings,
    val connectivityResult: ConnectivityResult?,
    val isCheckingConnectivity: Boolean,
    val dreamLoopInterval: DreamLoopInterval,
    val dreamRunState: DreamRunObserver.Snapshot,
    val lastDreamSuccessAtMs: Long,
    val lastDreamSuccessSavedCount: Int,
    val dataJustClearedAt: Long,
    val dataJustClearedCount: Int,
    val healthSyncState: com.xiaoqi.companion.data.source.HealthSyncManager.SyncState,
    val healthAutoSyncEnabled: Boolean,
    val healthLastSyncAt: Long,
)

internal data class SettingsScreenActions(
    val viewModel: ChatViewModel,
    val healthConnectDataSource: HealthConnectDataSource,
    val sensorHealthSource: SensorManagerHealthSource,
    val onApiKeyChanged: (String) -> Unit,
    val onProviderChanged: (LlmProvider) -> Unit,
    val onModelNameChanged: (String) -> Unit,
    val onBaseUrlChanged: (String) -> Unit,
    val onSave: () -> Unit,
    val onTestConnection: () -> Unit,
    val onDownloadLocalQwenModel: () -> Unit,
    val onDeviceStatusEnabledChanged: (Boolean) -> Unit,
    val onLocationContextEnabledChanged: (Boolean) -> Unit,
    val onWeatherContextEnabledChanged: (Boolean) -> Unit,
    val onReminderToolEnabledChanged: (Boolean) -> Unit,
    val onNotificationEnabledChanged: (Boolean) -> Unit,
    val onDreamLoopIntervalChanged: (DreamLoopInterval) -> Unit,
    val onTriggerDreamLoopNow: () -> Unit,
    val onRequestContextPermissions: () -> Unit,
    val onHealthAutoSyncEnabledChanged: (Boolean) -> Unit,
    val onHealthSyncNow: () -> Unit,
    val onOpenMcpSettings: () -> Unit,
    val onBack: () -> Unit,
) {
    companion object {
        fun from(
            viewModel: ChatViewModel,
            onRequestContextPermissions: () -> Unit,
            onOpenMcpSettings: () -> Unit,
            onBack: () -> Unit,
        ): SettingsScreenActions = SettingsScreenActions(
            viewModel = viewModel,
            healthConnectDataSource = viewModel.healthConnectDataSource,
            sensorHealthSource = viewModel.sensorHealthSource,
            onApiKeyChanged = viewModel::updateSettingsApiKey,
            onProviderChanged = viewModel::updateSettingsProvider,
            onModelNameChanged = viewModel::updateSettingsModelName,
            onBaseUrlChanged = viewModel::updateSettingsBaseUrl,
            onSave = viewModel::saveSettings,
            onTestConnection = viewModel::checkLlmConnectivity,
            onDownloadLocalQwenModel = viewModel::downloadSelectedLocalQwenModel,
            onDeviceStatusEnabledChanged = viewModel::setDeviceStatusContextEnabled,
            onLocationContextEnabledChanged = viewModel::setLocationContextEnabled,
            onWeatherContextEnabledChanged = viewModel::setWeatherContextEnabled,
            onReminderToolEnabledChanged = viewModel::setReminderToolEnabled,
            onNotificationEnabledChanged = viewModel::setNotificationEnabled,
            onDreamLoopIntervalChanged = viewModel::setDreamLoopInterval,
            onTriggerDreamLoopNow = viewModel::triggerDreamLoopNow,
            onRequestContextPermissions = onRequestContextPermissions,
            onHealthAutoSyncEnabledChanged = viewModel::setHealthAutoSyncEnabled,
            onHealthSyncNow = viewModel::triggerHealthSyncNow,
            onOpenMcpSettings = onOpenMcpSettings,
            onBack = onBack,
        )
    }
}

@Composable
fun SettingsScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onOpenMcpSettings: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dreamLoopInterval by viewModel.dreamLoopInterval.collectAsStateWithLifecycle()
    val dreamRunState by viewModel.dreamRunState.collectAsStateWithLifecycle()
    val lastDreamSuccessAtMs by viewModel.lastDreamSuccessAtMs.collectAsStateWithLifecycle()
    val lastDreamSuccessSavedCount by viewModel.lastDreamSuccessSavedCount.collectAsStateWithLifecycle()
    val healthSyncState by viewModel.healthSyncState.collectAsStateWithLifecycle()
    val healthAutoSyncEnabled by viewModel.healthAutoSyncEnabled.collectAsStateWithLifecycle()
    val healthLastSyncAt by viewModel.healthLastSyncAt.collectAsStateWithLifecycle()
    val contextPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    SettingsScreenContent(
        state = SettingsScreenState(
            apiKey = uiState.settingsApiKey,
            provider = uiState.settingsProvider,
            modelName = uiState.settingsModelName,
            baseUrl = uiState.settingsBaseUrl,
            message = uiState.settingsMessage,
            localQwenDownload = uiState.localQwenDownload,
            toolSettings = uiState.toolCapabilitySettings,
            connectivityResult = uiState.connectivityResult,
            isCheckingConnectivity = uiState.isCheckingConnectivity,
            dreamLoopInterval = dreamLoopInterval,
            dreamRunState = dreamRunState,
            lastDreamSuccessAtMs = lastDreamSuccessAtMs,
            lastDreamSuccessSavedCount = lastDreamSuccessSavedCount,
            dataJustClearedAt = uiState.dataJustClearedAt,
            dataJustClearedCount = uiState.dataJustClearedCount,
            healthSyncState = healthSyncState,
            healthAutoSyncEnabled = healthAutoSyncEnabled,
            healthLastSyncAt = healthLastSyncAt,
        ),
        actions = SettingsScreenActions.from(
            viewModel = viewModel,
            onRequestContextPermissions = {
                contextPermissionLauncher.launch(contextPermissions())
            },
            onOpenMcpSettings = onOpenMcpSettings,
            onBack = onBack,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreenContent(
    state: SettingsScreenState,
    actions: SettingsScreenActions,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    // 清空类操作完成时弹一次 snackbar:用 dataJustClearedAt 时间戳当 key,避免 count
    // 不变时(理论上不会,稳)重复弹。
    val dataClearTemplate = stringResource(R.string.data_clear_done)
    LaunchedEffect(state.dataJustClearedAt) {
        if (state.dataJustClearedAt > 0L && state.dataJustClearedCount > 0) {
            snackbarHostState.showSnackbar(dataClearTemplate.format(state.dataJustClearedCount))
        }
    }

    // dreamRunState 变化时弹一次 snackbar 反馈;Queued/Succeeded/Failed 各自一次。
    // 用 status 当 key 而不是整个 snapshot,避免 savedCount 改变时(理论上不会,但稳)重复弹。
    val queuedMsg = stringResource(R.string.dream_loop_queued)
    val completedWithCountTemplate = stringResource(R.string.dream_loop_completed_with_count)
    val completedEmptyMsg = stringResource(R.string.dream_loop_completed_empty)
    val failedMsg = stringResource(R.string.dream_loop_failed)
    LaunchedEffect(state.dreamRunState.status) {
        when (state.dreamRunState.status) {
            DreamRunObserver.Status.QUEUED -> snackbarHostState.showSnackbar(queuedMsg)
            DreamRunObserver.Status.SUCCEEDED -> {
                val msg = if (state.dreamRunState.savedCount > 0) {
                    completedWithCountTemplate.format(state.dreamRunState.savedCount)
                } else {
                    completedEmptyMsg
                }
                snackbarHostState.showSnackbar(msg)
            }
            DreamRunObserver.Status.FAILED -> snackbarHostState.showSnackbar(failedMsg)
            else -> Unit
        }
    }

    // healthSyncState 走 snackbar 反馈。Skipped 故意弹 — 用户期待点击立即有动作,
    // 没动作 = bug,告知防抖原因可减少困惑。
    val healthSuccessTemplate = stringResource(R.string.health_sync_success)
    val healthFailureTemplate = stringResource(R.string.health_sync_failure)
    val healthSkippedTemplate = stringResource(R.string.health_sync_skipped)
    LaunchedEffect(state.healthSyncState) {
        when (val s = state.healthSyncState) {
            is com.xiaoqi.companion.data.source.HealthSyncManager.SyncState.Success ->
                snackbarHostState.showSnackbar(healthSuccessTemplate.format(s.daysWithData))
            is com.xiaoqi.companion.data.source.HealthSyncManager.SyncState.Failure ->
                snackbarHostState.showSnackbar(healthFailureTemplate.format(s.reason))
            is com.xiaoqi.companion.data.source.HealthSyncManager.SyncState.Skipped ->
                snackbarHostState.showSnackbar(healthSkippedTemplate.format(humanizeDuration(s.sinceLastMs)))
            else -> Unit
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = actions.onOpenMcpSettings) {
                        Icon(Icons.Default.Build, contentDescription = "MCP 设置")
                    }
                    // 保存按钮提到 TopAppBar — 之前放在 LazyColumn 末尾会被数据透明 Section
                    // 推到屏外,用户根本点不到,导致 api_key 等字段没真正写进 DataStore。
                    androidx.compose.material3.TextButton(onClick = actions.onSave) {
                        Text("保存")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                SettingsSectionTitle(
                    title = "主对话模型",
                    subtitle = "Dream Loop / Insight / Reminder 等后台任务固定使用本地 Qwen，与本设置无关",
                )
            }
            item {
                ProviderPicker(
                    provider = state.provider,
                    onProviderChanged = actions.onProviderChanged,
                )
            }
            item {
                ModelPicker(
                    provider = state.provider,
                    modelName = state.modelName,
                    onModelNameChanged = actions.onModelNameChanged,
                )
            }
            if (state.provider != LlmProvider.LOCAL_QWEN) {
                item {
                    OutlinedTextField(
                        value = state.baseUrl,
                        onValueChange = actions.onBaseUrlChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("接口地址") },
                        readOnly = true,
                        singleLine = true,
                    )
                }
                item {
                    OutlinedTextField(
                        value = state.apiKey,
                        onValueChange = actions.onApiKeyChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API Key") },
                        placeholder = { Text("保留当前密钥") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                }
                item {
                    ConnectivityCheckRow(
                        result = state.connectivityResult,
                        isChecking = state.isCheckingConnectivity,
                        onTest = actions.onTestConnection,
                    )
                }
            }
            item {
                SettingsSectionTitle(
                    title = "本地模型",
                    subtitle = "主对话 Provider = 本地 Qwen 时使用此处下载的模型；" +
                        "Dream Loop / Insight / Reminder 等后台任务也共用此模型。",
                )
            }
            item {
                LocalQwenDownloadSection(
                    state = state.localQwenDownload,
                    onDownload = actions.onDownloadLocalQwenModel,
                )
            }
            item {
                ToolCapabilitiesSection(
                    settings = state.toolSettings,
                    onDeviceStatusEnabledChanged = actions.onDeviceStatusEnabledChanged,
                    onLocationContextEnabledChanged = actions.onLocationContextEnabledChanged,
                    onWeatherContextEnabledChanged = actions.onWeatherContextEnabledChanged,
                    onReminderToolEnabledChanged = actions.onReminderToolEnabledChanged,
                    onNotificationEnabledChanged = actions.onNotificationEnabledChanged,
                    onRequestContextPermissions = actions.onRequestContextPermissions,
                )
            }
            item {
                DreamLoopSection(
                    current = state.dreamLoopInterval,
                    runState = state.dreamRunState,
                    lastSuccessAtMs = state.lastDreamSuccessAtMs,
                    lastSuccessSavedCount = state.lastDreamSuccessSavedCount,
                    onIntervalChanged = actions.onDreamLoopIntervalChanged,
                    onTriggerNow = actions.onTriggerDreamLoopNow,
                )
            }
            item {
                HealthDataSection(
                    syncState = state.healthSyncState,
                    autoSyncEnabled = state.healthAutoSyncEnabled,
                    lastSyncAtMillis = state.healthLastSyncAt,
                    onAutoSyncEnabledChanged = actions.onHealthAutoSyncEnabledChanged,
                    onSyncNow = actions.onHealthSyncNow,
                    healthConnectDataSource = actions.healthConnectDataSource,
                    sensorSource = actions.sensorHealthSource,
                )
            }
            item {
                DataTransparencySection(viewModel = actions.viewModel)
            }
            item {
                state.message?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderPicker(
    provider: LlmProvider,
    onProviderChanged: (LlmProvider) -> Unit,
) {
    // FlowRow:避免窄屏下最后一个 chip 被挤出/换行。
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = provider == LlmProvider.GLM,
            onClick = { onProviderChanged(LlmProvider.GLM) },
            label = { Text("GLM") },
        )
        FilterChip(
            selected = provider == LlmProvider.KIMI,
            onClick = { onProviderChanged(LlmProvider.KIMI) },
            label = { Text("Kimi") },
        )
        FilterChip(
            selected = provider == LlmProvider.MODELSCOPE,
            onClick = { onProviderChanged(LlmProvider.MODELSCOPE) },
            label = { Text("ModelScope") },
        )
        FilterChip(
            selected = provider == LlmProvider.LOCAL_QWEN,
            onClick = { onProviderChanged(LlmProvider.LOCAL_QWEN) },
            label = { Text("Local") },
        )
    }
}

@Composable
private fun ModelPicker(
    provider: LlmProvider,
    modelName: String,
    onModelNameChanged: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DefaultLlmValues.modelOptions(provider).forEach { option ->
                FilterChip(
                    selected = modelName == option,
                    onClick = { onModelNameChanged(option) },
                    label = {
                        Text(
                            text = option.modelOptionLabel(provider),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    }
}

@Composable
internal fun SettingsSectionTitle(
    title: String,
    subtitle: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        subtitle?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    // 父 LazyColumn 的 18dp 不够:subtitle 会跟下方卡片粘在一起,再补 6dp。
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun LocalQwenDownloadSection(
    state: LocalQwenDownloadUiState,
    onDownload: () -> Unit,
) {
    ChatCardSurface {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = state.modelName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val status = when {
                // 下载中:把百分比和字节数合并到同一行,避免再占一行字节数。
                state.isDownloading ->
                    "下载中 ${formatPercent(state.progress)} · ${formatBytes(state.downloadedBytes, state.totalBytes)}"
                state.isInstalled -> "已安装"
                state.error != null -> "下载失败"
                else -> "未安装"
            }
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = if (state.error != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (state.isDownloading) {
                LinearProgressIndicator(
                    progress = { state.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            state.message?.takeIf { it.isNotBlank() && !state.isDownloading }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = onDownload,
                enabled = !state.isDownloading,
            ) {
                Text(if (state.isInstalled) "重试" else "下载")
            }
        }
    }
}

@Composable
private fun ToolCapabilitiesSection(
    settings: ChatToolCapabilitySettings,
    onDeviceStatusEnabledChanged: (Boolean) -> Unit,
    onLocationContextEnabledChanged: (Boolean) -> Unit,
    onWeatherContextEnabledChanged: (Boolean) -> Unit,
    onReminderToolEnabledChanged: (Boolean) -> Unit,
    onNotificationEnabledChanged: (Boolean) -> Unit,
    onRequestContextPermissions: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "能力",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            TextButton(onClick = onRequestContextPermissions) {
                Text("授权")
            }
        }
        ToolCapabilityRow(
            title = "上下文",
            detail = "时间与近期对话",
            meta = "",
            tools = "",
            enabled = true,
            locked = true,
            onEnabledChanged = {},
        )
        ToolCapabilityRow(
            title = "设备",
            detail = "电量与网络",
            meta = "本机",
            metaIcon = Icons.Outlined.Smartphone,
            tools = "",
            enabled = settings.deviceStatusEnabled,
            onEnabledChanged = onDeviceStatusEnabledChanged,
        )
        ToolCapabilityRow(
            title = "位置",
            detail = "上次授权位置",
            meta = "需授权",
            metaIcon = Icons.Outlined.Lock,
            tools = "",
            enabled = settings.locationContextEnabled,
            onEnabledChanged = onLocationContextEnabledChanged,
        )
        ToolCapabilityRow(
            title = "天气",
            detail = "当前天气",
            meta = "需联网",
            metaIcon = Icons.Outlined.Wifi,
            tools = "",
            enabled = settings.weatherContextEnabled,
            onEnabledChanged = onWeatherContextEnabledChanged,
        )
        ToolCapabilityRow(
            title = "提醒",
            detail = "本地提醒",
            meta = "",
            tools = "",
            enabled = settings.reminderToolEnabled,
            onEnabledChanged = onReminderToolEnabledChanged,
        )
        ToolCapabilityRow(
            title = "MCP",
            detail = settings.mcpServers.firstOrNull { it.isReady }?.resolvedName ?: "未配置（缺密钥/地址）",
            meta = "高级",
            metaIcon = Icons.Outlined.Bolt,
            tools = "",
            enabled = settings.mcpServers.any { it.isReady },
            locked = true,
            onEnabledChanged = {},
            statusDotColor = if (settings.mcpServers.any { it.enabled && it.isReady }) {
                ChatStatusColors.SuccessDot
            } else {
                ChatStatusColors.Unknown
            },
        )
        ToolCapabilityRow(
            title = "通知",
            detail = "提醒推送",
            meta = "需通知",
            metaIcon = Icons.Outlined.NotificationsOff,
            tools = "",
            enabled = settings.notificationEnabled,
            onEnabledChanged = onNotificationEnabledChanged,
        )
        ToolCapabilityRow(
            title = "记忆",
            detail = "回复后自动复盘",
            meta = "",
            tools = "",
            enabled = true,
            locked = true,
            onEnabledChanged = {},
        )
    }
}

@Composable
private fun ToolCapabilityRow(
    title: String,
    detail: String,
    meta: String,
    metaIcon: ImageVector? = null,
    tools: String,
    enabled: Boolean,
    locked: Boolean = false,
    onEnabledChanged: (Boolean) -> Unit,
    statusDotColor: Color? = null,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = ChatColors.CardSurface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (metaIcon != null) {
                        Icon(
                            imageVector = metaIcon,
                            contentDescription = meta,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                    if (statusDotColor != null) {
                        Canvas(
                            modifier = Modifier.size(8.dp),
                        ) {
                            drawCircle(color = statusDotColor)
                        }
                    }
                }
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
                if (tools.isNotBlank()) {
                    Text(
                        text = tools,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f),
                        maxLines = 1,
                    )
                }
            }
            if (locked) {
                // locked 行没有 Switch,不放右侧 pill 避免无意义标签
            } else {
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChanged,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DreamLoopSection(
    current: DreamLoopInterval,
    runState: DreamRunObserver.Snapshot,
    lastSuccessAtMs: Long,
    lastSuccessSavedCount: Int,
    onIntervalChanged: (DreamLoopInterval) -> Unit,
    onTriggerNow: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val showFreqWarning = current == DreamLoopInterval.M15 || current == DreamLoopInterval.M30

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsSectionTitle(
            title = stringResource(R.string.dream_loop_section_title),
            subtitle = stringResource(R.string.dream_loop_section_subtitle),
        )
        ChatCardSurface {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                ) {
                    OutlinedTextField(
                        value = stringResource(current.labelRes),
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text("周期") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        DreamLoopInterval.entries.forEach { interval ->
                            DropdownMenuItem(
                                text = { Text(stringResource(interval.labelRes)) },
                                onClick = {
                                    onIntervalChanged(interval)
                                    expanded = false
                                },
                            )
                        }
                    }
                }
                if (showFreqWarning) {
                    Text(
                        text = stringResource(R.string.dream_loop_warning_freq),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedButton(
                    onClick = onTriggerNow,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = current.isEnabled && !runState.isRunning,
                ) {
                    if (runState.isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        if (runState.isRunning) {
                            stringResource(R.string.dream_loop_running)
                        } else {
                            stringResource(R.string.dream_loop_trigger_now)
                        },
                    )
                }
                Text(
                    text = lastDreamRunLabel(lastSuccessAtMs = lastSuccessAtMs, lastSavedCount = lastSuccessSavedCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 上次运行状态的展示文案:
 * - 从未成功过 → "尚无运行记录"
 * - 跑过 + 有新增 → "上次运行: 3 分钟前 · 新增 2 条"
 * - 跑过 + 0 新增 → "上次运行: 3 分钟前"
 *
 * savedCount 仅在当前 snapshot.status == SUCCEEDED 时才有意义 — 但 UI 层无法判断
 * "当前 lastSuccessAtMs 对应的 savedCount 是多少",所以简化成"有 >0 就显示"且只在
 * status 是 SUCCEEDED 时显示。失败/运行中 → 不显示数字。
 */
@Composable
private fun lastDreamRunLabel(lastSuccessAtMs: Long, lastSavedCount: Int): String {
    if (lastSuccessAtMs == 0L) {
        return stringResource(R.string.dream_loop_last_run_never)
    }
    val ago = relativeTimeAgo(lastSuccessAtMs)
    return if (lastSavedCount > 0) {
        stringResource(R.string.dream_loop_last_run_with_count, ago, lastSavedCount)
    } else {
        stringResource(R.string.dream_loop_last_run_at, ago)
    }
}

private fun relativeTimeAgo(thenMs: Long): String {
    val diffMs = (System.currentTimeMillis() - thenMs).coerceAtLeast(0L)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMs)
    return when {
        minutes < 1L -> "刚刚"
        minutes < 60L -> "${minutes} 分钟前"
        minutes < 60L * 24L -> "${TimeUnit.MILLISECONDS.toHours(diffMs)} 小时前"
        else -> "${TimeUnit.MILLISECONDS.toDays(diffMs)} 天前"
    }
}

@Composable
internal fun CapabilityMetaPill(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.09f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            maxLines = 1,
        )
    }
}

internal fun String.modelOptionLabel(provider: LlmProvider): String =
    if (provider == LlmProvider.LOCAL_QWEN) {
        // "Qwen3.5-0.8B-MNN" -> "Qwen 0.8B":保留 Qwen 前缀便于识别尺寸档位。
        replace("Qwen3.5-", "Qwen ").removeSuffix("-MNN")
    } else {
        this
    }

internal fun formatPercent(value: Float): String =
    "${(value.coerceIn(0f, 1f) * 100f).toInt()}%"

internal fun formatBytes(downloadedBytes: Long, totalBytes: Long?): String {
    fun mb(bytes: Long): String = String.format(Locale.US, "%.1f MB", bytes / 1024f / 1024f)
    return totalBytes?.let { "${mb(downloadedBytes)} / ${mb(it)}" } ?: mb(downloadedBytes)
}

private fun contextPermissions(): Array<String> =
    buildList {
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

@Composable
private fun ConnectivityCheckRow(
    result: ConnectivityResult?,
    isChecking: Boolean,
    onTest: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onTest,
            enabled = !isChecking,
        ) {
            Text("测试连接")
        }
        if (isChecking) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
            )
        }
        ConnectivityResultLabel(result)
    }
}

@Composable
private fun ConnectivityResultLabel(result: ConnectivityResult?) {
    val (text, color) = when (result) {
        null -> "" to MaterialTheme.colorScheme.onSurfaceVariant
        is ConnectivityResult.Success -> {
            "成功 · 延迟 ${result.latencyMs} ms" to ChatStatusColors.SuccessText
        }
        is ConnectivityResult.AuthFailure -> {
            "鉴权失败（${result.statusCode}）" to MaterialTheme.colorScheme.error
        }
        is ConnectivityResult.Unreachable -> {
            "不可达：${result.cause}" to MaterialTheme.colorScheme.error
        }
    }
    if (text.isNotEmpty()) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
private fun DataTransparencySection(viewModel: ChatViewModel) {
    var insightCount by remember { mutableStateOf(0) }
    var memoryCount by remember { mutableStateOf(0) }
    var moodCount by remember { mutableStateOf(0) }
    var pendingClear by remember { mutableStateOf<ClearTarget?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val json = viewModel.exportAllJson()
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
            } catch (e: Exception) {
                AppLogger.error(LogTags.App, e, "export_all_failed")
            }
        }
    }

    LaunchedEffect(Unit) {
        insightCount = viewModel.insightCount()
        memoryCount = viewModel.memoryCount()
        moodCount = viewModel.moodSnapshotCount()
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsSectionTitle(
            title = "数据透明",
            subtitle = "本地存储，可查可改可删",
        )
        ChatCardSurface {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CountRow(ClearTarget.Insights.label, insightCount)
                CountRow(ClearTarget.MoodSnapshots.label, moodCount)
                CountRow(ClearTarget.Memories.label, memoryCount)
                Spacer(Modifier.height(4.dp))
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                            .format(java.util.Date())
                        exportLauncher.launch("aura_export_$timestamp.json")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("导出全部为 JSON")
                }
                // 二次确认由 AlertDialog 处理,无需额外标题。
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ClearButton(
                        label = "清空洞察",
                        target = ClearTarget.Insights,
                        modifier = Modifier.weight(1f),
                    ) { pendingClear = it }
                    ClearButton(
                        label = "清空情绪快照",
                        target = ClearTarget.MoodSnapshots,
                        modifier = Modifier.weight(1f),
                    ) { pendingClear = it }
                    ClearButton(
                        label = "清空记忆",
                        target = ClearTarget.Memories,
                        modifier = Modifier.weight(1f),
                    ) { pendingClear = it }
                }
            }
        }
    }

    pendingClear?.let { target ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingClear = null },
            title = { Text("确认清空？") },
            text = {
                Text(
                    "将永久删除所有${target.label}，无法恢复。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        when (target) {
                            ClearTarget.Insights -> viewModel.clearInsights()
                            ClearTarget.MoodSnapshots -> viewModel.clearMoodSnapshots()
                            ClearTarget.Memories -> viewModel.clearMemories()
                        }
                        pendingClear = null
                        scope.launch {
                            insightCount = viewModel.insightCount()
                            memoryCount = viewModel.memoryCount()
                            moodCount = viewModel.moodSnapshotCount()
                        }
                    },
                ) { Text("确定清空") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { pendingClear = null }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun CountRow(label: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "• $label",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ClearButton(
    label: String,
    target: ClearTarget,
    modifier: Modifier = Modifier,
    onClick: (ClearTarget) -> Unit,
) {
    androidx.compose.material3.TextButton(
        onClick = { onClick(target) },
        modifier = modifier,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.error,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private enum class ClearTarget(val label: String) {
    Insights("洞察"),
    MoodSnapshots("情绪快照"),
    Memories("记忆"),
}
