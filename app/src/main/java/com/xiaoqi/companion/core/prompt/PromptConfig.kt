package com.xiaoqi.companion.core.prompt

data class PromptConfig(
    val name: String,
    val base: String,
    val sections: Map<String, SectionTemplate>,
    /**
     * Few-shot examples extracted from sections whose key starts with `examples_`.
     * The prefix is stripped, so the map key matches the example name (e.g. `reflection_save`).
     */
    val examples: Map<String, String> = emptyMap(),
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
