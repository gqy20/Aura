package com.xiaoqi.companion.core.prompt

import com.xiaoqi.companion.core.companion.model.UserInput
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.core.prompt.templates.SystemPersona

data class BuiltPrompt(
    val systemPrompt: String,
    val userMessage: String,
    val hasImage: Boolean = false,
    val imageBase64: String? = null,
    val imageMediaType: String? = null,
    val allowTools: Boolean = true,
)

class PromptBuilder {

    fun build(
        input: UserInput,
        emotionContext: String? = null,
        relationshipContext: String? = null,
        recentConversation: List<String> = emptyList(),
        memories: List<String> = emptyList(),
        summaries: List<String> = emptyList(),
        locationContext: String? = null,
    ): BuiltPrompt {
        val systemPrompt = buildSystemPrompt(
            emotionContext = emotionContext,
            relationshipContext = relationshipContext,
            recentConversation = recentConversation,
            memories = memories,
            summaries = summaries,
            locationContext = locationContext,
        )
        AppLogger.debug(
            LogTags.Prompt,
            "prompt_built",
            "systemLength" to systemPrompt.length,
            "hasEmotion" to !emotionContext.isNullOrBlank(),
            "hasRelation" to !relationshipContext.isNullOrBlank(),
            "recentConversationCount" to recentConversation.size,
            "memoryCount" to memories.size,
            "summaryCount" to summaries.size,
            "inputType" to input::class.simpleName,
        )
        return when (input) {
            is UserInput.Text -> BuiltPrompt(systemPrompt, input.content)
            is UserInput.Vision -> BuiltPrompt(
                systemPrompt = systemPrompt,
                userMessage = input.text,
                hasImage = true,
                imageBase64 = input.imageBase64,
                imageMediaType = input.mediaType,
            )
            is UserInput.Speech -> BuiltPrompt(systemPrompt, input.transcript)
        }
    }

    private fun buildSystemPrompt(
        emotionContext: String?,
        relationshipContext: String?,
        recentConversation: List<String>,
        memories: List<String>,
        summaries: List<String>,
        locationContext: String?,
    ): String {
        val sb = StringBuilder(SystemPersona.base)

        emotionContext?.let {
            sb.append(replacePlaceholders(SystemPersona.emotionSectionTemplate, mapOf("emotion_context" to it)))
        }

        relationshipContext?.let {
            sb.append(replacePlaceholders(SystemPersona.relationshipSectionTemplate, mapOf("relationship_context" to it)))
        }

        if (summaries.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("## ${SystemPersona.summariesTitle}")
            sb.append(summaries.joinToString("\n"))
        }

        if (recentConversation.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("## ${SystemPersona.recentTitle}")
            sb.append(recentConversation.joinToString("\n"))
        }

        if (memories.isNotEmpty()) {
            val memoryText = memories.joinToString("\n")
            sb.append(replacePlaceholders(SystemPersona.memorySectionTemplate, mapOf("memories" to memoryText)))
        }

        locationContext?.let {
            sb.append(replacePlaceholders(SystemPersona.locationSectionTemplate, mapOf("location_context" to it)))
        }

        if (SystemPersona.toolsSectionTemplate.isNotEmpty()) {
            sb.append(replacePlaceholders(SystemPersona.toolsSectionTemplate, emptyMap()))
        }

        return sb.toString()
    }

    /**
     * Replace `{{key}}` placeholders in [template]. Any placeholder whose key
     * is not provided is left in the output as a visible marker so we can
     * tell at a glance when the yml is missing a slot — better than silently
     * passing `{{memories}}` to the LLM.
     */
    private fun replacePlaceholders(template: String, values: Map<String, String>): String {
        if (template.isEmpty()) return template
        val regex = Regex("""\{\{([a-zA-Z0-9_]+)\}\}""")
        return regex.replace(template) { match ->
            val key = match.groupValues[1]
            values[key] ?: "[MISSING:$key]"
        }
    }
}
