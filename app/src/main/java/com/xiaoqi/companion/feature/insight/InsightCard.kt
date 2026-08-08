package com.xiaoqi.companion.feature.insight

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaoqi.companion.feature.chat.ChatInsight

/**
 * 单条 Insight 卡片(plan §4.2 样式)。
 *
 * 布局:headline + ⨯ IconButton → 类别图标 + 时间范围 → body (≤ 3 行)。
 *
 * 长按卡片 → 触发 [onLongPress] 弹层(类别静音 / 知道了 / 查看依据)。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun InsightCard(
    insight: ChatInsight,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardColor = if (insight.triggerType == "POST_CHAT") {
        Color(0xFFF0F4FF)
    } else {
        Color(0xFFFFF8EA)
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = cardColor,
        tonalElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                role = Role.Button,
                onClickLabel = "和 Aura 聊聊",
                onClick = onClick,
                onLongClick = onLongPress,
            ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = insight.headline,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "知道了",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (insight.triggerType == "POST_CHAT") {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFF4A6CF7),
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "刚刚注意到",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4A6CF7),
                        fontSize = 11.sp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Lightbulb,
                        contentDescription = null,
                        tint = Color(0xFFE5A100),
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "${insight.category} · ${insight.relevanceWindow}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (insight.bodyMarkdown.isNotBlank()) {
                Text(
                    text = insight.bodyMarkdown,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}
