package com.xiaoqi.companion.core.prompt

data class PromptConfig(
    val name: String,
    val base: String,
    val sections: Map<String, SectionTemplate>,
) {
    data class SectionTemplate(
        val title: String,
        val placeholder: String,
    ) {
        fun render(context: String): String = buildString {
            appendLine()
            append("## $title")
            append(placeholder.replace("{{" + placeholder.removeSurrounding("{{", "}}") + "}}", context))
        }
    }
}
