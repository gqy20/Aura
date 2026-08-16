package com.xiaoqi.companion.feature.chat

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaoqi.companion.core.companion.model.ToolCallStatus
import com.xiaoqi.companion.core.presence.PresenceAnimationState
import com.xiaoqi.companion.core.presence.PresenceMode
import com.xiaoqi.companion.core.presence.PresenceUiState
import com.xiaoqi.companion.ui.theme.ChatColors
import com.xiaoqi.companion.ui.theme.CompanionTheme
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.feature.chat.map.MapIntentLauncher
import com.xiaoqi.companion.feature.chat.map.MapRoutePromptBuilder
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

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
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val currentSessionId by viewModel.currentSessionId.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // M3 prefill:从主页 Insight 卡片"和 Aura 聊聊"带过来的预填 prompt
    androidx.compose.runtime.LaunchedEffect(uiState.pendingPrefill) {
        val prefill = uiState.pendingPrefill ?: return@LaunchedEffect
        viewModel.updateInputText(prefill)
        // 清空 pendingPrefill,避免下次进入 Chat 重复触发
        viewModel.consumePrefillPrompt("")
    }

    ChatScreenContent(
        uiState = uiState,
        conversations = conversations,
        currentSessionId = currentSessionId,
        onSendMessage = { viewModel.sendMessage(uiState.inputText) },
        onStopGenerating = { viewModel.stopGenerating() },
        onRetryMessage = { viewModel.retryMessage(it) },
        onEditMessage = { viewModel.editMessage(it) },
        onRetryLastMessage = { viewModel.retryLastMessage() },
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
        onOpenMap = { interaction -> MapIntentLauncher.open(context, interaction) },
        onRerunRoute = { draft -> viewModel.sendMessage(MapRoutePromptBuilder.build(draft)) },
        onNewConversation = { viewModel.startNewConversation() },
        onSwitchConversation = { viewModel.switchConversation(it) },
        onDeleteConversation = { viewModel.deleteConversation(it) },
    )
}

@Composable
fun ChatScreenContent(
    uiState: ChatUiState,
    conversations: List<com.xiaoqi.companion.data.repository.ConversationItem> = emptyList(),
    currentSessionId: String = "default",
    onSendMessage: () -> Unit,
    onStopGenerating: () -> Unit,
    onRetryMessage: (String) -> Unit = {},
    onEditMessage: (String) -> Unit = {},
    onRetryLastMessage: () -> Unit = {},
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
    onOpenMap: (com.xiaoqi.companion.feature.chat.map.MapToolInteraction) -> Unit = {},
    onRerunRoute: (com.xiaoqi.companion.feature.chat.map.MapRouteDraft) -> Unit = {},
    onNewConversation: () -> Unit = {},
    onSwitchConversation: (String) -> Unit = {},
    onDeleteConversation: (String) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let(onAttachImage)
    }
    var isRemindersOpen by remember { mutableStateOf(false) }
    var isConversationsOpen by remember { mutableStateOf(false) }
    var selectedToolCall by remember { mutableStateOf<ChatToolCall?>(null) }
    val messages = uiState.messages
    val latestMessage = messages.lastOrNull()
    val latestUserMessageId = messages.lastOrNull { it.role == "USER" }?.id
    var hasCompletedInitialScroll by remember { mutableStateOf(false) }
    val reversedMessages = remember(messages) { messages.asReversed() }

    // 只有真实拖动才暂停跟随；卡片变高、流式重排和 IME 动画不能夺走滚动控制权。
    var isUserPinnedToBottom by rememberSaveable { mutableStateOf(true) }
    var hasUnseenMessages by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(reversedMessages.size) {
        if (reversedMessages.isNotEmpty() && !hasCompletedInitialScroll) {
            listState.scrollToItem(0)
            hasCompletedInitialScroll = true
        }
    }

    LaunchedEffect(latestUserMessageId) {
        if (latestUserMessageId != null && hasCompletedInitialScroll) {
            // 用户主动发送用动画滚动;流式跟随保持即时滚动,快速增高时动画反而抖
            listState.animateScrollToItem(0)
            isUserPinnedToBottom = true
            hasUnseenMessages = false
        }
    }

    val userScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    isUserPinnedToBottom = false
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            Triple(
                listState.isScrollInProgress,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
            )
        }
            .distinctUntilChanged()
            .collect { (isScrolling, idx, offset) ->
                val nowPinned = idx == 0 && offset < 32
                if (!isUserPinnedToBottom && !isScrolling && nowPinned) {
                    isUserPinnedToBottom = true
                    hasUnseenMessages = false
                }
            }
    }

    LaunchedEffect(latestMessage?.id) {
        if (!hasCompletedInitialScroll || latestMessage == null) return@LaunchedEffect
        if (isUserPinnedToBottom) {
            listState.scrollToItem(0)
        } else {
            hasUnseenMessages = true
        }
    }

    // IME 弹起/收起动画结束后,确保 index 0(最新消息)滚入视口。
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val listStateHolder = rememberUpdatedState(listState)
    val reversedMessagesHolder = rememberUpdatedState(reversedMessages)
    val hasInitialScrollHolder = rememberUpdatedState(hasCompletedInitialScroll)
    val isPinnedHolder = rememberUpdatedState(isUserPinnedToBottom)
    DisposableEffect(view) {
        val callback = object : WindowInsetsAnimationCompat.Callback(
            WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_STOP,
        ) {
            override fun onProgress(
                insets: WindowInsetsCompat,
                runningAnimations: List<WindowInsetsAnimationCompat>,
            ): WindowInsetsCompat = insets

            override fun onEnd(animation: WindowInsetsAnimationCompat) {
                val isImeAnimation =
                    animation.typeMask and WindowInsetsCompat.Type.ime() != 0
                if (!isImeAnimation) return
                val current = reversedMessagesHolder.value
                if (hasInitialScrollHolder.value && isPinnedHolder.value && current.isNotEmpty()) {
                    coroutineScope.launch {
                        listStateHolder.value.scrollToItem(0)
                    }
                }
            }
        }
        ViewCompat.setWindowInsetsAnimationCallback(view, callback)
        onDispose {
            ViewCompat.setWindowInsetsAnimationCallback(view, null)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ChatColors.InputSurface,
                            ChatColors.CardSurface,
                            ChatColors.InputSurface,
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
                presenceAnimation = uiState.presenceAnimation,
                configStatus = uiState.configStatus,
                memories = uiState.memories,
                reminders = uiState.reminders,
                toolSettings = uiState.toolCapabilitySettings,
                mcpServerTools = uiState.mcpServerTools,
                isLoading = uiState.isLoading,
                latestActivity = latestMessage?.takeIf { it.isStreaming }?.toolStatus,
                hasError = uiState.error != null,
                onOpenMemoryRoom = onOpenMemoryRoom,
                onOpenReminders = { isRemindersOpen = true },
                onOpenConversations = { isConversationsOpen = true },
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
                    EmptyChatState(
                        presence = uiState.presence,
                        presenceAnimation = uiState.presenceAnimation,
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    LazyColumn(
                        state = listState,
                        reverseLayout = true,
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(userScrollConnection),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = 8.dp,
                            end = 16.dp,
                            bottom = if (isUserPinnedToBottom) 8.dp else 64.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(reversedMessages, key = { it.id }) { message ->
                            val messageToolCall = remember(message.id, uiState.toolCalls) {
                                findToolCallForMessage(message, uiState.toolCalls)
                            }
                            val messageMapToolCall = remember(
                                message.id,
                                message.isStreaming,
                                message.toolCallIds,
                                uiState.mapToolCalls,
                            ) {
                                findMapToolCallForMessage(message, uiState.mapToolCalls)
                            }
                            val onToolClick = remember(messageToolCall) {
                                messageToolCall?.let { toolCall ->
                                    { selectedToolCall = toolCall }
                                }
                            }
                            val onToolStepClick = remember(message.id, uiState.toolCalls, uiState.mapToolCalls) {
                                val callsById = (uiState.toolCalls + uiState.mapToolCalls)
                                    .associateBy { call -> call.id }
                                val handler: (ChatToolStep) -> Unit = { step ->
                                    val target = step.callId?.let { id -> callsById[id] }
                                    if (target != null) selectedToolCall = target
                                }
                                handler
                            }
                            Column(
                                modifier = Modifier.animateItem(
                                    fadeInSpec = androidx.compose.animation.core.tween(durationMillis = 250),
                                ),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                MessageBubble(
                                    message = message,
                                    onToolStatusClick = onToolClick,
                                    onToolStepClick = onToolStepClick,
                                    onRetry = if (message.role == "ASSISTANT") {
                                        { onRetryMessage(message.id) }
                                    } else {
                                        null
                                    },
                                    onEdit = if (message.role == "USER") {
                                        { onEditMessage(message.id) }
                                    } else {
                                        null
                                    },
                                    onRegenerate = if (message.role == "ASSISTANT") {
                                        { onRetryMessage(message.id) }
                                    } else {
                                        null
                                    },
                                )
                                val mapInteraction = messageMapToolCall?.mapInteraction
                                AnimatedVisibility(
                                    visible = !message.isStreaming && mapInteraction != null,
                                    enter = fadeIn(tween(180)) + expandVertically(
                                        animationSpec = tween(220),
                                        expandFrom = Alignment.Top,
                                    ),
                                    exit = ExitTransition.None,
                                ) {
                                    mapInteraction?.let { interaction ->
                                        MapResultCard(
                                            interaction = interaction,
                                            onOpenMap = { onOpenMap(interaction) },
                                            onAdjustRoute = { selectedToolCall = messageMapToolCall },
                                            modifier = Modifier.padding(start = 4.dp).fillMaxWidth(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (!isUserPinnedToBottom) {
                        SmallFloatingActionButton(
                            onClick = {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(0)
                                    isUserPinnedToBottom = true
                                    hasUnseenMessages = false
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp)
                                .semantics {
                                    contentDescription = if (hasUnseenMessages) {
                                        "有新回复，回到最新消息"
                                    } else {
                                        "回到最新消息"
                                    }
                                },
                        ) {
                            Box(contentAlignment = Alignment.TopEnd) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                                if (hasUnseenMessages) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .background(MaterialTheme.colorScheme.error, CircleShape),
                                    )
                                }
                            }
                        }
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

            selectedToolCall?.let { toolCall ->
                ToolCallDetailSheet(
                    toolCall = toolCall,
                    onDismiss = { selectedToolCall = null },
                    onOpenMap = onOpenMap,
                    onRerunRoute = onRerunRoute,
                )
            }

            uiState.error?.let { error ->
                ChatErrorCard(
                    message = error,
                    canRetry = messages.any { it.role == "USER" } && !uiState.isLoading,
                    onRetry = onRetryLastMessage,
                    onDismiss = onClearError,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            InputBar(
                inputText = uiState.inputText,
                onInputTextChanged = onInputTextChanged,
                onSendMessage = onSendMessage,
                onStopGenerating = onStopGenerating,
                pendingImage = uiState.pendingImage,
                isPreparingImage = uiState.isPreparingImage,
                onPickImage = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onRemoveImage = onRemoveImage,
                isLoading = uiState.isLoading,
                isConfigReady = uiState.configStatus.isReady,
                modifier = Modifier
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        }
    }

    if (isRemindersOpen) {
        RemindersDialog(
            reminders = uiState.reminders,
            onDismiss = { isRemindersOpen = false },
            onCancelReminder = onCancelReminder,
        )
    }

    if (isConversationsOpen) {
        ConversationListSheet(
            conversations = conversations,
            currentSessionId = currentSessionId,
            onDismiss = { isConversationsOpen = false },
            onNewConversation = {
                onNewConversation()
                isConversationsOpen = false
            },
            onSwitchConversation = onSwitchConversation,
            onDeleteConversation = onDeleteConversation,
        )
    }
}

@Composable
internal fun ChatErrorCard(
    message: String,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            if (canRetry) {
                TextButton(onClick = onRetry) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                    Text("重试")
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.semantics { contentDescription = "关闭错误提示" },
            ) {
                Icon(Icons.Default.Close, contentDescription = null)
            }
        }
    }
}

@Composable
private fun EmptyChatState(
    presence: PresenceUiState,
    presenceAnimation: PresenceAnimationState,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(horizontal = 32.dp),
    ) {
        AuraPetAvatar(
            presence = presence,
            animationState = presenceAnimation,
            size = 64.dp,
        )
        Text(
            text = "Aura 在这里",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "想聊什么都可以，我一直在。",
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
            onStopGenerating = {},
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

internal fun findToolCallForMessage(
    message: ChatMessage,
    toolCalls: List<ChatToolCall>,
): ChatToolCall? {
    if (message.role != "ASSISTANT") return null
    toolCalls.firstOrNull { it.id in message.toolCallIds }?.let { return it }
    return toolCalls
        .asSequence()
        .filter { it.mapInteraction != null }
        .filter { call ->
            val completedAt = call.completedAt ?: return@filter false
            completedAt <= message.timestamp && message.timestamp - completedAt <= MAP_TOOL_HISTORY_WINDOW_MS
        }
        .maxByOrNull { it.completedAt ?: Long.MIN_VALUE }
}

internal fun findMapToolCallForMessage(
    message: ChatMessage,
    mapToolCalls: List<ChatToolCall>,
): ChatToolCall? {
    if (message.role != "ASSISTANT") return null

    val ownedMapCall = mapToolCalls
        .asSequence()
        .filter { it.id in message.toolCallIds }
        .filter { it.mapInteraction != null && it.toolStatus == ToolCallStatus.SUCCEEDED }
        .maxByOrNull { it.completedAt ?: Long.MIN_VALUE }
    if (ownedMapCall != null) return ownedMapCall

    if (message.isStreaming || message.toolCallIds.isNotEmpty()) return null
    return mapToolCalls
        .asSequence()
        .filter { it.mapInteraction != null }
        .filter { call ->
            val completedAt = call.completedAt ?: return@filter false
            completedAt <= message.timestamp && message.timestamp - completedAt <= MAP_TOOL_HISTORY_WINDOW_MS
        }
        .maxByOrNull { it.completedAt ?: Long.MIN_VALUE }
}

private const val MAP_TOOL_HISTORY_WINDOW_MS = 2 * 60 * 1000L

//endregion
