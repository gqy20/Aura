package com.xiaoqi.companion.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == "USER"
    val bubbleColor = if (isUser) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bubbleColor)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        val displayText = if (message.isStreaming && message.content.isNotEmpty()) {
            "${message.content}..."
        } else {
            message.content
        }
        Text(
            text = displayText,
            color = contentColor,
        )
        if (!isUser && message.toolStatus != null) {
            Spacer(modifier = Modifier.size(6.dp))
            ToolStatusPill(text = message.toolStatus)
        }
    }
}

@Composable
private fun ToolStatusPill(text: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.58f))
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
