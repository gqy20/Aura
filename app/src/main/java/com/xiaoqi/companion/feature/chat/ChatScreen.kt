package com.xiaoqi.companion.feature.chat

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.core.presence.PresenceUiState
import com.xiaoqi.companion.core.presence.PresenceMode
import com.xiaoqi.companion.ui.theme.CompanionTheme
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenMemoryRoom: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMcpSettings: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ChatScreenContent(
        uiState = uiState,
        onSendMessage = { viewModel.sendMessage(uiState.inputText) },
        onInputTextChanged = { viewModel.updateInputText(it) },
        onClearError = { viewModel.clearError() },
        onOpenMemoryRoom = onOpenMemoryRoom,
        onCancelReminder = { viewModel.cancelReminder(it) },
        onOpenSettings = onOpenSettings,
        onOpenMcpSettings = onOpenMcpSettings,
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
    onCancelReminder: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMcpSettings: () -> Unit,
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
    var isRemindersOpen by remember { mutableStateOf(false) }
    val messages = uiState.messages
    val lastContentLength = messages.lastOrNull()?.content?.length ?: 0
    var hasCompletedInitialScroll by remember { mutableStateOf(false) }

    LaunchedEffect(messages.size, lastContentLength) {
        if (messages.isNotEmpty()) {
            val latestIndex = messages.lastIndex
            if (!hasCompletedInitialScroll) {
                listState.scrollToItem(latestIndex)
                hasCompletedInitialScroll = true
            } else {
                val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: latestIndex
                val isNearLatest = lastVisibleIndex >= latestIndex - 1
                if (isNearLatest) {
                    if (messages.lastOrNull()?.isStreaming == true) {
                        listState.scrollToItem(latestIndex)
                    } else {
                        listState.animateScrollToItem(latestIndex)
                    }
                }
            }
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
                onOpenReminders = { isRemindersOpen = true },
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

    if (isRemindersOpen) {
        RemindersDialog(
            reminders = uiState.reminders,
            onDismiss = { isRemindersOpen = false },
            onCancelReminder = onCancelReminder,
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
            .padding(horizontal = 18.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AuraPetAvatar(
                presence = presence,
                size = 42.dp,
                onClick = onPresenceTapped,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Text(
                    text = "Aura",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF496B5E),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "奥拉在听 · ${status.moodLabel()} · ${status.relationshipLabel}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 51.dp),
        ) {
            HeaderActionText(
                text = "记忆 ${memories.size}",
                onClick = onOpenMemoryRoom,
                contentDescription = "打开记忆房间",
            )
            HeaderDot()
            HeaderActionText(
                text = "提醒 $scheduledReminderCount",
                onClick = onOpenReminders,
                contentDescription = "打开提醒",
            )
            HeaderDot()
            HeaderActionText(
                text = mcpLabel,
                onClick = onOpenMcpSettings,
                contentDescription = "打开 MCP 设置",
            )
        }

        if (!configStatus.isReady) {
            ConfigStatusCard(status = configStatus, onOpenSettings = onOpenSettings)
        }
    }
}

@Composable
private fun HeaderDot() {
    Text(
        text = "·",
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.34f),
        style = MaterialTheme.typography.labelSmall,
    )
}

@Composable
private fun HeaderActionText(
    text: String,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = Color(0xFF496B5E).copy(alpha = 0.82f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .padding(horizontal = 2.dp, vertical = 3.dp),
    )
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

@Composable
private fun AuraDialogPanel(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    fillHeight: Float? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        val panelModifier = fillHeight?.let {
            modifier
                .fillMaxWidth()
                .fillMaxHeight(it)
        } ?: modifier.fillMaxWidth()

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFFFFFCF6),
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
            modifier = panelModifier,
        ) {
            Column(
                modifier = contentModifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun AuraDialogHeader(
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Surface(
            shape = CircleShape,
            color = Color(0xFFF1EADB),
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(38.dp)
                    .semantics { contentDescription = "关闭" },
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
}

@Composable
private fun AuraEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFFFFF8EA),
            modifier = Modifier.size(52.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "A",
                    color = Color(0xFF496B5E),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
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
private fun RemindersDialog(
    reminders: List<ChatReminder>,
    onDismiss: () -> Unit,
    onCancelReminder: (String) -> Unit,
) {
    val scheduledReminderCount = reminders.count { it.status == "SCHEDULED" }

    AuraDialogPanel(
        onDismiss = onDismiss,
        fillHeight = 0.72f,
    ) {
                AuraDialogHeader(
                    title = "奥拉帮你记着",
                    subtitle = "$scheduledReminderCount 个待提醒 · ${reminders.size} 条记录",
                    onDismiss = onDismiss,
                )

                if (reminders.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        AuraEmptyState(
                            title = "还没有提醒",
                            message = "你可以直接说：三分钟后提醒我喝咖啡。奥拉会把它记下来。",
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

@Composable
private fun ReminderItemCard(
    reminder: ChatReminder,
    onCancel: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF7F2EA),
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
                    CapabilityMetaPill(text = if (reminder.exact) "准时提醒" else "灵活提醒")
                    CapabilityMetaPill(text = reminder.status.reminderStatusLabel())
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
                    text = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(reminder.triggerAtMillis)),
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

private fun String.reminderStatusLabel(): String =
    when (uppercase(Locale.US)) {
        "SCHEDULED" -> "待提醒"
        "CANCELLED" -> "已取消"
        "FIRED" -> "已提醒"
        else -> lowercase(Locale.US)
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
        color = Color(0xFFF7F2EA).copy(alpha = 0.96f),
        tonalElevation = 0.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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
                Surface(
                    color = Color(0xFFFFF8EA),
                    shape = CircleShape,
                    shadowElevation = 1.dp,
                ) {
                    IconButton(
                        onClick = onPickImage,
                        enabled = !isLoading && !isPreparingImage,
                        modifier = Modifier.semantics { contentDescription = "添加图片" },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = Color(0xFF496B5E),
                        )
                    }
                }
                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputTextChanged,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp),
                    placeholder = {
                        Text(
                            text = "想和奥拉说什么？",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Send,
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (canSend) onSendMessage()
                        },
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = RoundedCornerShape(20.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFFFFCF6),
                        unfocusedContainerColor = Color(0xFFFFF8EA),
                        disabledContainerColor = Color(0xFFEDE4D3),
                        focusedIndicatorColor = Color(0xFF7EB8AF),
                        unfocusedIndicatorColor = Color(0xFFD8CEBE),
                        cursorColor = Color(0xFF496B5E),
                    ),
                )
                if (isLoading || isPreparingImage) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp).padding(4.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF496B5E),
                    )
                } else {
                    Surface(
                        color = if (canSend) Color(0xFFDDE8D9) else Color(0xFFE4DBC9),
                        shape = CircleShape,
                    ) {
                        IconButton(
                            onClick = onSendMessage,
                            enabled = canSend,
                            modifier = Modifier.semantics { contentDescription = "发送" },
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                tint = if (canSend) Color(0xFF496B5E) else Color(0xFF918B82),
                            )
                        }
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

@Preview(
    name = "Chat / Conversation",
    showBackground = true,
    widthDp = 393,
    heightDp = 852,
)
@Composable
private fun ChatConversationPreview() {
    ChatPreviewContent(
        state = previewChatState(
            messages = listOf(
                ChatMessage(
                    id = "a1",
                    role = "ASSISTANT",
                    content = "宇哥，今天想从哪儿开始？可以是一个小想法，也可以是一张图。",
                ),
                ChatMessage(
                    id = "u1",
                    role = "USER",
                    content = "帮我想一下这个聊天页怎么优化。",
                ),
                ChatMessage(
                    id = "a2",
                    role = "ASSISTANT",
                    content = "我会先把它变得更像奥拉的房间，而不是一个普通工具页：\n\n- 顶部保留状态，但降低工具感\n- 消息正文更适合长时间阅读\n- 输入区改成中文和暖色\n\n然后我们再细调工具调用和记忆展示。",
                ),
            ),
        ),
    )
}

@Preview(
    name = "Chat / Long Reply",
    showBackground = true,
    widthDp = 393,
    heightDp = 852,
)
@Composable
private fun ChatLongReplyPreview() {
    ChatPreviewContent(
        state = previewChatState(
            messages = listOf(
                ChatMessage(
                    id = "u1",
                    role = "USER",
                    content = "给我一个旅行计划。",
                ),
                ChatMessage(
                    id = "a1",
                    role = "ASSISTANT",
                    content = "可以，宇哥。我建议先按节奏来，不要把一天塞太满。\n\n**上午**\n- 慢一点出发，先去一个开阔的地方走走\n- 途中找一家安静的小店吃早餐\n\n**下午**\n- 安排一个需要认真看的景点\n- 留一点空白时间给临时发现\n\n**晚上**\n- 找舒服的餐厅，不赶路\n- 回来前买点第二天的小零食",
                ),
            ),
        ),
    )
}

@Preview(
    name = "Chat / Empty",
    showBackground = true,
    widthDp = 393,
    heightDp = 852,
)
@Composable
private fun ChatEmptyPreview() {
    ChatPreviewContent(state = previewChatState(messages = emptyList()))
}

@Preview(
    name = "Chat / Thinking",
    showBackground = true,
    widthDp = 393,
    heightDp = 852,
)
@Composable
private fun ChatThinkingPreview() {
    ChatPreviewContent(
        state = previewChatState(
            messages = listOf(
                ChatMessage(
                    id = "u1",
                    role = "USER",
                    content = "奥拉，你怎么看？",
                ),
                ChatMessage(
                    id = "a1",
                    role = "ASSISTANT",
                    content = "",
                    isStreaming = true,
                ),
            ),
            isLoading = true,
            presence = PresenceUiState(mode = PresenceMode.THINKING),
        ),
    )
}

@Composable
private fun ChatPreviewContent(state: ChatUiState) {
    CompanionTheme {
        ChatScreenContent(
            uiState = state,
            onSendMessage = {},
            onInputTextChanged = {},
            onClearError = {},
            onOpenMemoryRoom = {},
            onCancelReminder = {},
            onOpenSettings = {},
            onOpenMcpSettings = {},
            onOpenPermissionSettings = {},
            onDismissPermissionPrompt = {},
            onAttachImage = {},
            onRemoveImage = {},
            onPresenceTapped = {},
        )
    }
}

private fun previewChatState(
    messages: List<ChatMessage>,
    isLoading: Boolean = false,
    presence: PresenceUiState = PresenceUiState(mode = PresenceMode.IDLE),
): ChatUiState =
    ChatUiState(
        messages = messages,
        memories = listOf(
            ChatMemory(
                id = "memory-1",
                content = "用户喜欢温暖、安静、不要太工具化的界面。",
                type = "PREFERENCE",
                importance = 0.8f,
            ),
        ),
        reminders = listOf(
            ChatReminder(
                id = "reminder-1",
                title = "喝咖啡",
                message = "提醒宇哥喝咖啡",
                triggerAtMillis = System.currentTimeMillis() + 180_000L,
                exact = true,
                status = "SCHEDULED",
            ),
        ),
        configStatus = ChatConfigStatus(
            label = "GLM 已就绪",
            isReady = true,
            detail = "Preview",
        ),
        presence = presence,
        isLoading = isLoading,
    )

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
