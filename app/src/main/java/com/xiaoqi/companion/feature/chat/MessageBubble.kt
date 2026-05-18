package com.xiaoqi.companion.feature.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import coil.compose.AsyncImage

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(message: ChatMessage) {
    val context = LocalContext.current
    val isUser = message.role == "USER"
    val bubbleColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = bubbleColor,
            tonalElevation = if (isUser) 0.dp else 1.dp,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomEnd = if (isUser) 6.dp else 18.dp,
                bottomStart = if (isUser) 18.dp else 6.dp,
            ),
            modifier = Modifier
                .widthIn(max = 320.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        if (message.content.isNotBlank()) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Aura message", message.content))
                            Toast.makeText(context, "已复制消息", Toast.LENGTH_SHORT).show()
                        }
                    },
                ),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            ) {
                message.imageUri?.let { imageUri ->
                    AsyncImage(
                        model = imageUri,
                        contentDescription = "Message image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(188.dp)
                            .height(144.dp)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                }
                if (!isUser && message.isStreaming && message.content.isBlank()) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(18.dp)
                            .semantics { contentDescription = "Assistant response loading" },
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    if (message.isStreaming) {
                        StreamingMessageText(message = message, color = contentColor)
                    } else {
                        MarkdownMessageText(
                            text = message.content,
                            color = contentColor,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                if (!isUser && message.toolStatus != null) {
                    Spacer(modifier = Modifier.size(6.dp))
                    ToolStatusPill(text = message.toolStatus)
                }
            }
        }
    }
}

@Composable
private fun StreamingMessageText(message: ChatMessage, color: androidx.compose.ui.graphics.Color) {
    val draftText = message.renderDraft.ifBlank {
        message.content.takeIf { message.renderBlocks.isEmpty() }.orEmpty()
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        message.renderBlocks.forEach { block ->
            MessageRenderBlockText(
                block = block,
                color = color,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (draftText.isNotBlank()) {
            Text(
                text = "$draftText...",
                color = color,
                style = if (message.isRenderDraftCode) {
                    MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                } else {
                    MaterialTheme.typography.bodyLarge
                },
            )
        }
    }
}

@Composable
private fun ToolStatusPill(text: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)),
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
