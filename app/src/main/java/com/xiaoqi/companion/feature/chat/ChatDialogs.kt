package com.xiaoqi.companion.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.xiaoqi.companion.ui.theme.ChatColors
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * 聊天页的所有弹窗与卡片集合:
 * - [RemindersDialog]:提醒列表(可取消 / 显示状态)
 * - [PermissionPromptCard]:精确闹钟权限申请卡片
 * - [AuraDialogPanel] / [AuraDialogHeader] / [AuraEmptyState]:可复用的 Aura 弹窗原语
 *
 * 配套扩展:
 * - [String.reminderStatusLabel]:提醒状态机到展示文案的映射
 */
@Composable
internal fun RemindersDialog(
    reminders: List<ChatReminder>,
    onDismiss: () -> Unit,
    onCancelReminder: (String) -> Unit,
) {
    val scheduledReminderCount = reminders.count { it.status == "SCHEDULED" }

    AuraDialogPanel(
        onDismiss = onDismiss,
        fillHeight = 0.72f,
    ) {
        AuraDialogHeader(
            title = "提醒",
            subtitle = "$scheduledReminderCount 个待生效",
            onDismiss = onDismiss,
        )

        if (reminders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                AuraEmptyState(
                    title = "暂无提醒",
                    message = "试试：3 分钟后提醒我",
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(reminders, key = { it.id }) { reminder ->
                    ReminderItemCard(
                        reminder = reminder,
                        onCancel = { onCancelReminder(reminder.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReminderItemCard(
    reminder: ChatReminder,
    onCancel: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ChatColors.CardSurface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CapabilityMetaPill(text = if (reminder.exact) "精准" else "宽限")
                    CapabilityMetaPill(text = reminder.status.reminderStatusLabel())
                }
                Text(
                    text = reminder.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = reminder.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = DateFormat.getDateTimeInstance().format(Date(reminder.triggerAtMillis)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                )
            }
            if (reminder.status == "SCHEDULED") {
                TextButton(onClick = onCancel) {
                    Text("取消")
                }
            }
        }
    }
}

private fun String.reminderStatusLabel(): String =
    when (uppercase(Locale.US)) {
        "SCHEDULED" -> "待生效"
        "CANCELLED" -> "已关闭"
        "FIRED" -> "已触发"
        else -> lowercase(Locale.US)
    }

@Composable
internal fun PermissionPromptCard(
    prompt: ChatPermissionPrompt,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.64f),
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = prompt.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = prompt.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.78f),
                )
            }
            TextButton(onClick = onDismiss) {
                Text("稍后")
            }
            Button(onClick = onOpenSettings) {
                Text(prompt.primaryActionLabel)
            }
        }
    }
}

/**
 * 通用 Aura 弹窗外壳(浅米色 Surface + 圆角 + 阴影)。
 * [fillHeight] 为 0~1 时占用屏幕高度的对应比例,null 时按内容自适应。
 */
@Composable
internal fun AuraDialogPanel(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    fillHeight: Float? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        val panelModifier = fillHeight?.let {
            modifier
                .fillMaxWidth()
                .fillMaxHeight(it)
        } ?: modifier.fillMaxWidth()

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = ChatColors.InputSurface,
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
            modifier = panelModifier,
        ) {
            Column(
                modifier = contentModifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content,
            )
        }
    }
}

@Composable
internal fun AuraDialogHeader(
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Surface(
            shape = CircleShape,
            color = Color(0xFFF1EADB),
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(38.dp)
                    .semantics { contentDescription = "关闭" },
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
internal fun AuraEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = ChatColors.BubbleAi,
            modifier = Modifier.size(52.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "A",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
