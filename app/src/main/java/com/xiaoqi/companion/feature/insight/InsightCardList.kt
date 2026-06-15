package com.xiaoqi.companion.feature.insight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiaoqi.companion.feature.chat.ChatInsight

/**
 * 主页 Insight 卡片 Section。
 *
 * 仅当 `insights.isNotEmpty()` 时渲染,显示 "Aura 注意到..." 标题 + 最多 [limit] 张卡片。
 */
@Composable
internal fun InsightCardList(
    insights: List<ChatInsight>,
    limit: Int = 3,
    onInsightClick: (ChatInsight) -> Unit,
    onInsightLongPress: (ChatInsight) -> Unit,
    onInsightDismiss: (ChatInsight) -> Unit,
    onInsightChat: (ChatInsight) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (insights.isEmpty()) return
    val visible = insights.take(limit)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "Aura 注意到",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
        )
        Spacer(Modifier.height(4.dp))
        visible.forEach { insight ->
            InsightCard(
                insight = insight,
                onClick = { onInsightClick(insight) },
                onLongPress = { onInsightLongPress(insight) },
                onDismiss = { onInsightDismiss(insight) },
                onChat = { onInsightChat(insight) },
            )
        }
    }
}
