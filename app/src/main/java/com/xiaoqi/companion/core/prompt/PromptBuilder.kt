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
)

class PromptBuilder {

    fun build(
        input: UserInput,
        emotionContext: String? = null,
        relationshipContext: String? = null,
        memories: List<String> = emptyList(),
    ): BuiltPrompt {
        val systemPrompt = buildSystemPrompt(emotionContext, relationshipContext, memories)
        AppLogger.debug(
            LogTags.Prompt,
            "prompt_built",
            "systemLength" to systemPrompt.length,
            "hasEmotion" to !emotionContext.isNullOrBlank(),
            "hasRelation" to !relationshipContext.isNullOrBlank(),
            "memoryCount" to memories.size,
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
        memories: List<String>,
    ): String {
        val sb = StringBuilder(SystemPersona.base)

        emotionContext?.let {
            sb.append(SystemPersona.emotionSectionTemplate.replace("{emotion_context}", it))
        }

        relationshipContext?.let {
            sb.append(SystemPersona.relationshipSectionTemplate.replace("{relationship_context}", it))
        }

        if (memories.isNotEmpty()) {
            val memoryText = memories.joinToString("\n")
            sb.append(SystemPersona.memorySectionTemplate.replace("{memories}", memoryText))
        }

        return sb.toString()
    }
}
