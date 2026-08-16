package com.xiaoqi.companion.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaoqi.companion.BuildConfig
import com.xiaoqi.companion.core.presence.PresenceAnimationState
import com.xiaoqi.companion.core.presence.PresenceMode
import com.xiaoqi.companion.core.presence.PresenceUiState
import com.xiaoqi.companion.data.db.converter.LlmProvider
import com.xiaoqi.companion.ui.theme.ChatStatusColors

/**
 * 聊天页顶栏:头像 + 标题 + 状态动画点 + Memory/Reminders/MCP/Settings 入口按钮,
 * 以及模型未就绪时的 [ConfigStatusCard] 提示。
 *
 * 配套扩展:
 * - [ChatToolCapabilitySettings.mcpDisplayLabel]:MCP 入口按钮文案
 */
@Composable
internal fun CompanionHeader(
    presence: PresenceUiState,
    presenceAnimation: PresenceAnimationState,
    configStatus: ChatConfigStatus,
    memories: List<ChatMemory>,
    reminders: List<ChatReminder>,
    toolSettings: ChatToolCapabilitySettings,
    mcpServerTools: Map<String, List<String>>,
    isLoading: Boolean = false,
    latestActivity: String? = null,
    hasError: Boolean = false,
    /** 关系阶段徽标(陌生/初识/熟悉/亲密);"陌生"阶段不显示。 */
    relationshipLabel: String? = null,
    onOpenMemoryRoom: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenConversations: () -> Unit,
    onOpenMcpSettings: () -> Unit,
    onOpenSettings: () -> Unit,
    onPresenceTapped: () -> Unit,
) {
    val scheduledReminderCount = reminders.count { it.status == "SCHEDULED" }
    val mcpState = resolveHeaderMcpState(toolSettings, mcpServerTools)
    val statusText = resolveCompanionHeaderStatus(
        isConfigReady = configStatus.isReady,
        isLoading = isLoading,
        latestActivity = latestActivity,
        hasError = hasError,
        presenceMode = presence.mode,
        presenceLabel = presence.label,
    )
    val statusHasError = !configStatus.isReady || hasError || presence.mode == PresenceMode.ERROR

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AuraPetAvatar(
                presence = presence,
                animationState = presenceAnimation,
                size = 40.dp,
                isLocalModel = configStatus.provider == LlmProvider.LOCAL_QWEN,
                onClick = onPresenceTapped,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            ) {
                Text(
                    text = BuildConfig.BRAND_NAME,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (statusHasError) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    ChatStatusColors.SuccessDot
                                }
                            ),
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!relationshipLabel.isNullOrBlank() && relationshipLabel != "陌生") {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                        ) {
                            Text(
                                text = relationshipLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
            HeaderCapabilityAction(
                imageVector = Icons.AutoMirrored.Filled.List,
                contentDescription = "打开对话列表",
                onClick = onOpenConversations,
            )
            HeaderCapabilityAction(
                imageVector = Icons.Default.Favorite,
                count = memories.size,
                contentDescription = "打开记忆，共 ${memories.size} 条",
                onClick = onOpenMemoryRoom,
            )
            HeaderCapabilityAction(
                imageVector = Icons.Default.Notifications,
                count = scheduledReminderCount,
                contentDescription = "打开提醒，共 $scheduledReminderCount 条待执行",
                onClick = onOpenReminders,
            )
            HeaderCapabilityAction(
                imageVector = Icons.Default.Build,
                contentDescription = mcpState.contentDescription,
                onClick = onOpenMcpSettings,
                tone = mcpState.tone,
            )
            HeaderCapabilityAction(
                imageVector = Icons.Default.Settings,
                contentDescription = "打开设置",
                onClick = onOpenSettings,
                hasAlert = !configStatus.isReady,
            )
        }

        if (!configStatus.isReady) {
            ConfigStatusCard(status = configStatus, onOpenSettings = onOpenSettings)
        }
    }
}

internal fun resolveCompanionHeaderStatus(
    isConfigReady: Boolean,
    isLoading: Boolean,
    latestActivity: String?,
    hasError: Boolean,
    presenceMode: PresenceMode,
    presenceLabel: String,
): String = when {
    !isConfigReady -> "待配置"
    isLoading -> latestActivity?.takeIf { it.isNotBlank() } ?: "思考中"
    hasError -> "连接异常"
    presenceMode == PresenceMode.ERROR -> "暂时离线"
    else -> presenceLabel.removePrefix("Aura ").ifBlank { "在这里" }
}

internal enum class HeaderCapabilityTone { NEUTRAL, ACTIVE, SUCCESS, WARNING }

internal enum class HeaderMcpState(
    val label: String,
    val contentDescription: String,
    val tone: HeaderCapabilityTone,
) {
    DISABLED("MCP", "打开 MCP，当前已关闭", HeaderCapabilityTone.NEUTRAL),
    NEEDS_SETUP("MCP", "打开 MCP，当前配置异常", HeaderCapabilityTone.WARNING),
    ACTIVE("MCP", "打开 MCP，等待连接检查", HeaderCapabilityTone.ACTIVE),
    READY("MCP", "打开 MCP，当前已连接", HeaderCapabilityTone.SUCCESS),
}

internal fun resolveHeaderMcpState(
    settings: ChatToolCapabilitySettings,
    discoveredTools: Map<String, List<String>>,
): HeaderMcpState {
    val enabledServers = settings.mcpServers.filter { it.enabled }
    if (!settings.mcpEnabled || enabledServers.isEmpty()) return HeaderMcpState.DISABLED
    if (enabledServers.any { !it.isReady }) return HeaderMcpState.NEEDS_SETUP
    val testedServers = enabledServers.filter { it.id in discoveredTools }
    if (testedServers.any { discoveredTools[it.id].isNullOrEmpty() }) return HeaderMcpState.NEEDS_SETUP
    if (testedServers.any { discoveredTools[it.id].orEmpty().isNotEmpty() }) return HeaderMcpState.READY
    return HeaderMcpState.ACTIVE
}

@Composable
private fun HeaderCapabilityAction(
    imageVector: ImageVector,
    onClick: () -> Unit,
    contentDescription: String,
    count: Int = 0,
    tone: HeaderCapabilityTone = HeaderCapabilityTone.NEUTRAL,
    hasAlert: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val containerColor = when (tone) {
        HeaderCapabilityTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f)
        HeaderCapabilityTone.ACTIVE -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f)
        HeaderCapabilityTone.SUCCESS -> Color(0xFFE8F3EA)
        HeaderCapabilityTone.WARNING -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.68f)
    }
    val contentColor = when (tone) {
        HeaderCapabilityTone.WARNING -> MaterialTheme.colorScheme.onErrorContainer
        HeaderCapabilityTone.SUCCESS -> Color(0xFF45684E)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(containerColor),
        )
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )
        if (count > 0) {
            Text(
                text = count.coerceAtMost(99).toString(),
                color = contentColor.copy(alpha = 0.9f),
                fontSize = 10.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 3.dp, end = 3.dp),
            )
        }
        if (hasAlert) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error),
            )
        }
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
