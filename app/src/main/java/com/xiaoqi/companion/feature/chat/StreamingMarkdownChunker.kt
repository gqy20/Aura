package com.xiaoqi.companion.feature.chat

private const val SOFT_TEXT_BLOCK_LIMIT = 420

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
    }

    private fun commitCode() {
        committedBlocks += MessageRenderBlock.Code(codeBlock.toString().trimEnd())
        codeBlock.clear()
    }

    private fun state(): StreamingMessageRenderState =
        StreamingMessageRenderState(
            committedBlocks = committedBlocks.toList(),
            draftText = draftText(),
            isDraftCode = inCodeBlock,
            rawText = rawText.toString(),
        )

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
    }
}
