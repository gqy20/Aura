package com.xiaoqi.companion.feature.insight

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

/**
 * 本地模型正在思考的动画指示器。
 *
 * 显示在 InsightCardList 上方,带脉冲动画的“本地模型正在思考...” 文案,
 * 让感知到本地模型正在后台工作。
 */
@Composable
internal fun InsightAnalyzingIndicator(
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "insight_analyzing")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = Color(0xFFF0F4FF),
        tonalElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = Color(0xFF4A6CF7).copy(alpha = alpha),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Aura 正在整理刚才的对话…",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF4A6CF7).copy(alpha = alpha),
            )
        }
    }
}
