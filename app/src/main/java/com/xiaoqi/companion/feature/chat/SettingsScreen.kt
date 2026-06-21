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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.xiaoqi.companion.ui.components.CompanionTopAppBar
import com.xiaoqi.companion.ui.theme.ChatCardSurface
import com.xiaoqi.companion.ui.theme.ChatColors
import com.xiaoqi.companion.ui.theme.ChatStatusColors
import com.xiaoqi.companion.ui.theme.LocalCompanionSpacing
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
    val dreamLoopModelName: String,
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
    val onLocalToolsEnabledChanged: (Boolean) -> Unit,
    val onMcpEnabledChanged: (Boolean) -> Unit,
    val onSystemToolsEnabledChanged: (Boolean) -> Unit,
    val onDreamLoopIntervalChanged: (DreamLoopInterval) -> Unit,
    val onDreamLoopModelNameChanged: (String) -> Unit,
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
            onLocalToolsEnabledChanged = viewModel::setLocalToolsEnabled,
            onMcpEnabledChanged = viewModel::setMcpEnabled,
            onSystemToolsEnabledChanged = viewModel::setSystemToolsEnabled,
            onDreamLoopIntervalChanged = viewModel::setDreamLoopInterval,
            onDreamLoopModelNameChanged = viewModel::setDreamLoopModelName,
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
    val dreamLoopModelName by viewModel.dreamLoopModelName.collectAsStateWithLifecycle()
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
            dreamLoopModelName = dreamLoopModelName,
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
    var hasConsumedInitialDreamState by remember { mutableStateOf(false) }
    var hasConsumedInitialHealthState by remember { mutableStateOf(false) }
    var selectedPage by remember { mutableStateOf(SettingsPage.MODEL) }
    val spacing = LocalCompanionSpacing.current

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
        if (!hasConsumedInitialDreamState) {
            hasConsumedInitialDreamState = true
            return@LaunchedEffect
        }
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
        if (!hasConsumedInitialHealthState) {
            hasConsumedInitialHealthState = true
            return@LaunchedEffect
        }
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
            CompanionTopAppBar(
                title = "设置",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navigationContentDescription = "返回",
                onNavigationClick = actions.onBack,
                actionText = "保存",
                onActionClick = actions.onSave,
                extraActions = {
                    IconButton(onClick = actions.onOpenMcpSettings) {
                        Icon(Icons.Default.Build, contentDescription = "MCP 设置")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.section),
        ) {
            SettingsPagePicker(
                selectedPage = selectedPage,
                onSelectedPageChanged = { selectedPage = it },
            )
            state.message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            AnimatedContent(
                modifier = Modifier.weight(1f, fill = true),
                targetState = selectedPage,
                transitionSpec = {
                    fadeIn(tween(AuraMotion.MediumMs)) togetherWith fadeOut(tween(AuraMotion.ShortMs))
                },
                label = "settings-page",
            ) { page ->
                when (page) {
                    SettingsPage.MODEL -> SettingsPageContent { settingsModelPage(state, actions) }
                    SettingsPage.CAPABILITIES -> SettingsPageContent { settingsCapabilitiesPage(state, actions) }
                    SettingsPage.SYSTEM -> SettingsPageContent { settingsSystemPage(state, actions) }
                }
            }
        }
    }
}

@Composable
private fun SettingsPageContent(content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(LocalCompanionSpacing.current.section),
        contentPadding = PaddingValues(bottom = LocalCompanionSpacing.current.section),
    ) {
        content()
    }
}

private enum class SettingsPage(
    val label: String,
) {
    MODEL("模型"),
    CAPABILITIES("能力"),
    SYSTEM("系统"),
}

private fun LazyListScope.settingsModelPage(
    state: SettingsScreenState,
    actions: SettingsScreenActions,
) {
    item {
        ModelSummaryCard(state = state)
    }
    item {
        SettingsSectionTitle(
            title = "当前引擎",
            subtitle = "选择主对话使用的 Provider 与模型",
        )
    }
    item {
        SettingsGroupCard {
            SettingsFieldLabel("Provider")
            ProviderPicker(
                provider = state.provider,
                onProviderChanged = actions.onProviderChanged,
            )
            Spacer(Modifier.height(8.dp))
            SettingsFieldLabel("Model")
            ModelPicker(
                provider = state.provider,
                modelName = state.modelName,
                onModelNameChanged = actions.onModelNameChanged,
            )
        }
    }
    if (state.provider != LlmProvider.LOCAL_QWEN) {
        item {
            SettingsSectionTitle(
                title = "云端接入",
                subtitle = "云端模型会即时保存接口配置与密钥",
            )
        }
        item {
            SettingsGroupCard {
                OutlinedTextField(
                    value = state.baseUrl,
                    onValueChange = actions.onBaseUrlChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("接口地址") },
                    readOnly = true,
                    singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
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
                Spacer(Modifier.height(10.dp))
                ConnectivityCheckRow(
                    result = state.connectivityResult,
                    isChecking = state.isCheckingConnectivity,
                    onTest = actions.onTestConnection,
                )
            }
        }
    }
    item {
        SettingsSectionTitle(
            title = "本地模型",
            subtitle = "后台任务默认共用本地模型资源",
        )
    }
    item {
        LocalQwenDownloadSection(
            state = state.localQwenDownload,
            onDownload = actions.onDownloadLocalQwenModel,
        )
    }
}

@Composable
private fun ModelSummaryCard(state: SettingsScreenState) {
    ChatCardSurface {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "当前：${state.provider.name} · ${state.modelName.modelOptionLabel(state.provider)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val statusText = when {
                state.provider == LlmProvider.LOCAL_QWEN && state.localQwenDownload.isChecking -> "正在检查本地模型"
                state.provider == LlmProvider.LOCAL_QWEN && state.localQwenDownload.isDownloading -> "本地模型下载中"
                state.provider == LlmProvider.LOCAL_QWEN && state.localQwenDownload.isInstalled -> "本地模型已安装"
                state.provider == LlmProvider.LOCAL_QWEN -> "本地模型未安装"
                state.isCheckingConnectivity -> "正在测试连接"
                else -> state.connectivityResult.summaryLabel()
            }
            SettingsMetaRow(
                label = "状态",
                value = statusText,
            )
            SettingsMetaRow(
                label = "说明",
                value = if (state.provider == LlmProvider.LOCAL_QWEN) {
                    "聊天与后台任务都会优先复用这份本地模型"
                } else {
                    "当前主对话走云端模型，本地模型保留给后台任务"
                },
            )
        }
    }
}

@Composable
private fun SettingsFieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SettingsMetaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun LazyListScope.settingsCapabilitiesPage(
    state: SettingsScreenState,
    actions: SettingsScreenActions,
) {
    item {
        SettingsSectionTitle(
            title = "基础能力",
            subtitle = "这些开关控制 Aura 可读取的环境上下文与工具",
        )
    }
    item {
        SettingsGroupCard {
            SettingsToggleRow(
                title = "设备",
                detail = "电量与网络",
                meta = "本机",
                metaIcon = Icons.Outlined.Smartphone,
                checked = state.toolSettings.deviceStatusEnabled,
                onCheckedChange = actions.onDeviceStatusEnabledChanged,
            )
            DividerSpacer()
            SettingsToggleRow(
                title = "位置",
                detail = "上次授权位置",
                meta = "需授权",
                metaIcon = Icons.Outlined.Lock,
                checked = state.toolSettings.locationContextEnabled,
                onCheckedChange = actions.onLocationContextEnabledChanged,
            )
            DividerSpacer()
            SettingsToggleRow(
                title = "天气",
                detail = "当前天气",
                meta = "需联网",
                metaIcon = Icons.Outlined.Wifi,
                checked = state.toolSettings.weatherContextEnabled,
                onCheckedChange = actions.onWeatherContextEnabledChanged,
            )
            DividerSpacer()
            SettingsToggleRow(
                title = "提醒",
                detail = "本地提醒",
                meta = "",
                checked = state.toolSettings.reminderToolEnabled,
                onCheckedChange = actions.onReminderToolEnabledChanged,
            )
            DividerSpacer()
            SettingsToggleRow(
                title = "通知",
                detail = "提醒推送",
                meta = "需通知",
                metaIcon = Icons.Outlined.NotificationsOff,
                checked = state.toolSettings.notificationEnabled,
                onCheckedChange = actions.onNotificationEnabledChanged,
            )
            DividerSpacer()
            SettingsToggleRow(
                title = "本地工具调用",
                detail = "本地模型可调记忆/提醒等工具(实验)",
                meta = "实验",
                metaIcon = Icons.Outlined.Science,
                checked = state.toolSettings.localToolsEnabled,
                onCheckedChange = actions.onLocalToolsEnabledChanged,
            )
            DividerSpacer()
            SettingsToggleRow(
                title = "系统工具",
                detail = "记忆/时间/提醒/Health 等内置工具",
                meta = "",
                checked = state.toolSettings.systemToolsEnabled,
                onCheckedChange = actions.onSystemToolsEnabledChanged,
            )
            DividerSpacer()
            SettingsToggleRow(
                title = "上下文",
                detail = "时间与近期对话",
                meta = "固定开启",
                checked = true,
                enabled = false,
                locked = true,
                onCheckedChange = {},
            )
            DividerSpacer()
            SettingsToggleRow(
                title = "MCP",
                detail = state.toolSettings.mcpServers.firstOrNull { it.isReady }?.resolvedName
                    ?: "未配置（缺密钥/地址）",
                meta = "高级",
                metaIcon = Icons.Outlined.Bolt,
                checked = state.toolSettings.mcpEnabled && state.toolSettings.mcpServers.any { it.isReady },
                statusDotColor = if (state.toolSettings.mcpEnabled && state.toolSettings.mcpServers.any { it.enabled && it.isReady }) {
                    ChatStatusColors.SuccessDot
                } else {
                    ChatStatusColors.Unknown
                },
                onCheckedChange = actions.onMcpEnabledChanged,
            )
        }
    }
    item {
        // 位置/通知权限入口：之前「位置」开关只是 toggle，没有请求/跳转入口，
        // 导致权限没授予时位置拿不到。这里直接跳系统「应用详情 → 权限」页让用户开位置。
        val context = androidx.compose.ui.platform.LocalContext.current
        OutlinedButton(
            onClick = {
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", context.packageName, null),
                )
                runCatching { context.startActivity(intent) }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("授予位置 / 通知权限（跳转系统设置）")
        }
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
}

private fun LazyListScope.settingsSystemPage(
    state: SettingsScreenState,
    actions: SettingsScreenActions,
) {
    item {
        DreamLoopSection(
            current = state.dreamLoopInterval,
            modelName = state.dreamLoopModelName,
            runState = state.dreamRunState,
            lastSuccessAtMs = state.lastDreamSuccessAtMs,
            lastSuccessSavedCount = state.lastDreamSuccessSavedCount,
            onIntervalChanged = actions.onDreamLoopIntervalChanged,
            onModelNameChanged = actions.onDreamLoopModelNameChanged,
            onTriggerNow = actions.onTriggerDreamLoopNow,
        )
    }
    item {
        DataTransparencySection(viewModel = actions.viewModel)
    }
    item {
        AboutSection()
    }
}

@Composable
private fun AboutSection() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SettingsSectionTitle(
            title = "关于",
            subtitle = "版本与素材声明",
        )
        Text(
            text = stringResource(R.string.font_credit_misans),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DreamLoopSection(
    current: DreamLoopInterval,
    modelName: String,
    runState: DreamRunObserver.Snapshot,
    lastSuccessAtMs: Long,
    lastSuccessSavedCount: Int,
    onIntervalChanged: (DreamLoopInterval) -> Unit,
    onModelNameChanged: (String) -> Unit,
    onTriggerNow: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsSectionTitle(
            title = "后台觉察",
            subtitle = "Aura 会在后台按固定间隔整理近期状态",
        )
        SettingsGroupCard {
            SettingsMetaRow(
                label = "间隔",
                value = current.label(),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DreamLoopInterval.entries.forEach { interval ->
                    FilterChip(
                        selected = current == interval,
                        onClick = { onIntervalChanged(interval) },
                        label = { Text(interval.label()) },
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(Modifier.height(4.dp))

            // Dream Loop 独立模型选择：空=跟随主聊天，非空=强制指定本地模型。
            val localOptions = DefaultLlmValues.modelOptions(LlmProvider.LOCAL_QWEN)
            SettingsFieldLabel("后台模型")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // "跟随" chip — 与主聊天 MODEL 页选择的本地模型一致。
                val isFollowing = modelName.isBlank()
                FilterChip(
                    selected = isFollowing,
                    onClick = { onModelNameChanged("") },
                    label = { Text("跟随主聊天") },
                )
                localOptions.forEach { option ->
                    FilterChip(
                        selected = !isFollowing && modelName == option,
                        onClick = { onModelNameChanged(option) },
                        label = {
                            Text(
                                text = option.modelOptionLabel(LlmProvider.LOCAL_QWEN),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
            Text(
                text = when (runState.status) {
                    DreamRunObserver.Status.IDLE -> "待命中"
                    DreamRunObserver.Status.QUEUED -> "已排队"
                    DreamRunObserver.Status.RUNNING -> "运行中"
                    DreamRunObserver.Status.SUCCEEDED -> {
                        if (runState.savedCount > 0) {
                            "已完成 · 新增 ${runState.savedCount} 条"
                        } else {
                            "已完成"
                        }
                    }
                    DreamRunObserver.Status.FAILED -> "失败"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = lastDreamRunLabel(lastSuccessAtMs, lastSuccessSavedCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onTriggerNow) {
                Text(stringResource(R.string.dream_loop_trigger_now))
            }
        }
    }
}

@Composable
private fun DreamLoopInterval.label(): String = stringResource(labelRes)

@Composable
private fun SettingsPagePicker(
    selectedPage: SettingsPage,
    onSelectedPageChanged: (SettingsPage) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SettingsPage.entries.forEach { page ->
            FilterChip(
                selected = selectedPage == page,
                onClick = { onSelectedPageChanged(page) },
                label = { Text(page.label) },
            )
        }
    }
}

@Composable
private fun SettingsGroupCard(content: @Composable () -> Unit) {
    ChatCardSurface {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun DividerSpacer() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
    )
}

@Composable
private fun ProviderPicker(
    provider: LlmProvider,
    onProviderChanged: (LlmProvider) -> Unit,
) {
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
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun SettingsToggleRow(
    title: String,
    detail: String,
    meta: String,
    metaIcon: ImageVector? = null,
    checked: Boolean,
    enabled: Boolean = true,
    locked: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
    statusDotColor: Color? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (metaIcon != null && meta.isNotBlank()) {
                    Icon(
                        imageVector = metaIcon,
                        contentDescription = meta,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp),
                    )
                }
                if (statusDotColor != null) {
                    Canvas(modifier = Modifier.size(8.dp)) {
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
        }
        if (locked) {
            Text(
                text = if (checked) "开" else "关",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun LocalQwenDownloadSection(
    state: LocalQwenDownloadUiState,
    onDownload: () -> Unit,
) {
    ChatCardSurface {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = state.modelName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            SettingsMetaRow(
                label = "状态",
                value = when {
                    state.isChecking -> "正在检查"
                    state.isDownloading -> "下载中 ${formatPercent(state.progress)}"
                    state.isInstalled -> "已安装"
                    state.error != null -> "下载失败"
                    else -> "未安装"
                },
            )
            if (state.isDownloading) {
                LinearProgressIndicator(
                    progress = { state.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = formatBytes(state.downloadedBytes, state.totalBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.isChecking) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AuraLoadingIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "正在确认模型文件完整性",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                enabled = !state.isDownloading && !state.isChecking,
            ) {
                Text(if (state.isInstalled) "重新下载" else "下载模型")
            }
        }
    }
}

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
            AuraLoadingIndicator(
                modifier = Modifier.size(16.dp),
                color = MaterialTheme.colorScheme.primary,
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

private fun ConnectivityResult?.summaryLabel(): String =
    when (this) {
        null -> "等待检查"
        is ConnectivityResult.Success -> "连接正常"
        is ConnectivityResult.AuthFailure -> "鉴权失败"
        is ConnectivityResult.Unreachable -> "不可达"
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
