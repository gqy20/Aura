package com.xiaoqi.companion.feature.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.xiaoqi.companion.core.presence.PresenceUiState
import com.xiaoqi.companion.data.db.converter.LlmProvider

@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ChatScreenContent(
        uiState = uiState,
        onSendMessage = { viewModel.sendMessage(uiState.inputText) },
        onInputTextChanged = { viewModel.updateInputText(it) },
        onClearError = { viewModel.clearError() },
        onOpenMemoryRoom = { viewModel.openMemoryRoom() },
        onCloseMemoryRoom = { viewModel.closeMemoryRoom() },
        onOpenSettings = { viewModel.openSettings() },
        onCloseSettings = { viewModel.closeSettings() },
        onSettingsApiKeyChanged = { viewModel.updateSettingsApiKey(it) },
        onSettingsProviderChanged = { viewModel.updateSettingsProvider(it) },
        onSettingsModelNameChanged = { viewModel.updateSettingsModelName(it) },
        onSaveSettings = { viewModel.saveSettings() },
        onAttachImage = { viewModel.attachImage(it.toString()) },
        onRemoveImage = { viewModel.removePendingImage() },
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
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit,
    onSettingsApiKeyChanged: (String) -> Unit,
    onSettingsProviderChanged: (LlmProvider) -> Unit,
    onSettingsModelNameChanged: (String) -> Unit,
    onSaveSettings: () -> Unit,
    onAttachImage: (Uri) -> Unit,
    onRemoveImage: () -> Unit,
) {
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let(onAttachImage)
    }
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
                onOpenMemoryRoom = onOpenMemoryRoom,
                onOpenSettings = onOpenSettings,
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

            ToolActivityLine(toolCalls = uiState.toolCalls)
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
                isLoading = uiState.isLoading && uiState.messages.none { it.role == "ASSISTANT" && it.isStreaming },
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
        )
    }

    if (uiState.isSettingsOpen) {
        SettingsDialog(
            apiKey = uiState.settingsApiKey,
            provider = uiState.settingsProvider,
            modelName = uiState.settingsModelName,
            message = uiState.settingsMessage,
            onApiKeyChanged = onSettingsApiKeyChanged,
            onProviderChanged = onSettingsProviderChanged,
            onModelNameChanged = onSettingsModelNameChanged,
            onSave = onSaveSettings,
            onDismiss = onCloseSettings,
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
    onOpenMemoryRoom: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 1.dp,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PresenceAvatar(presence = presence)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Aura",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${presence.label} · ${status.mood} · ${status.relationshipLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onOpenMemoryRoom,
                    modifier = Modifier.semantics { contentDescription = "Open memories" },
                ) {
                    Text(
                        text = memories.size.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.semantics { contentDescription = "Open settings" },
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
                    text = "Model setup needed",
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
private fun SettingsDialog(
    apiKey: String,
    provider: LlmProvider,
    modelName: String,
    message: String?,
    onApiKeyChanged: (String) -> Unit,
    onProviderChanged: (LlmProvider) -> Unit,
    onModelNameChanged: (String) -> Unit,
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
                        text = "模型设置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "API keys stay on this device.",
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

                OutlinedTextField(
                    value = modelName,
                    onValueChange = onModelNameChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("模型名称") },
                    singleLine = true,
                )

                Text(
                    text = when (provider) {
                        LlmProvider.GLM -> "Default model: glm-5v-turbo."
                        LlmProvider.KIMI -> "Uses the Moonshot-compatible endpoint."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API Key") },
                    placeholder = { Text("留空则保留当前 Key") },
                    singleLine = true,
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
private fun MemoryRoomDialog(
    memories: List<ChatMemory>,
    onDismiss: () -> Unit,
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
                            text = "记忆房间",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Long-term things Aura remembers",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text("关闭")
                    }
                }

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
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(memories, key = { it.id }) { memory ->
                            MemoryRoomItem(memory = memory)
                        }
                    }
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
private fun ToolActivityLine(toolCalls: List<ChatToolCall>) {
    if (toolCalls.isEmpty()) return
    val latestCall = toolCalls.last()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f),
            shape = RoundedCornerShape(999.dp),
        ) {
            Text(
                text = latestCall.label,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

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
                        enabled = (inputText.isNotBlank() || pendingImage != null) && isConfigReady,
                        modifier = Modifier.semantics { contentDescription = "Send" },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingImagePreview(
    imageUri: String,
    onRemoveImage: () -> Unit,
) {
    Box(
        modifier = Modifier
            .heightIn(max = 148.dp)
            .widthIn(max = 190.dp),
    ) {
        AsyncImage(
            model = imageUri,
            contentDescription = "Selected image",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .height(132.dp)
                .width(176.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        IconButton(
            onClick = onRemoveImage,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .semantics { contentDescription = "Remove image" },
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
