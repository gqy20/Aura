package com.xiaoqi.companion.feature.chat

import androidx.annotation.VisibleForTesting

private const val SOFT_TEXT_BLOCK_LIMIT = 640

data class StreamingMessageRenderState(
    val committedBlocks: List<MessageRenderBlock> = emptyList(),
    val draftText: String = "",
    val isDraftCode: Boolean = false,
    val rawText: String = "",
)

sealed interface MessageRenderBlock {
    val text: String

    data class Text(override val text: String) : MessageRenderBlock
    data class Code(override val text: String) : MessageRenderBlock
}

class StreamingMarkdownChunker {
    private val committedBlocks = mutableListOf<MessageRenderBlock>()
    private val rawText = StringBuilder()
    private val pendingLine = StringBuilder()
    private val textBlock = StringBuilder()
    private val codeBlock = StringBuilder()
    private var inCodeBlock = false
    // P2: 增量 append-only 解析优化——state 实例缓存 + committedBlocks list 引用稳定
    // 让上游 ChatMessage.copy() 在 rawText/draftText 未变时引用等、跳过 Compose 重组
    private var cachedState: StreamingMessageRenderState? = null
    private var cachedBlocksSnapshot: List<MessageRenderBlock>? = null

    fun append(delta: String): StreamingMessageRenderState {
        if (delta.isEmpty()) return state()
        rawText.append(delta)
        pendingLine.append(delta)
        processCompleteLines()
        maybeSoftCommitText()
        return state()
    }

    fun complete(finalText: String): List<MessageRenderBlock> {
        clear()
        append(finalText)
        if (inCodeBlock) {
            commitCode()
            inCodeBlock = false
        } else {
            if (pendingLine.isNotEmpty()) {
                appendTextLine(pendingLine.toString())
                pendingLine.clear()
            }
            commitText()
        }
        return committedBlocks.toList()
    }

    /**
     * 测试入口：直接读取当前 state（不通过 append/empty 路径触发）。
     * 用于验证"连续两次 state() 调用是否返回同一 instance"等缓存行为。
     */
    @VisibleForTesting
    internal fun stateForTest(): StreamingMessageRenderState = state()

    private fun processCompleteLines() {
        while (true) {
            val newlineIndex = pendingLine.indexOf('\n')
            if (newlineIndex < 0) return
            val line = pendingLine.substring(0, newlineIndex).trimEnd('\r')
            pendingLine.delete(0, newlineIndex + 1)
            processLine(line)
        }
    }

    private fun processLine(line: String) {
        if (line.trimStart().startsWith("```")) {
            if (inCodeBlock) {
                commitCode()
                inCodeBlock = false
            } else {
                commitText()
                inCodeBlock = true
            }
            return
        }

        if (inCodeBlock) {
            codeBlock.append(line).append('\n')
        } else if (line.isBlank()) {
            commitText()
        } else {
            appendTextLine(line.trim())
        }
    }

    private fun appendTextLine(line: String) {
        if (textBlock.isNotEmpty()) textBlock.append('\n')
        textBlock.append(line)
    }

    private fun maybeSoftCommitText() {
        if (inCodeBlock || textBlock.length < SOFT_TEXT_BLOCK_LIMIT || pendingLine.isNotEmpty()) return
        commitText()
    }

    private fun commitText() {
        val text = textBlock.toString().trim()
        if (text.isNotEmpty()) committedBlocks += MessageRenderBlock.Text(text)
        textBlock.clear()
        // commit 后必须 invalidate blocks snapshot,否则下次 state() 会拿到过期的 list
        cachedBlocksSnapshot = null
    }

    private fun commitCode() {
        committedBlocks += MessageRenderBlock.Code(codeBlock.toString().trimEnd())
        codeBlock.clear()
        cachedBlocksSnapshot = null
    }

    private fun state(): StreamingMessageRenderState {
        // Blocks 引用稳定:连续 state() 在无 commit 时复用上次 toList() 引用
        val blocksSnapshot = cachedBlocksSnapshot
            ?: committedBlocks.toList().also { cachedBlocksSnapshot = it }
        val draft = draftText()
        val isCode = inCodeBlock
        val raw = rawText.toString()
        // State 实例缓存:4 字段全等时返回 cached instance
        val cached = cachedState
        if (cached != null &&
            cached.committedBlocks === blocksSnapshot &&
            cached.draftText == draft &&
            cached.isDraftCode == isCode &&
            cached.rawText == raw
        ) {
            return cached
        }
        val next = StreamingMessageRenderState(
            committedBlocks = blocksSnapshot,
            draftText = draft,
            isDraftCode = isCode,
            rawText = raw,
        )
        cachedState = next
        return next
    }

    private fun draftText(): String =
        if (inCodeBlock) {
            codeBlock.toString() + pendingLine.toString()
        } else {
            buildString {
                append(textBlock)
                if (isNotEmpty() && pendingLine.isNotEmpty()) append('\n')
                append(pendingLine)
            }
        }

    private fun clear() {
        committedBlocks.clear()
        rawText.clear()
        pendingLine.clear()
        textBlock.clear()
        codeBlock.clear()
        inCodeBlock = false
        cachedState = null
        cachedBlocksSnapshot = null
    }
}
