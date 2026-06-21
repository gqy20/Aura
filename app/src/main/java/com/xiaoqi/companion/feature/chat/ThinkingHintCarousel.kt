package com.xiaoqi.companion.feature.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 首字到达前的安抚态：转圈 + 一行随时间升级语气的小字。
 *
 * 生命周期由调用方保证：仅在 `message.isStreaming && message.content.isBlank()` 时
 * 组合本 Composable，首字到达后调用方分支切走，本组件自动 dispose，无需手动清理。
 *
 * 节奏：首条立即出（避免白屏），之后每 [tickIntervalMs] 切一条；档位阈值见
 * [ThinkingHints]。换档瞬间立刻出该档第 0 条，让语气升级即时可感。
 *
 * @param tickIntervalMs 同档内文案轮播间隔，默认 2.4s
 */
@Composable
fun ThinkingHintCarousel(
    modifier: Modifier = Modifier,
    startedAt: Long = System.currentTimeMillis(),
    tickIntervalMs: Long = 2_400L,
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val current = produceState(
        initialValue = ThinkingHintState(elapsedMs = 0L, hint = ThinkingHints.hintFor(0L, 0)),
        startedAt, tickIntervalMs,
    ) {
        var stageIndex = ThinkingHints.stageIndexFor(0L)
        var indexInStage = 0
        while (true) {
            value = ThinkingHintState(
                elapsedMs = System.currentTimeMillis() - startedAt,
                hint = ThinkingHints.hintFor(System.currentTimeMillis() - startedAt, indexInStage),
            )
            kotlinx.coroutines.delay(tickIntervalMs)
            val nextStage = ThinkingHints.stageIndexFor(System.currentTimeMillis() - startedAt)
            if (nextStage != stageIndex) {
                stageIndex = nextStage
                indexInStage = 0
            } else {
                indexInStage++
            }
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AuraLoadingIndicator(
            modifier = Modifier.size(18.dp),
            color = indicatorColor,
        )
        Spacer(Modifier.width(8.dp))
        AnimatedContent(
            targetState = current.value.hint,
            transitionSpec = {
                fadeIn(tween(300)).togetherWith(fadeOut(tween(300)))
            },
            contentKey = { it },
            label = "thinking-hint",
        ) { hint ->
            Text(
                text = hint,
                color = textColor,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private data class ThinkingHintState(
    val elapsedMs: Long,
    val hint: String,
)
