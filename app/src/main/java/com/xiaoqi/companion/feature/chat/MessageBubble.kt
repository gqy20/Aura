package com.xiaoqi.companion.feature.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xiaoqi.companion.core.companion.model.ToolCallStatus
import com.xiaoqi.companion.ui.theme.ChatColors
import com.xiaoqi.companion.ui.theme.ChatStatusColors

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    onToolStatusClick: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onRegenerate: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val isUser = message.role == "USER"
    val contentColor = if (isUser) Color(0xFF20362F) else MaterialTheme.colorScheme.onSurface
    var menuExpanded by remember(message.id) { mutableStateOf(false) }
    val canShowActions = message.content.isNotBlank() && !message.isStreaming
    val copyMessage = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Aura 消息", message.content))
        Toast.makeText(context, "已复制消息", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Top,
        ) {
            if (isUser) {
                Surface(
                    color = ChatColors.BubbleUser,
                    tonalElevation = 0.dp,
                    shape = RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomEnd = 6.dp,
                        bottomStart = 18.dp,
                    ),
                    modifier = Modifier
                        .widthIn(max = 312.dp)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { if (canShowActions) menuExpanded = true },
                        ),
                ) {
                    MessageBubbleContent(
                        message = message,
                        isUser = true,
                        contentColor = contentColor,
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp),
                    )
                }
            } else {
                MessageBubbleContent(
                    message = message,
                    isUser = false,
                    contentColor = contentColor,
                    modifier = Modifier
                        .widthIn(max = 344.dp)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { if (canShowActions) menuExpanded = true },
                        )
                        .padding(top = 1.dp, bottom = 8.dp),
                    onToolStatusClick = onToolStatusClick,
                    onRetry = onRetry,
                )
            }
        }
        if (canShowActions) {
            Box {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .width(36.dp)
                        .height(22.dp)
                        .clip(CircleShape)
                        .clickable { menuExpanded = true }
                        .semantics { contentDescription = "消息操作" },
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f),
                        modifier = Modifier.size(17.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("复制") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            copyMessage()
                        },
                    )
                    if (isUser && onEdit != null) {
                        DropdownMenuItem(
                            text = { Text("编辑后重发") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            },
                        )
                    }
                    if (!isUser && onRegenerate != null) {
                        DropdownMenuItem(
                            text = { Text("重新生成") },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onRegenerate()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubbleContent(
    message: ChatMessage,
    isUser: Boolean,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onToolStatusClick: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        message.imageUri?.let { imageUri ->
            AsyncImage(
                model = imageUri,
                contentDescription = "消息图片",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(188.dp)
                    .height(144.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
            Spacer(modifier = Modifier.size(8.dp))
        }
        if (!isUser && message.isStreaming && message.content.isBlank()) {
            val activeToolStatus = message.toolStatus
                ?.takeIf { message.toolStatusType == ToolCallStatus.STARTED }
            if (activeToolStatus == null) {
                ThinkingHintCarousel(
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    modifier = Modifier.semantics {
                        stateDescription = activeToolStatus
                    },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AuraLoadingIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.primary,
                        contentDescription = null,
                    )
                    Text(
                        text = activeToolStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else if (message.isStreaming) {
            StreamingMessageText(
                renderBlocks = message.renderBlocks,
                renderDraft = message.renderDraft,
                isRenderDraftCode = message.isRenderDraftCode,
                contentFallback = message.content,
                color = contentColor,
            )
        } else {
            MarkdownMessageText(
                text = message.content,
                color = contentColor,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp),
            )
        }
        val toolStatus = message.toolStatus
        val performanceInfo = message.performanceInfo
        val showToolStatus = !isUser &&
            toolStatus != null &&
            message.toolStatusType != null &&
            !(message.isStreaming && message.content.isBlank())
        val showPerformance = !isUser && !message.isStreaming && performanceInfo != null
        if (showToolStatus || showPerformance) {
            Spacer(modifier = Modifier.size(6.dp))
            // 工具状态 pill 和 耗时/tok/s pill 同一行（FlowRow 兜底换行），间距 10dp
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (showToolStatus) {
                    ToolStatusPill(
                        text = toolStatus,
                        status = message.toolStatusType,
                        onClick = onToolStatusClick,
                    )
                }
                if (showPerformance) {
                    PerformancePill(performanceInfo)
                }
            }
        }
        val completionState = message.completionState
        if (!isUser && completionState != null) {
            Spacer(modifier = Modifier.size(6.dp))
            CompletionStatus(
                state = completionState,
                onRetry = onRetry,
            )
        }
    }
}

@Composable
private fun CompletionStatus(
    state: ChatMessageCompletionState,
    onRetry: (() -> Unit)?,
) {
    val label = when (state) {
        ChatMessageCompletionState.STOPPED -> "已停止生成"
        ChatMessageCompletionState.FAILED -> "回复中断"
    }
    val actionLabel = when (state) {
        ChatMessageCompletionState.STOPPED -> "重新生成"
        ChatMessageCompletionState.FAILED -> "重试"
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, end = 2.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        if (state == ChatMessageCompletionState.FAILED) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
                        }
                    ),
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall)
            if (onRetry != null) {
                TextButton(onClick = onRetry) {
                    Text(actionLabel, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun PerformancePill(performance: PerformanceInfo) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = performance.format(),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ToolStatusPill(
    text: String,
    status: ToolCallStatus? = null,
    onClick: (() -> Unit)? = null,
) {
    val dotColor = when (status) {
        ToolCallStatus.SUCCEEDED -> ChatStatusColors.SuccessDot
        ToolCallStatus.FAILED -> MaterialTheme.colorScheme.error
        ToolCallStatus.STARTED -> ChatStatusColors.Warning
        null -> MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
    }
    val baseModifier = Modifier
        .clip(RoundedCornerShape(8.dp))
        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f))
        .then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        )
        .padding(horizontal = 8.dp, vertical = 4.dp)
    Row(
        modifier = baseModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
