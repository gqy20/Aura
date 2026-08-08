package com.xiaoqi.companion.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiaoqi.companion.data.repository.ConversationItem
import com.xiaoqi.companion.ui.theme.ChatColors
import java.text.DateFormat
import java.util.Date

@Composable
internal fun ConversationListSheet(
    conversations: List<ConversationItem>,
    currentSessionId: String,
    onDismiss: () -> Unit,
    onNewConversation: () -> Unit,
    onSwitchConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
) {
    AuraDialogPanel(
        onDismiss = onDismiss,
        fillHeight = 0.72f,
    ) {
        AuraDialogHeader(
            title = "对话",
            titleMeta = conversations.size.toString(),
            onDismiss = onDismiss,
            primaryAction = {
                IconButton(
                    onClick = onNewConversation,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = "新建对话" },
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            },
        )

        if (conversations.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                AuraEmptyState(
                    title = "暂无对话",
                    message = "点击右上角的＋开始聊天",
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(conversations, key = { it.id }) { conversation ->
                    val isCurrentConversation = conversation.id == currentSessionId
                    ConversationItemCard(
                        conversation = conversation,
                        isCurrent = isCurrentConversation,
                        onClick = {
                            // 点击当前会话只关弹窗,不触发 switch(写入相同 sessionId 不会触发 flow)
                            if (!isCurrentConversation) {
                                onSwitchConversation(conversation.id)
                            }
                            onDismiss()
                        },
                        onDelete = { onDeleteConversation(conversation.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationItemCard(
    conversation: ConversationItem,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isCurrent) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            ChatColors.CardSurface
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                } else {
                    ChatColors.BubbleAi
                },
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = if (isCurrent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        },
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "${conversation.messageCount} 条消息",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    )
                    Text(
                        text = DateFormat.getDateInstance(DateFormat.SHORT).format(Date(conversation.updatedAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    )
                }
            }
            if (!isCurrent) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = "删除对话" },
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = "当前",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}
