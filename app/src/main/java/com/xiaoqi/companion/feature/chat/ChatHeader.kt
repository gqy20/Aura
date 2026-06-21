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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiaoqi.companion.BuildConfig
import com.xiaoqi.companion.core.presence.PresenceAnimationState
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
    presenceAnimation: PresenceAnimationState,
    configStatus: ChatConfigStatus,
    memories: List<ChatMemory>,
    reminders: List<ChatReminder>,
    mcpLabel: String,
    onOpenMemoryRoom: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenConversations: () -> Unit,
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AuraPetAvatar(
                presence = presence,
                animationState = presenceAnimation,
                size = 40.dp,
                onClick = onPresenceTapped,
            )
            // 副标题为空时不渲染,避免空 Text 撑高 Row。
            val subtitle = presence.chatHeaderStatus(configStatus)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = BuildConfig.BRAND_NAME,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeaderActionIcon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    onClick = onOpenConversations,
                    contentDescription = "对话列表",
                )
                HeaderActionIcon(
                    imageVector = Icons.Default.Favorite,
                    onClick = onOpenMemoryRoom,
                    contentDescription = "打开记忆",
                    badge = memories.size,
                )
                if (scheduledReminderCount > 0) {
                    HeaderActionIcon(
                        imageVector = Icons.Default.Notifications,
                        onClick = onOpenReminders,
                        contentDescription = "打开提醒",
                        badge = scheduledReminderCount,
                    )
                }
                HeaderActionIcon(
                    imageVector = Icons.Default.Build,
                    onClick = onOpenMcpSettings,
                    contentDescription = "打开 MCP",
                    active = mcpLabel != "MCP",
                )
                HeaderActionIcon(
                    imageVector = Icons.Default.Settings,
                    onClick = onOpenSettings,
                    contentDescription = "打开设置",
                )
            }
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
    badge: Int? = null,
    active: Boolean = false,
    modifier: Modifier = Modifier,
) {
    BadgedBox(
        badge = {
            badge?.takeIf { it > 0 }?.let {
                Badge { Text(it.toString()) }
            }
        },
        modifier = modifier.size(40.dp),
    ) {
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
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f)
                },
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun PresenceUiState.chatHeaderStatus(configStatus: ChatConfigStatus): String =
    when {
        !configStatus.isReady -> "待配置"
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = status.label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (status.detail.isNotBlank()) {
                Text(
                    text = status.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                )
            }
            TextButton(onClick = onOpenSettings) {
                Text("去设置")
            }
        }
    }
}

internal fun ChatToolCapabilitySettings.mcpDisplayLabel(): String {
    val first = mcpServers.firstOrNull { it.enabled && it.isReady } ?: mcpServers.firstOrNull { it.enabled }
    return when {
        first == null -> "MCP"
        first.displayName.isNotBlank() -> first.displayName
        else -> first.provider.displayName
    }
}
