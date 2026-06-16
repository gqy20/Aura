package com.xiaoqi.companion.feature.insight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiaoqi.companion.data.repository.InsightEvidenceView
import com.xiaoqi.companion.feature.chat.ChatInsight

/**
 * 长按 Insight 卡片触发的弹层(plan §4.3 关键交互)。
 *
 * 4 个动作:
 * - 本周不再说 [category] → onMute(days=7)
 * - 知道了 → onDismiss
 * - 查看依据 → 弹层展开 evidence 列表(MVP 阶段只显示 id,后续 M3 接 LLM 解释)
 * - 和 Aura 聊聊 → onChat(本 PR 只 markClicked,prefill 留给 PR-C)
 */
@Composable
internal fun InsightLongPressDialog(
    insight: ChatInsight,
    evidence: InsightEvidenceView,
    onDismiss: () -> Unit,
    onMute: (days: Int) -> Unit,
    onAcknowledge: () -> Unit,
    onShowEvidence: () -> Unit,
    onChat: () -> Unit,
    showEvidence: Boolean,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            // 标题栏右侧 ⨯ 关闭 — 跟 MemoryDetailDialog 风格统一;dismissButton 不再放"关闭"避免和 4 个带 icon 的 action 视觉混在一起
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = insight.headline,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "${insight.category} · ${insight.relevanceWindow}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (insight.bodyMarkdown.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = insight.bodyMarkdown,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (showEvidence && !evidence.isEmpty) {
                    Spacer(Modifier.height(8.dp))
                    EvidenceSection(evidence = evidence)
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                InsightActionRow(
                    icon = Icons.Filled.ChatBubbleOutline,
                    label = "和 Aura 聊聊",
                    onClick = onChat,
                )
                InsightActionRow(
                    icon = Icons.Filled.VolumeOff,
                    label = "本周不聊 ${insight.category}",
                    onClick = { onMute(7) },
                )
                InsightActionRow(
                    icon = Icons.Filled.Visibility,
                    label = if (showEvidence) "隐藏依据" else "查看依据",
                    onClick = onShowEvidence,
                )
                InsightActionRow(
                    icon = Icons.Filled.Close,
                    label = "知道了",
                    onClick = onAcknowledge,
                )
            }
        },
        // 删掉 dismissButton 的"关闭" — 关闭入口上移到 title 栏右侧 ⨯,跟 MemoryDetailDialog 风格一致
    )
}

@Composable
private fun InsightActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .height(18.dp)
                .width(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun EvidenceSection(evidence: InsightEvidenceView) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = Color(0xFFF7F2EA),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "依据",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (evidence.messageIds.isNotEmpty()) {
                Text(
                    text = "消息：${evidence.messageIds.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (evidence.memoryIds.isNotEmpty()) {
                Text(
                    text = "记忆：${evidence.memoryIds.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (evidence.moodSnapshotIds.isNotEmpty()) {
                Text(
                    text = "情绪快照：${evidence.moodSnapshotIds.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
