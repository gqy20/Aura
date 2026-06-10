package com.xiaoqi.companion.feature.chat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.core.presence.PresenceUiState
import com.xiaoqi.companion.data.db.converter.LlmProvider
import com.xiaoqi.companion.data.repository.DefaultLlmValues
import kotlinx.coroutines.delay

@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ChatScreenContent(
        uiState = uiState,
        onSendMessage = { viewModel.sendMessage(uiState.inputText) },
        onInputTextChanged = { viewModel.updateInputText(it) },
        onClearError = { viewModel.clearError() },
        onOpenMemoryRoom = { viewModel.openMemoryRoom() },
        onCloseMemoryRoom = { viewModel.closeMemoryRoom() },
        onDeleteMemory = { viewModel.deleteMemory(it) },
        onOpenReminders = { viewModel.openReminders() },
        onCloseReminders = { viewModel.closeReminders() },
        onCancelReminder = { viewModel.cancelReminder(it) },
        onOpenSettings = { viewModel.openSettings() },
        onCloseSettings = { viewModel.closeSettings() },
        onOpenMcpSettings = { viewModel.openMcpSettings() },
        onCloseMcpSettings = { viewModel.closeMcpSettings() },
        onMcpNameChanged = { viewModel.updateMcpSettingsName(it) },
        onMcpUrlChanged = { viewModel.updateMcpSettingsUrl(it) },
        onSaveMcpSettings = { viewModel.saveMcpSettings() },
        onSettingsApiKeyChanged = { viewModel.updateSettingsApiKey(it) },
        onSettingsProviderChanged = { viewModel.updateSettingsProvider(it) },
        onSettingsModelNameChanged = { viewModel.updateSettingsModelName(it) },
        onSettingsBaseUrlChanged = { viewModel.updateSettingsBaseUrl(it) },
        onSaveSettings = { viewModel.saveSettings() },
        onDeviceStatusEnabledChanged = { viewModel.setDeviceStatusContextEnabled(it) },
        onLocationContextEnabledChanged = { viewModel.setLocationContextEnabled(it) },
        onWeatherContextEnabledChanged = { viewModel.setWeatherContextEnabled(it) },
        onReminderToolEnabledChanged = { viewModel.setReminderToolEnabled(it) },
        onNotificationEnabledChanged = { viewModel.setNotificationEnabled(it) },
        onOpenPermissionSettings = { prompt ->
            when (prompt.type) {
                ChatPermissionType.EXACT_ALARM -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        )
                    }
                }
            }
            viewModel.dismissPermissionPrompt()
        },
        onDismissPermissionPrompt = { viewModel.dismissPermissionPrompt() },
        onAttachImage = { viewModel.attachImage(it.toString()) },
        onRemoveImage = { viewModel.removePendingImage() },
        onPresenceTapped = { viewModel.onPresenceTapped() },
    )
}

@Composable
fun ChatScreenContent(
    uiState: ChatUiState,
    onSendMessage: () -> Unit,
    onInputTextChanged: (String) -> Unit,
    onClearError: () -> Unit,
    onOpenMemoryRoom: () -> Unit,
    onCloseMemoryRoom: () -> Unit,
    onDeleteMemory: (String) -> Unit,
    onOpenReminders: () -> Unit,
    onCloseReminders: () -> Unit,
    onCancelReminder: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit,
    onOpenMcpSettings: () -> Unit,
    onCloseMcpSettings: () -> Unit,
    onMcpNameChanged: (String) -> Unit,
    onMcpUrlChanged: (String) -> Unit,
    onSaveMcpSettings: () -> Unit,
    onSettingsApiKeyChanged: (String) -> Unit,
    onSettingsProviderChanged: (LlmProvider) -> Unit,
    onSettingsModelNameChanged: (String) -> Unit,
    onSettingsBaseUrlChanged: (String) -> Unit,
    onSaveSettings: () -> Unit,
    onDeviceStatusEnabledChanged: (Boolean) -> Unit,
    onLocationContextEnabledChanged: (Boolean) -> Unit,
    onWeatherContextEnabledChanged: (Boolean) -> Unit,
    onReminderToolEnabledChanged: (Boolean) -> Unit,
    onNotificationEnabledChanged: (Boolean) -> Unit,
    onOpenPermissionSettings: (ChatPermissionPrompt) -> Unit,
    onDismissPermissionPrompt: () -> Unit,
    onAttachImage: (Uri) -> Unit,
    onRemoveImage: () -> Unit,
    onPresenceTapped: () -> Unit,
) {
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let(onAttachImage)
    }
    val contextPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { }
    val messages = uiState.messages
    val lastContentLength = messages.lastOrNull()?.content?.length ?: 0

    LaunchedEffect(messages.size, lastContentLength) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            CompanionHeader(
                status = uiState.status,
                presence = uiState.presence,
                configStatus = uiState.configStatus,
                memories = uiState.memories,
                reminders = uiState.reminders,
                mcpLabel = uiState.toolCapabilitySettings.mcpDisplayLabel(),
                onOpenMemoryRoom = onOpenMemoryRoom,
                onOpenReminders = onOpenReminders,
                onOpenMcpSettings = onOpenMcpSettings,
                onOpenSettings = onOpenSettings,
                onPresenceTapped = onPresenceTapped,
            )
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyChatState()
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(messages, key = { it.id }) { message ->
                        MessageBubble(message = message)
                    }
                }
            }

            uiState.permissionPrompt?.let { prompt ->
                PermissionPromptCard(
                    prompt = prompt,
                    onOpenSettings = { onOpenPermissionSettings(prompt) },
                    onDismiss = onDismissPermissionPrompt,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            InputBar(
                inputText = uiState.inputText,
                onInputTextChanged = onInputTextChanged,
                onSendMessage = onSendMessage,
                pendingImage = uiState.pendingImage,
                isPreparingImage = uiState.isPreparingImage,
                onPickImage = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onRemoveImage = onRemoveImage,
                isLoading = uiState.isLoading && uiState.messages.none {
                    it.role == "ASSISTANT" && it.isStreaming && it.content.isNotBlank()
                },
                isConfigReady = uiState.configStatus.isReady,
                modifier = Modifier.imePadding(),
            )
        }
    }

    uiState.error?.let { error ->
        LaunchedEffect(error) {
            snackbarHostState.showSnackbar(error)
            onClearError()
        }
    }

    if (uiState.isMemoryRoomOpen) {
        MemoryRoomDialog(
            memories = uiState.memories,
            onDismiss = onCloseMemoryRoom,
            onDeleteMemory = onDeleteMemory,
        )
    }

    if (uiState.isRemindersOpen) {
        RemindersDialog(
            reminders = uiState.reminders,
            onDismiss = onCloseReminders,
            onCancelReminder = onCancelReminder,
        )
    }

    if (uiState.isSettingsOpen) {
        SettingsDialog(
            apiKey = uiState.settingsApiKey,
            provider = uiState.settingsProvider,
            modelName = uiState.settingsModelName,
            baseUrl = uiState.settingsBaseUrl,
            message = uiState.settingsMessage,
            toolSettings = uiState.toolCapabilitySettings,
            onApiKeyChanged = onSettingsApiKeyChanged,
            onProviderChanged = onSettingsProviderChanged,
            onModelNameChanged = onSettingsModelNameChanged,
            onBaseUrlChanged = onSettingsBaseUrlChanged,
            onSave = onSaveSettings,
            onDeviceStatusEnabledChanged = onDeviceStatusEnabledChanged,
            onLocationContextEnabledChanged = onLocationContextEnabledChanged,
            onWeatherContextEnabledChanged = onWeatherContextEnabledChanged,
            onReminderToolEnabledChanged = onReminderToolEnabledChanged,
            onNotificationEnabledChanged = onNotificationEnabledChanged,
            onRequestContextPermissions = {
                contextPermissionLauncher.launch(contextPermissions())
            },
            onDismiss = onCloseSettings,
        )
    }

    if (uiState.isMcpSettingsOpen) {
        McpSettingsDialog(
            mcpServerName = uiState.mcpSettingsName,
            mcpHttpUrl = uiState.mcpSettingsUrl,
            currentMcpServerName = uiState.toolCapabilitySettings.mcpServerName,
            currentMcpHttpUrl = uiState.toolCapabilitySettings.mcpHttpUrl,
            message = uiState.mcpSettingsMessage,
            onMcpServerNameChanged = onMcpNameChanged,
            onMcpHttpUrlChanged = onMcpUrlChanged,
            onSave = onSaveMcpSettings,
            onDismiss = onCloseMcpSettings,
        )
    }
}

@Composable
private fun EmptyChatState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(horizontal = 32.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.74f),
            shape = CircleShape,
            modifier = Modifier.size(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "A",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Text(
            text = "Aura is here",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Tell her what happened today, or send a picture.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CompanionHeader(
    status: CompanionStatus,
    presence: PresenceUiState,
    configStatus: ChatConfigStatus,
    memories: List<ChatMemory>,
    reminders: List<ChatReminder>,
    mcpLabel: String,
    onOpenMemoryRoom: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenMcpSettings: () -> Unit,
    onOpenSettings: () -> Unit,
    onPresenceTapped: () -> Unit,
) {
    val scheduledReminderCount = reminders.count { it.status == "SCHEDULED" }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 1.dp,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AuraPetAvatar(
                    presence = presence,
                    onClick = onPresenceTapped,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = "Aura",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "在线 · ${status.moodLabel()} · ${status.relationshipLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(
                    onClick = onOpenMemoryRoom,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier
                        .widthIn(max = 72.dp)
                        .semantics { contentDescription = "打开记忆房间" },
                ) {
                    Text(
                        text = "记忆 ${memories.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(
                    onClick = onOpenReminders,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier
                        .widthIn(max = 72.dp)
                        .semantics { contentDescription = "Open reminders" },
                ) {
                    Text(
                        text = "提醒 $scheduledReminderCount",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(
                    onClick = onOpenMcpSettings,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier
                        .widthIn(max = 72.dp)
                        .semantics { contentDescription = "Open MCP settings" },
                ) {
                    Text(
                        text = mcpLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.semantics { contentDescription = "打开设置" },
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (!configStatus.isReady) {
            ConfigStatusCard(status = configStatus, onOpenSettings = onOpenSettings)
        }
    }
}

private fun CompanionStatus.moodLabel(): String =
    when (mood.lowercase()) {
        "happy" -> "开心"
        "sad" -> "低落"
        "angry" -> "生气"
        "excited", "exited" -> "兴奋"
        "calm" -> "平静"
        "tired" -> "疲惫"
        "neutral" -> "平常"
        else -> mood
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
private fun ConfigStatusCard(
    status: ChatConfigStatus,
    onOpenSettings: () -> Unit,
) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.56f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "需要配置模型",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = status.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = status.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onOpenSettings) {
                    Text("设置")
                }
            }
        }
    }
}

@Composable
private fun PermissionPromptCard(
    prompt: ChatPermissionPrompt,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.64f),
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = prompt.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = prompt.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.78f),
                )
            }
            TextButton(onClick = onDismiss) {
                Text("Later")
            }
            Button(onClick = onOpenSettings) {
                Text(prompt.primaryActionLabel)
            }
        }
    }
}

@Composable
private fun SettingsDialog(
    apiKey: String,
    provider: LlmProvider,
    modelName: String,
    baseUrl: String,
    message: String?,
    toolSettings: ChatToolCapabilitySettings,
    onApiKeyChanged: (String) -> Unit,
    onProviderChanged: (LlmProvider) -> Unit,
    onModelNameChanged: (String) -> Unit,
    onBaseUrlChanged: (String) -> Unit,
    onSave: () -> Unit,
    onDeviceStatusEnabledChanged: (Boolean) -> Unit,
    onLocationContextEnabledChanged: (Boolean) -> Unit,
    onWeatherContextEnabledChanged: (Boolean) -> Unit,
    onReminderToolEnabledChanged: (Boolean) -> Unit,
    onNotificationEnabledChanged: (Boolean) -> Unit,
    onRequestContextPermissions: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f),
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "模型设置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "API Key 只保存在本机。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

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
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DefaultLlmValues.modelOptions(provider).forEach { option ->
                        FilterChip(
                            selected = modelName == option,
                            onClick = { onModelNameChanged(option) },
                            label = { Text(option) },
                        )
                    }
                }

                OutlinedTextField(
                    value = modelName,
                    onValueChange = onModelNameChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("模型名称") },
                    readOnly = true,
                    singleLine = true,
                )

                Text(
                    text = when (provider) {
                        LlmProvider.GLM -> "默认模型：${DefaultLlmValues.GLM_MODEL}"
                        LlmProvider.KIMI -> "默认模型：${DefaultLlmValues.KIMI_MODEL}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = "当前端点：${DefaultLlmValues.defaultBaseUrl(provider)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Base URL") },
                    readOnly = true,
                    singleLine = true,
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API Key") },
                    placeholder = { Text("留空则保留当前 Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )

                ToolCapabilitiesSection(
                    settings = toolSettings,
                    onDeviceStatusEnabledChanged = onDeviceStatusEnabledChanged,
                    onLocationContextEnabledChanged = onLocationContextEnabledChanged,
                    onWeatherContextEnabledChanged = onWeatherContextEnabledChanged,
                    onReminderToolEnabledChanged = onReminderToolEnabledChanged,
                    onNotificationEnabledChanged = onNotificationEnabledChanged,
                    onRequestContextPermissions = onRequestContextPermissions,
                )

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
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onSave) {
                        Text("保存")
                    }
                }
            }
        }
    }
}

@Composable
private fun McpSettingsDialog(
    mcpServerName: String,
    mcpHttpUrl: String,
    currentMcpServerName: String,
    currentMcpHttpUrl: String,
    message: String?,
    onMcpServerNameChanged: (String) -> Unit,
    onMcpHttpUrlChanged: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Remote MCP",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Connect Aura to one HTTP MCP endpoint. Tools are loaded when a new agent run starts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedTextField(
                    value = mcpServerName,
                    onValueChange = onMcpServerNameChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Server name") },
                    placeholder = { Text("e.g. Notes, Browser, Research") },
                    singleLine = true,
                )

                OutlinedTextField(
                    value = mcpHttpUrl,
                    onValueChange = onMcpHttpUrlChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("MCP HTTP URL") },
                    placeholder = { Text("https://example.com/mcp") },
                    singleLine = true,
                )

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            text = if (currentMcpHttpUrl.isBlank()) {
                                "No MCP server configured"
                            } else {
                                currentMcpServerName.ifBlank { "Active MCP server" }
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = currentMcpHttpUrl.ifBlank { "Save a URL to register remote MCP tools." },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Remote tool names appear as mcp__host__tool_name.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        )
                    }
                }

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
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onSave) {
                        Text("Save MCP")
                    }
                }
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
                    text = "上下文与工具",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "控制 Aura 能感知和执行什么",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onRequestContextPermissions) {
                Text("授权定位/通知")
            }
        }

        ToolCapabilityRow(
            title = "时间与最近互动",
            detail = "当前时间、日期，以及最近聊天概况",
            meta = "本地 · 始终可用",
            tools = "get_current_time · get_recent_interaction_context",
            enabled = true,
            locked = true,
            onEnabledChanged = {},
        )
        ToolCapabilityRow(
            title = "设备状态",
            detail = "电量、充电状态、网络状态和省电模式",
            meta = "本地读取",
            tools = "get_device_status",
            enabled = settings.deviceStatusEnabled,
            onEnabledChanged = onDeviceStatusEnabledChanged,
        )
        ToolCapabilityRow(
            title = "位置",
            detail = "使用最近一次定位作为本地上下文",
            meta = "需要系统定位权限",
            tools = "get_user_context_settings",
            enabled = settings.locationContextEnabled,
            onEnabledChanged = onLocationContextEnabledChanged,
        )
        ToolCapabilityRow(
            title = "天气",
            detail = "按城市或已授权的当前位置查询天气",
            meta = "网络查询 · 可关闭",
            tools = "get_weather",
            enabled = settings.weatherContextEnabled,
            onEnabledChanged = onWeatherContextEnabledChanged,
        )
        ToolCapabilityRow(
            title = "提醒",
            detail = "创建本地提醒通知",
            meta = "依赖通知权限",
            tools = "create_local_reminder",
            enabled = settings.reminderToolEnabled,
            onEnabledChanged = onReminderToolEnabledChanged,
        )
        ToolCapabilityRow(
            title = "Remote MCP",
            detail = settings.mcpHttpUrl.ifBlank { "No remote MCP server configured" },
            meta = "HTTP",
            tools = "mcp__*",
            enabled = settings.mcpHttpUrl.isNotBlank(),
            locked = true,
            onEnabledChanged = {},
        )
        ToolCapabilityRow(
            title = "通知开关",
            detail = "允许 Aura 发送本地提醒通知",
            meta = "系统权限 + 应用开关",
            tools = "Android 通知权限",
            enabled = settings.notificationEnabled,
            onEnabledChanged = onNotificationEnabledChanged,
        )
        ToolCapabilityRow(
            title = "记忆、情绪与关系",
            detail = "回复完成后整理记忆与陪伴状态",
            meta = "本地 · 始终可用",
            tools = "search_memory · search_records · search_summaries · post-response reflection",
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
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f),
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
                    maxLines = 1,
                )
                Text(
                    text = "工具：$tools",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    maxLines = 1,
                )
            }
            if (locked) {
                CapabilityMetaPill(text = "开启")
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
private fun CapabilityMetaPill(text: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
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

@Composable
private fun MemoryRoomDialog(
    memories: List<ChatMemory>,
    onDismiss: () -> Unit,
    onDeleteMemory: (String) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.72f),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "记忆房间",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .semantics { contentDescription = "Close memory room" },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                MemoryRoomStats(memories = memories)

                if (memories.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No long-term memories yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(memories, key = { it.id }) { memory ->
                            MemoryRoomItemCard(
                                memory = memory,
                                onDelete = { onDeleteMemory(memory.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryRoomStats(memories: List<ChatMemory>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MemoryStatPill(label = "All", value = memories.size.toString())
        MemoryStatPill(label = "Facts", value = memories.count { it.type == "FACT" }.toString())
        MemoryStatPill(label = "Moments", value = memories.count { it.type == "EPISODE" }.toString())
        MemoryStatPill(label = "Habits", value = memories.count { it.type == "PROCEDURAL" }.toString())
    }
}

@Composable
private fun MemoryStatPill(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RemindersDialog(
    reminders: List<ChatReminder>,
    onDismiss: () -> Unit,
    onCancelReminder: (String) -> Unit,
) {
    val scheduledReminderCount = reminders.count { it.status == "SCHEDULED" }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.72f),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "提醒",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "$scheduledReminderCount scheduled · ${reminders.size} total",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text("关闭")
                    }
                }

                if (reminders.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No reminders yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(reminders, key = { it.id }) { reminder ->
                            ReminderItemCard(
                                reminder = reminder,
                                onCancel = { onCancelReminder(reminder.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderItemCard(
    reminder: ChatReminder,
    onCancel: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CapabilityMetaPill(text = if (reminder.exact) "Exact" else "Flexible")
                    CapabilityMetaPill(text = reminder.status.lowercase())
                }
                Text(
                    text = reminder.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = reminder.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "At ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(reminder.triggerAtMillis))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                )
            }
            if (reminder.status == "SCHEDULED") {
                TextButton(onClick = onCancel) {
                    Text("取消")
                }
            }
        }
    }
}

@Composable
private fun MemoryRoomItem(memory: ChatMemory) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "${memory.type} · 重要度 ${String.format("%.2f", memory.importance)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = memory.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun MemoryRoomItemCard(
    memory: ChatMemory,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(13.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CapabilityMetaPill(text = memory.type.memoryTypeLabel())
                    Text(
                        text = memory.source.memorySourceLabel(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = memory.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Importance ${(memory.importance * 100).toInt().coerceIn(0, 100)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f),
            ) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(32.dp)
                        .semantics { contentDescription = "Delete memory" },
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

private fun String.memoryTypeLabel(): String =
    when (this) {
        "FACT" -> "About you"
        "EPISODE" -> "Moment"
        "PROCEDURAL" -> "Habit"
        else -> lowercase()
    }

private fun String.memorySourceLabel(): String =
    when {
        isBlank() -> "Saved by Aura"
        startsWith("tool:") -> "Saved by Aura"
        else -> this
    }

private fun ChatToolCapabilitySettings.mcpDisplayLabel(): String =
    when {
        mcpHttpUrl.isBlank() -> "MCP"
        mcpServerName.isNotBlank() -> mcpServerName
        else -> "MCP on"
    }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun InputBar(
    inputText: String,
    onInputTextChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    pendingImage: ChatImageAttachment?,
    isPreparingImage: Boolean,
    onPickImage: () -> Unit,
    onRemoveImage: () -> Unit,
    isLoading: Boolean = false,
    isConfigReady: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val canSend = (inputText.isNotBlank() || pendingImage != null) &&
        isConfigReady &&
        !isLoading &&
        !isPreparingImage
    val context = LocalContext.current
    val inputView = LocalView.current
    var imeStuck by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            pendingImage?.let {
                PendingImagePreview(
                    imageUri = it.uriString,
                    onRemoveImage = onRemoveImage,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedIconButton(
                    onClick = onPickImage,
                    enabled = !isLoading && !isPreparingImage,
                    modifier = Modifier.semantics { contentDescription = "Attach image" },
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                    )
                }
                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputTextChanged,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Talk to Aura") },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Send,
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (canSend) onSendMessage()
                        },
                    ),
                    shape = RoundedCornerShape(22.dp),
                )
                if (isLoading || isPreparingImage) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp).padding(4.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    IconButton(
                        onClick = onSendMessage,
                        enabled = canSend,
                        modifier = Modifier.semantics { contentDescription = "Send" },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                        )
                    }
                }
            }
            ImeRecoveryHint(
                visible = imeStuck,
                onSwitchInputMethod = { context.showInputMethodPicker() },
            )
        }
    }

    LaunchedEffect(inputView) {
        while (true) {
            delay(IME_STUCK_CHECK_INTERVAL_MS)
            val state = inputView.currentImeSnapshot()
            val stuck = state.isWeChatInputMethod &&
                state.bottomInset in 1 until (inputView.height * MIN_USEFUL_IME_HEIGHT_RATIO).toInt()
            if (stuck != imeStuck) {
                imeStuck = stuck
            }
            if (stuck) {
                AppLogger.warn(
                    LogTags.Chat,
                    "ime_stuck_small_panel",
                    "defaultIme" to state.defaultIme,
                    "imeBottom" to state.bottomInset,
                    "viewHeight" to inputView.height,
                    "hasWindowFocus" to inputView.hasWindowFocus(),
                    "hasViewFocus" to inputView.hasFocus(),
                )
            }
        }
    }
}

@Composable
private fun ImeRecoveryHint(
    visible: Boolean,
    onSwitchInputMethod: () -> Unit,
) {
    if (!visible) {
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onSwitchInputMethod) {
            Text("切换输入法")
        }
    }
}

private fun View.currentImeSnapshot(): ImeSnapshot {
    val defaultIme = runCatching {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
    }.getOrNull()
    val bottomInset = ViewCompat.getRootWindowInsets(this)
        ?.getInsets(WindowInsetsCompat.Type.ime())
        ?.bottom ?: 0
    return ImeSnapshot(
        defaultIme = defaultIme,
        bottomInset = bottomInset,
    )
}

private fun Context.showInputMethodPicker() {
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    imm?.showInputMethodPicker()
}

private data class ImeSnapshot(
    val defaultIme: String?,
    val bottomInset: Int,
) {
    val isWeChatInputMethod: Boolean =
        defaultIme?.startsWith("com.tencent.wetype/") == true
}

private const val MIN_USEFUL_IME_HEIGHT_RATIO = 0.12f
private const val IME_STUCK_CHECK_INTERVAL_MS = 350L

@Composable
private fun PendingImagePreview(
    imageUri: String,
    onRemoveImage: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.64f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = imageUri,
                contentDescription = "Selected image",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .height(78.dp)
                    .width(96.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Ready to share",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Add a thought, or send it as-is.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            IconButton(
                onClick = onRemoveImage,
                modifier = Modifier.semantics { contentDescription = "Remove image" },
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
