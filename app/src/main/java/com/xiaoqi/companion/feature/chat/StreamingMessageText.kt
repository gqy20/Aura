package com.xiaoqi.companion.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
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
 * 4. 去掉原来 "$draftText..." 的字符串拼接,draftText 直接作为 text 渲染,
 *    GC 压力降低。
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
            // draft 裸走 Markdown 渲染,不再拼 "...";流式增量本身已是足够的光标提示,
            // 拼 "..." 会污染 markdown 解析(如 **未闭合** 后跟 ...)并让中文排版发紧。
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
    }
}
