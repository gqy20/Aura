package com.xiaoqi.companion.core.prompt

import com.xiaoqi.companion.core.companion.model.UserInput
import com.xiaoqi.companion.core.prompt.templates.SystemPersona

data class BuiltPrompt(
    val systemPrompt: String,
    val userMessage: String,
    val hasImage: Boolean = false,
    val imageBase64: String? = null,
    val imageMediaType: String? = null,
)

class PromptBuilder {

    fun build(
        input: UserInput,
        emotionContext: String? = null,
        relationshipContext: String? = null,
        memories: List<String> = emptyList(),
    ): BuiltPrompt {
        val systemPrompt = buildSystemPrompt(emotionContext, relationshipContext, memories)
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
        memories: List<String>,
    ): String {
        val sb = StringBuilder(SystemPersona.BASE)

        emotionContext?.let {
            sb.append(SystemPersona.EMOTION_SECTION_TEMPLATE.replace("{emotion_context}", it))
        }

        relationshipContext?.let {
            sb.append(SystemPersona.RELATIONSHIP_SECTION_TEMPLATE.replace("{relationship_context}", it))
        }

        if (memories.isNotEmpty()) {
            val memoryText = memories.joinToString("\n")
            sb.append(SystemPersona.MEMORY_SECTION_TEMPLATE.replace("{memories}", memoryText))
        }

        return sb.toString()
    }
}
