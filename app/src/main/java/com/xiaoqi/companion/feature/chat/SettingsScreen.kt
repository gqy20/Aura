package com.xiaoqi.companion.feature.chat

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.xiaoqi.companion.core.presence.runtime.DreamLoopInterval
import kotlinx.coroutines.launch
import com.xiaoqi.companion.data.db.converter.LlmProvider
import com.xiaoqi.companion.data.repository.DefaultLlmValues
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onOpenMcpSettings: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dreamLoopInterval by viewModel.dreamLoopInterval.collectAsStateWithLifecycle()
    val healthSyncState by viewModel.healthSyncState.collectAsStateWithLifecycle()
    val healthAutoSyncEnabled by viewModel.healthAutoSyncEnabled.collectAsStateWithLifecycle()
    val healthLastSyncAt by viewModel.healthLastSyncAt.collectAsStateWithLifecycle()
    val contextPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    SettingsScreenContent(
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
        healthSyncState = healthSyncState,
        healthAutoSyncEnabled = healthAutoSyncEnabled,
        healthLastSyncAt = healthLastSyncAt,
        viewModel = viewModel,
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
        onRequestContextPermissions = {
            contextPermissionLauncher.launch(contextPermissions())
        },
        onHealthAutoSyncEnabledChanged = viewModel::setHealthAutoSyncEnabled,
        onHealthSyncNow = viewModel::triggerHealthSyncNow,
        onOpenMcpSettings = onOpenMcpSettings,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreenContent(
    viewModel: ChatViewModel,
    apiKey: String,
    provider: LlmProvider,
    modelName: String,
    baseUrl: String,
    message: String?,
    localQwenDownload: LocalQwenDownloadUiState,
    toolSettings: ChatToolCapabilitySettings,
    connectivityResult: ConnectivityResult?,
    isCheckingConnectivity: Boolean,
    dreamLoopInterval: DreamLoopInterval,
    healthSyncState: com.xiaoqi.companion.data.source.HealthSyncManager.SyncState,
    healthAutoSyncEnabled: Boolean,
    healthLastSyncAt: Long,
    onApiKeyChanged: (String) -> Unit,
    onProviderChanged: (LlmProvider) -> Unit,
    onModelNameChanged: (String) -> Unit,
    onBaseUrlChanged: (String) -> Unit,
    onSave: () -> Unit,
    onTestConnection: () -> Unit,
    onDownloadLocalQwenModel: () -> Unit,
    onDeviceStatusEnabledChanged: (Boolean) -> Unit,
    onLocationContextEnabledChanged: (Boolean) -> Unit,
    onWeatherContextEnabledChanged: (Boolean) -> Unit,
    onReminderToolEnabledChanged: (Boolean) -> Unit,
    onNotificationEnabledChanged: (Boolean) -> Unit,
    onDreamLoopIntervalChanged: (DreamLoopInterval) -> Unit,
    onTriggerDreamLoopNow: () -> Unit,
    onRequestContextPermissions: () -> Unit,
    onHealthAutoSyncEnabledChanged: (Boolean) -> Unit,
    onHealthSyncNow: () -> Unit,
    onOpenMcpSettings: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenMcpSettings) {
                        Icon(Icons.Default.Build, contentDescription = "MCP 设置")
                    }
                    // 保存按钮提到 TopAppBar — 之前放在 LazyColumn 末尾会被数据透明 Section
                    // 推到屏外,用户根本点不到,导致 api_key 等字段没真正写进 DataStore。
                    androidx.compose.material3.TextButton(onClick = onSave) {
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
                    title = "模型",
                    subtitle = "对话引擎",
                )
            }
            item {
                ProviderPicker(
                    provider = provider,
                    onProviderChanged = onProviderChanged,
                )
            }
            item {
                ModelPicker(
                    provider = provider,
                    modelName = modelName,
                    onModelNameChanged = onModelNameChanged,
                )
            }
            if (provider == LlmProvider.LOCAL_QWEN) {
                item {
                    SettingsSectionTitle(
                        title = "本地模型",
                        subtitle = "离线回复",
                    )
                }
                item {
                    LocalQwenDownloadSection(
                        state = localQwenDownload,
                        onDownload = onDownloadLocalQwenModel,
                    )
                }
            } else {
                item {
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = onBaseUrlChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("接口地址") },
                        readOnly = true,
                        singleLine = true,
                    )
                }
                item {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = onApiKeyChanged,
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
                        result = connectivityResult,
                        isChecking = isCheckingConnectivity,
                        onTest = onTestConnection,
                    )
                }
            }
            item {
                ToolCapabilitiesSection(
                    settings = toolSettings,
                    onDeviceStatusEnabledChanged = onDeviceStatusEnabledChanged,
                    onLocationContextEnabledChanged = onLocationContextEnabledChanged,
                    onWeatherContextEnabledChanged = onWeatherContextEnabledChanged,
                    onReminderToolEnabledChanged = onReminderToolEnabledChanged,
                    onNotificationEnabledChanged = onNotificationEnabledChanged,
                    onRequestContextPermissions = onRequestContextPermissions,
                )
            }
            item {
                DreamLoopSection(
                    current = dreamLoopInterval,
                    onIntervalChanged = onDreamLoopIntervalChanged,
                    onTriggerNow = onTriggerDreamLoopNow,
                )
            }
            item {
                HealthDataSection(
                    syncState = healthSyncState,
                    autoSyncEnabled = healthAutoSyncEnabled,
                    lastSyncAtMillis = healthLastSyncAt,
                    onAutoSyncEnabledChanged = onHealthAutoSyncEnabledChanged,
                    onSyncNow = onHealthSyncNow,
                    healthConnectDataSource = viewModel.healthConnectDataSource,
                    sensorSource = viewModel.sensorHealthSource,
                )
            }
            item {
                DataTransparencySection(viewModel = viewModel)
            }
            item {
                message?.let {
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            label = { Text("Local Qwen") },
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
}

@Composable
private fun LocalQwenDownloadSection(
    state: LocalQwenDownloadUiState,
    onDownload: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = Color(0xFFF7F2EA),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val status = when {
                state.isDownloading -> "下载中 ${formatPercent(state.progress)}"
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
                Text(
                    text = formatBytes(state.downloadedBytes, state.totalBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            tools = "",
            enabled = settings.deviceStatusEnabled,
            onEnabledChanged = onDeviceStatusEnabledChanged,
        )
        ToolCapabilityRow(
            title = "位置",
            detail = "上次授权位置",
            meta = "需授权",
            tools = "",
            enabled = settings.locationContextEnabled,
            onEnabledChanged = onLocationContextEnabledChanged,
        )
        ToolCapabilityRow(
            title = "天气",
            detail = "当前天气",
            meta = "需联网",
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
            tools = "",
            enabled = settings.mcpServers.any { it.isReady },
            locked = true,
            onEnabledChanged = {},
        )
        ToolCapabilityRow(
            title = "通知",
            detail = "提醒推送",
            meta = "需通知",
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
    tools: String,
    enabled: Boolean,
    locked: Boolean = false,
    onEnabledChanged: (Boolean) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = Color(0xFFF7F2EA),
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
                    CapabilityMetaPill(text = meta)
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
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = Color(0xFFF7F2EA),
            modifier = Modifier.fillMaxWidth(),
        ) {
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
                        label = { Text(stringResource(R.string.dream_loop_section_title)) },
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
                    enabled = current.isEnabled,
                ) {
                    Text(stringResource(R.string.dream_loop_trigger_now))
                }
            }
        }
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
        removePrefix("Qwen3.5-").removeSuffix("-MNN")
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
            "成功 · 延迟 ${result.latencyMs} ms" to Color(0xFF2E7D32)
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
                android.util.Log.e("DataTransparency", "export failed", e)
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
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = Color(0xFFF7F2EA),
            modifier = Modifier.fillMaxWidth(),
        ) {
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
                Spacer(Modifier.height(4.dp))
                androidx.compose.material3.Text(
                    text = "清空（需二次确认）",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
                ClearButton("清空洞察", ClearTarget.Insights) { pendingClear = it }
                ClearButton("清空情绪快照", ClearTarget.MoodSnapshots) { pendingClear = it }
                ClearButton("清空记忆", ClearTarget.Memories) { pendingClear = it }
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
                        // 同步本地 count
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
    onClick: (ClearTarget) -> Unit,
) {
    androidx.compose.material3.TextButton(
        onClick = { onClick(target) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

private enum class ClearTarget(val label: String) {
    Insights("洞察"),
    MoodSnapshots("情绪快照"),
    Memories("记忆"),
}
