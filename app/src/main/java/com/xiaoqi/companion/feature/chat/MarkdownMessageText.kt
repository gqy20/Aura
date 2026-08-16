package com.xiaoqi.companion.feature.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp

@Composable
fun MarkdownMessageText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val displayText = remember(text) { text.sanitizeDisplayMarkdown() }
    val blocks = remember(displayText) { parseMarkdownBlocks(displayText) }
    val linkColor = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Code -> MarkdownCodeBlock(text = block.text)
                MarkdownBlock.Divider -> MarkdownDivider()
                is MarkdownBlock.Heading -> Text(
                    text = remember(block.text, linkColor) {
                        parseInlineMarkdown(block.text.removeControlFragments(), linkColor)
                    },
                    color = color,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(top = 4.dp),
                )
                is MarkdownBlock.Text -> Text(
                    text = remember(block.text, linkColor) {
                        parseInlineMarkdown(block.text.removeControlFragments(), linkColor)
                    },
                    color = color,
                    style = style,
                )
            }
        }
    }
}

private fun String.sanitizeDisplayMarkdown(): String =
    lineSequence()
        .mapNotNull { line ->
            val trimmed = line.trim().trimStart('\uFEFF', '\u200B', '\u200C', '\u200D')
            when {
                trimmed.startsWith(":") -> null
                trimmed == ">" -> null
                trimmed.startsWith("> ") -> trimmed.removePrefix("> ").trimStart()
                else -> line
            }
        }
        .joinToString("\n")

private fun String.removeControlFragments(): String =
    replace(Regex(""":[A-Za-z_]+\}"""), "")
        .replace(Regex(""":[A-Za-z_]+\]\(async:\d+\)`?"""), "")
        .lines()
        .filterNot { it.trim().isEmpty() }
        .joinToString("\n")

@Composable
fun MessageRenderBlockText(
    block: MessageRenderBlock,
    color: Color,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    when (block) {
        is MessageRenderBlock.Code -> MarkdownCodeBlock(text = block.text, modifier = modifier)
        is MessageRenderBlock.Text -> MarkdownMessageText(
            text = block.text,
            color = color,
            modifier = modifier,
            style = style,
        )
    }
}

@Composable
fun MarkdownCodeBlock(
    text: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.74f),
                shape = RoundedCornerShape(10.dp),
            )
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.padding(start = 10.dp, top = 8.dp, end = 44.dp, bottom = 8.dp),
        )
        IconButton(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("代码", text))
                Toast.makeText(context, "已复制代码", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .semantics { contentDescription = "复制代码" },
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(4.dp),
            )
        }
    }
}

@Composable
private fun MarkdownDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.42f)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)),
    )
}

private sealed interface MarkdownBlock {
    data class Text(val text: String) : MarkdownBlock
    data class Heading(val text: String) : MarkdownBlock
    data class Code(val text: String) : MarkdownBlock
    data object Divider : MarkdownBlock
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

    raw.lines().forEach { rawLine ->
        val line = rawLine.displayMarkdownLine() ?: return@forEach

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
        } else if (line.trim() == "---") {
            flushParagraph()
            blocks += MarkdownBlock.Divider
        } else if (line.trimStart().startsWith("### ")) {
            flushParagraph()
            blocks += MarkdownBlock.Heading(line.trimStart().removePrefix("### ").trim())
        } else if (line.trimStart().startsWith("## ")) {
            flushParagraph()
            blocks += MarkdownBlock.Heading(line.trimStart().removePrefix("## ").trim())
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

private fun String.displayMarkdownLine(): String? {
    val trimmed = trim()
    if (trimmed.isEmpty()) return this
    if (trimmed == ">") return null
    if (trimmed.startsWith(":") && (trimmed.endsWith("}") || trimmed.contains("](async:"))) {
        return null
    }
    return if (trimmed.startsWith("> ")) {
        trimmed.removePrefix("> ").trimStart()
    } else {
        this
    }
}

private fun parseInlineMarkdown(raw: String, linkColor: Color): AnnotatedString =
    buildAnnotatedString {
        var index = 0
        while (index < raw.length) {
            when {
                // [label](http…) → 可点击链接;url 非空白且 http 开头才匹配,
                // 避免 `](async:n)` 之类协议残渣被当成链接
                raw[index] == '[' -> {
                    val closeBracket = raw.indexOf("](", startIndex = index + 1)
                    val closeParen = if (closeBracket > index) raw.indexOf(')', startIndex = closeBracket + 2) else -1
                    val label = if (closeParen > closeBracket) raw.substring(index + 1, closeBracket) else ""
                    val url = if (closeParen > closeBracket) raw.substring(closeBracket + 2, closeParen) else ""
                    if (closeParen > closeBracket && label.isNotBlank() && url.startsWith("http")) {
                        withLink(
                            LinkAnnotation.Url(
                                url = url,
                                styles = TextLinkStyles(
                                    style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                                ),
                            )
                        ) {
                            append(label)
                        }
                        index = closeParen + 1
                    } else {
                        append(raw[index])
                        index++
                    }
                }

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
