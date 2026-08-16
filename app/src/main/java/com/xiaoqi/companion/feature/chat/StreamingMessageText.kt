package com.xiaoqi.companion.feature.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 流式消息文本 Composable。独立于 [MessageBubble] 容器,使内容子树只依赖窄参数,
 * 减少流式期间 [MessageBubble] 整棵 Row+Surface+Column 的重组范围。
 *
 * 优化要点:
 * 1. 接收窄参数(blocks / draft / isCode / color),不接收整个 [ChatMessage]。
 *    容器层重组不强制内容子树的所有 Composable 重新创建。
 * 2. `renderBlocks.forEach` 包一层 `key(block.text.hashCode())`,已 commit 的 block
 *    跨帧复用 slot table,Text 节点不重建、不重新解析 Markdown。
 * 3. `renderDraft` 不加 key——活跃尾巴按设计每字符重组。
 * 4. draft 文本不拼接 "..." 之类的字符串光标(会污染 markdown 解析);
 *    "还在生成"的信号由独立的 [StreamingCaret] Composable 承担。
 *
 * 详细背景:见 docs/plan/lively-kindling-truffle.md (P1 阶段)。
 */
@Composable
fun StreamingMessageText(
    renderBlocks: List<MessageRenderBlock>,
    renderDraft: String,
    isRenderDraftCode: Boolean,
    contentFallback: String,
    color: Color,
) {
    val draftText = renderDraft.ifBlank {
        contentFallback.takeIf { renderBlocks.isEmpty() }.orEmpty()
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        renderBlocks.forEach { block ->
            key(block.text.hashCode()) {
                MessageRenderBlockText(
                    block = block,
                    color = color,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp),
                )
            }
        }
        if (draftText.isNotBlank()) {
            if (isRenderDraftCode) {
                MarkdownCodeBlock(text = draftText)
            } else {
                MarkdownMessageText(
                    text = draftText,
                    color = color,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp),
                )
            }
        }
        StreamingCaret(color = color)
    }
}

/** 呼吸竖条光标:暂停的流和完成的流必须可区分,这是最廉价的"活着"信号。 */
@Composable
private fun StreamingCaret(color: Color) {
    val transition = rememberInfiniteTransition(label = "streamingCaret")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(560, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "streamingCaretAlpha",
    )
    Spacer(
        modifier = Modifier
            .padding(top = 4.dp)
            .size(width = 3.dp, height = 16.dp)
            .clip(RoundedCornerShape(1.5.dp))
            .background(color.copy(alpha = alpha)),
    )
}
