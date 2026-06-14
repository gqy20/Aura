package com.xiaoqi.companion.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiaoqi.companion.core.presence.PresenceUiState

/**
 * 聊天页顶栏:头像 + 标题 + Memory/Reminders/MCP/Settings 入口按钮,
 * 以及模型未就绪时的 [ConfigStatusCard] 提示。
 *
 * 配套扩展:
 * - [PresenceUiState.chatHeaderStatus]:Presence 状态映射到顶栏副标题
 * - [ChatToolCapabilitySettings.mcpDisplayLabel]:MCP 入口按钮文案
 */
@Composable
internal fun CompanionHeader(
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
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AuraPetAvatar(
                presence = presence,
                size = 40.dp,
                onClick = onPresenceTapped,
            )
            // Aura 标题 + 副标题横向排成一行,占满到右侧 icon,
            // 避免 Column 双行撑高 Row 让"Aura" 文字偏离 Row 中心。
            // 副标题为空时直接不渲染,不占行高。
            // Text 显式 height(48.dp) + wrapContentHeight 让 glyph 视觉中心
            // 跟外 Row 中心对齐(否则 lineHeight 容器会让 glyph 偏上 12px)。
            val subtitle = presence.chatHeaderStatus(configStatus)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Aura",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF496B5E),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .height(48.dp)
                        .wrapContentHeight(align = Alignment.CenterVertically),
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.wrapContentHeight(align = Alignment.CenterVertically),
                    )
                }
            }
            HeaderActionIcon(
                imageVector = Icons.Default.Favorite,
                onClick = onOpenMemoryRoom,
                contentDescription = "Open memory",
                badge = memories.size.takeIf { it > 0 }?.toString(),
            )
            if (scheduledReminderCount > 0) {
                HeaderActionIcon(
                    imageVector = Icons.Default.Notifications,
                    onClick = onOpenReminders,
                    contentDescription = "Open reminders",
                    badge = scheduledReminderCount.toString(),
                )
            }
            HeaderActionIcon(
                imageVector = Icons.Default.Build,
                onClick = onOpenMcpSettings,
                contentDescription = "Open MCP",
                active = mcpLabel != "MCP",
            )
            HeaderActionIcon(
                imageVector = Icons.Default.Settings,
                onClick = onOpenSettings,
                contentDescription = "Open settings",
            )
        }

        if (!configStatus.isReady) {
            ConfigStatusCard(status = configStatus, onOpenSettings = onOpenSettings)
        }
    }
}

@Composable
private fun HeaderActionIcon(
    imageVector: ImageVector,
    onClick: () -> Unit,
    contentDescription: String,
    badge: String? = null,
    active: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(40.dp)) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(40.dp)
                .semantics { this.contentDescription = contentDescription },
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = null,
                tint = if (active) {
                    Color(0xFF496B5E)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f)
                },
                modifier = Modifier.size(20.dp),
            )
        }
        badge?.let {
            Surface(
                shape = CircleShape,
                color = Color(0xFFDDE8D9),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(16.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF496B5E),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun PresenceUiState.chatHeaderStatus(configStatus: ChatConfigStatus): String =
    when {
        !configStatus.isReady -> "Setup"
        else -> label
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
                    text = "Model",
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
                    Text("Set")
                }
            }
        }
    }
}

internal fun ChatToolCapabilitySettings.mcpDisplayLabel(): String =
    when {
        mcpHttpUrl.isBlank() -> "MCP"
        mcpServerName.isNotBlank() -> mcpServerName
        else -> "MCP on"
    }
