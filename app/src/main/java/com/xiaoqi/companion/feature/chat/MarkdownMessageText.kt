package com.xiaoqi.companion.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun MarkdownMessageText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Code -> CodeBlock(text = block.text)
                is MarkdownBlock.Text -> Text(
                    text = remember(block.text) { parseInlineMarkdown(block.text) },
                    color = color,
                    style = style,
                )
            }
        }
    }
}

@Composable
private fun CodeBlock(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.74f),
                shape = RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

private sealed interface MarkdownBlock {
    data class Text(val text: String) : MarkdownBlock
    data class Code(val text: String) : MarkdownBlock
}

private fun parseMarkdownBlocks(raw: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraph = StringBuilder()
    val code = StringBuilder()
    var inCode = false

    fun flushParagraph() {
        val text = paragraph.toString().trim()
        if (text.isNotEmpty()) blocks += MarkdownBlock.Text(text)
        paragraph.clear()
    }

    fun flushCode() {
        blocks += MarkdownBlock.Code(code.toString().trimEnd())
        code.clear()
    }

    raw.lines().forEach { line ->
        if (line.trimStart().startsWith("```")) {
            if (inCode) {
                flushCode()
                inCode = false
            } else {
                flushParagraph()
                inCode = true
            }
            return@forEach
        }

        if (inCode) {
            code.appendLine(line)
        } else if (line.isBlank()) {
            flushParagraph()
        } else {
            if (paragraph.isNotEmpty()) paragraph.append('\n')
            paragraph.append(normalizeListMarker(line.trim()))
        }
    }

    if (inCode) flushCode() else flushParagraph()
    return blocks.ifEmpty { listOf(MarkdownBlock.Text("")) }
}

private fun normalizeListMarker(line: String): String =
    when {
        line.startsWith("- ") -> "• ${line.removePrefix("- ")}"
        line.startsWith("* ") -> "• ${line.removePrefix("* ")}"
        else -> line
    }

private fun parseInlineMarkdown(raw: String): AnnotatedString =
    buildAnnotatedString {
        var index = 0
        while (index < raw.length) {
            when {
                raw.startsWith("**", index) -> {
                    val end = raw.indexOf("**", startIndex = index + 2)
                    if (end > index) {
                        appendStyled(raw.substring(index + 2, end), SpanStyle(fontWeight = FontWeight.SemiBold))
                        index = end + 2
                    } else {
                        append(raw[index])
                        index++
                    }
                }

                raw[index] == '`' -> {
                    val end = raw.indexOf('`', startIndex = index + 1)
                    if (end > index) {
                        appendStyled(raw.substring(index + 1, end), SpanStyle(fontFamily = FontFamily.Monospace))
                        index = end + 1
                    } else {
                        append(raw[index])
                        index++
                    }
                }

                raw[index] == '*' -> {
                    val end = raw.indexOf('*', startIndex = index + 1)
                    if (end > index) {
                        appendStyled(raw.substring(index + 1, end), SpanStyle(fontStyle = FontStyle.Italic))
                        index = end + 1
                    } else {
                        append(raw[index])
                        index++
                    }
                }

                else -> {
                    append(raw[index])
                    index++
                }
            }
        }
    }

private fun AnnotatedString.Builder.appendStyled(text: String, style: SpanStyle) {
    val start = length
    append(text)
    addStyle(style, start, length)
}
