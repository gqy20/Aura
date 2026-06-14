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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
        onApiKeyChanged = viewModel::updateSettingsApiKey,
        onProviderChanged = viewModel::updateSettingsProvider,
        onModelNameChanged = viewModel::updateSettingsModelName,
        onBaseUrlChanged = viewModel::updateSettingsBaseUrl,
        onSave = viewModel::saveSettings,
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
    apiKey: String,
    provider: LlmProvider,
    modelName: String,
    baseUrl: String,
    message: String?,
    localQwenDownload: LocalQwenDownloadUiState,
    toolSettings: ChatToolCapabilitySettings,
    onApiKeyChanged: (String) -> Unit,
    onProviderChanged: (LlmProvider) -> Unit,
    onModelNameChanged: (String) -> Unit,
    onBaseUrlChanged: (String) -> Unit,
    onSave: () -> Unit,
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
                title = { Text("Aura settings") },
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
                    subtitle = "Choose the reply engine Aura uses for chat.",
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
                        subtitle = "Download the MNN model before using local Qwen.",
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
                        label = { Text("Base URL") },
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
                        placeholder = { Text("Leave blank to keep current key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
        OutlinedTextField(
            value = modelName,
            onValueChange = onModelNameChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Model name") },
            readOnly = true,
            singleLine = true,
        )
        Text(
            text = "Default: ${DefaultLlmValues.defaultModel(provider)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsSectionTitle(
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
                text = "${state.modelName} - $status",
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
                Text(if (state.isInstalled) "Download again" else "Download model")
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
                    text = "Tool capabilities",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Control the local context and device actions Aura can use.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onRequestContextPermissions) {
                Text("Permissions")
            }
        }
        ToolCapabilityRow(
            title = "Time and recent context",
            detail = "Current time, date, and recent conversation summary.",
            meta = "Always on",
            tools = "get_current_time - get_recent_interaction_context",
            enabled = true,
            locked = true,
            onEnabledChanged = {},
        )
        ToolCapabilityRow(
            title = "Device status",
            detail = "Battery, charging, network, and power saving state.",
            meta = "Local",
            tools = "get_device_status",
            enabled = settings.deviceStatusEnabled,
            onEnabledChanged = onDeviceStatusEnabledChanged,
        )
        ToolCapabilityRow(
            title = "Location",
            detail = "Use the last granted location as local context.",
            meta = "Permission",
            tools = "get_user_context_settings",
            enabled = settings.locationContextEnabled,
            onEnabledChanged = onLocationContextEnabledChanged,
        )
        ToolCapabilityRow(
            title = "Weather",
            detail = "Query weather by city or granted location.",
            meta = "Network",
            tools = "get_weather",
            enabled = settings.weatherContextEnabled,
            onEnabledChanged = onWeatherContextEnabledChanged,
        )
        ToolCapabilityRow(
            title = "Reminders",
            detail = "Create local reminder notifications.",
            meta = "Notification",
            tools = "create_local_reminder",
            enabled = settings.reminderToolEnabled,
            onEnabledChanged = onReminderToolEnabledChanged,
        )
        ToolCapabilityRow(
            title = "Remote MCP tools",
            detail = settings.mcpHttpUrl.ifBlank { "No remote tool server connected." },
            meta = "Advanced",
            tools = "mcp__*",
            enabled = settings.mcpHttpUrl.isNotBlank(),
            locked = true,
            onEnabledChanged = {},
        )
        ToolCapabilityRow(
            title = "Notifications",
            detail = "Allow Aura to send local reminder notifications.",
            meta = "App switch",
            tools = "Android notification permission",
            enabled = settings.notificationEnabled,
            onEnabledChanged = onNotificationEnabledChanged,
        )
        ToolCapabilityRow(
            title = "Memory and relationship",
            detail = "Reflection, mood, and relationship updates after replies.",
            meta = "Always on",
            tools = "memory and reflection",
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
