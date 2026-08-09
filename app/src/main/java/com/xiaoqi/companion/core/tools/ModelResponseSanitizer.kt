package com.xiaoqi.companion.core.tools

private val TOOL_PROTOCOL_BLOCK = Regex(
    pattern = """<\s*(tool_result|tool_call)\b[^>]*>.*?(?:<\s*/\s*\1\s*>|$)""",
    options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

internal fun String.withoutToolProtocolArtifacts(): String =
    TOOL_PROTOCOL_BLOCK.replace(this, "").trimEnd()
