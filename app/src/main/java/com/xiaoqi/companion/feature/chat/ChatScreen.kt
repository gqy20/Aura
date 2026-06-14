package com.xiaoqi.companion.feature.chat

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaoqi.companion.core.presence.PresenceMode
import com.xiaoqi.companion.core.presence.PresenceUiState
import com.xiaoqi.companion.ui.theme.CompanionTheme

/**
 * 聊天页入口与编排:
 * - 顶栏 [CompanionHeader]
 * - 消息列表 [LazyColumn](含空态 [EmptyChatState])
 * - 权限提示 [PermissionPromptCard]
 * - 输入栏 [InputBar]
 *
 * 子模块拆到同包平铺:
 * - [ChatHeader] / [ChatInputBar] / [ChatDialogs]
 */
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
        containerColor = Color.Transparent,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFFFCF6),
                            Color(0xFFF7F2EA),
                            Color(0xFFFFFCF6),
                        ),
                    ),
                ),
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            CompanionHeader(
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
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(messages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            modifier = Modifier.animateItem(
                                fadeInSpec = androidx.compose.animation.core.tween(durationMillis = 250),
                            ),
                        )
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
                modifier = Modifier
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
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

//region Previews

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
                    content = "Where should we start today?",
                ),
                ChatMessage(
                    id = "u1",
                    role = "USER",
                    content = "Help me think through this chat page.",
                ),
                ChatMessage(
                    id = "a2",
                    role = "ASSISTANT",
                    content = "I would simplify the top state, make long replies easier to read, and keep the input calm.",
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
                    content = "Give me a travel plan.",
                ),
                ChatMessage(
                    id = "a1",
                    role = "ASSISTANT",
                    content = "Sure. Keep the day light.\n\n**Morning**\n- Start slowly\n- Pick one quiet cafe\n\n**Afternoon**\n- Choose one main stop\n- Leave space for changes\n\n**Evening**\n- Eat nearby\n- Prepare for tomorrow",
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
                    content = "Aura, what do you think?",
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
                content = "Prefers warm, quiet interfaces.",
                type = "PREFERENCE",
                importance = 0.8f,
            ),
        ),
        reminders = listOf(
            ChatReminder(
                id = "reminder-1",
                title = "Coffee",
                message = "Remind me to drink coffee.",
                triggerAtMillis = System.currentTimeMillis() + 180_000L,
                exact = true,
                status = "SCHEDULED",
            ),
        ),
        configStatus = ChatConfigStatus(
            label = "GLM ready",
            isReady = true,
            detail = "Preview",
        ),
        presence = presence,
        isLoading = isLoading,
    )

//endregion
