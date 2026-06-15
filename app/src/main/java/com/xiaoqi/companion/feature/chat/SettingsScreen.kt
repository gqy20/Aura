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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaoqi.companion.core.llm.ConnectivityResult
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
        onRequestContextPermissions = {
            contextPermissionLauncher.launch(contextPermissions())
        },
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
    onRequestContextPermissions: () -> Unit,
    onOpenMcpSettings: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenMcpSettings) {
                        Icon(Icons.Default.Build, contentDescription = "MCP settings")
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
                    title = "Model",
                    subtitle = "Reply engine",
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
                        title = "Local model",
                        subtitle = "Offline replies",
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
                        label = { Text("Endpoint") },
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
                        placeholder = { Text("Keep current key") },
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onBack) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onSave) {
                        Text("Save")
                    }
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
private fun SettingsSectionTitle(
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
                state.isDownloading -> "Downloading ${formatPercent(state.progress)}"
                state.isInstalled -> "Installed"
                state.error != null -> "Download failed"
                else -> "Not installed"
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
                Text(if (state.isInstalled) "Retry" else "Download")
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
                    text = "Tools",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Context and actions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onRequestContextPermissions) {
                Text("Allow")
            }
        }
        ToolCapabilityRow(
            title = "Context",
            detail = "Time and recent chat.",
            meta = "On",
            tools = "",
            enabled = true,
            locked = true,
            onEnabledChanged = {},
        )
        ToolCapabilityRow(
            title = "Device",
            detail = "Battery and network.",
            meta = "Local",
            tools = "",
            enabled = settings.deviceStatusEnabled,
            onEnabledChanged = onDeviceStatusEnabledChanged,
        )
        ToolCapabilityRow(
            title = "Location",
            detail = "Last granted location.",
            meta = "Permission",
            tools = "",
            enabled = settings.locationContextEnabled,
            onEnabledChanged = onLocationContextEnabledChanged,
        )
        ToolCapabilityRow(
            title = "Weather",
            detail = "Current weather.",
            meta = "Network",
            tools = "",
            enabled = settings.weatherContextEnabled,
            onEnabledChanged = onWeatherContextEnabledChanged,
        )
        ToolCapabilityRow(
            title = "Reminders",
            detail = "Local reminders.",
            meta = "Notify",
            tools = "",
            enabled = settings.reminderToolEnabled,
            onEnabledChanged = onReminderToolEnabledChanged,
        )
        ToolCapabilityRow(
            title = "MCP",
            detail = settings.mcpHttpUrl.ifBlank { "Not connected." },
            meta = "Advanced",
            tools = "",
            enabled = settings.mcpHttpUrl.isNotBlank(),
            locked = true,
            onEnabledChanged = {},
        )
        ToolCapabilityRow(
            title = "Notifications",
            detail = "Reminder alerts.",
            meta = "App",
            tools = "",
            enabled = settings.notificationEnabled,
            onEnabledChanged = onNotificationEnabledChanged,
        )
        ToolCapabilityRow(
            title = "Memory",
            detail = "Reflection after replies.",
            meta = "On",
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
                CapabilityMetaPill(text = "On")
            } else {
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChanged,
                )
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
            Text("Test connection")
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
            "OK · ${result.latencyMs}ms" to Color(0xFF2E7D32)
        }
        is ConnectivityResult.AuthFailure -> {
            "鉴权失败 (${result.statusCode})" to MaterialTheme.colorScheme.error
        }
        is ConnectivityResult.Unreachable -> {
            "不可达: ${result.cause}" to MaterialTheme.colorScheme.error
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
            subtitle = "本地存储 · 你随时可查可改可删",
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
                CountRow("insights", insightCount)
                CountRow("mood_snapshots", moodCount)
                CountRow("memories", memoryCount)
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
                    text = "清空(二次确认)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
                ClearButton("清空 insights", ClearTarget.Insights) { pendingClear = it }
                ClearButton("清空 mood_snapshots", ClearTarget.MoodSnapshots) { pendingClear = it }
                ClearButton("清空 memories", ClearTarget.Memories) { pendingClear = it }
            }
        }
    }

    pendingClear?.let { target ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingClear = null },
            title = { Text("确认清空?") },
            text = {
                Text(
                    "将永久删除所有 ${target.label} 数据,无法恢复。",
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
                ) { Text("确认清空") }
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
    Insights("insights"),
    MoodSnapshots("mood_snapshots"),
    Memories("memories"),
}
