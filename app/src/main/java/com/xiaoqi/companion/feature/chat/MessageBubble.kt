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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
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
) {
    val context = LocalContext.current
    val isUser = message.role == "USER"
    val contentColor = if (isUser) Color(0xFF20362F) else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = modifier.fillMaxWidth(),
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
                        onLongClick = {
                            if (message.content.isNotBlank()) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Aura 消息", message.content))
                                Toast.makeText(context, "已复制消息", Toast.LENGTH_SHORT).show()
                            }
                        },
                    ),
            ) {
                MessageBubbleContent(
                    message = message,
                    isUser = true,
                    contentColor = contentColor,
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp),
                    onToolStatusClick = null,
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
                    onLongClick = {
                        if (message.content.isNotBlank()) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Aura 消息", message.content))
                            Toast.makeText(context, "已复制消息", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
                    .padding(top = 1.dp, bottom = 8.dp),
                onToolStatusClick = onToolStatusClick,
            )
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
            AuraLoadingIndicator(
                modifier = Modifier.size(18.dp),
                color = MaterialTheme.colorScheme.primary,
                contentDescription = "Aura 正在回复",
            )
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
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 22.sp),
            )
        }
        if (!isUser && message.toolStatus != null) {
            Spacer(modifier = Modifier.size(6.dp))
            ToolStatusPill(
                text = message.toolStatus,
                status = message.toolStatusType,
                onClick = onToolStatusClick,
            )
        }
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
