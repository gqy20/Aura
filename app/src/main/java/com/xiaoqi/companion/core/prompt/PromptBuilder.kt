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
    /**
     * 本地 LLM 路径专用：动态后缀（emotion/relationship/memories/summaries/recent/location），
     * 由 ReactiveCompanion 拼到 userMessage 前，保证 systemPrompt 仅含固定部分（人设 + 工具说明），
     * 从而让 MNN prefix cache 持久命中。云端路径始终为 null/empty。
     */
    val dynamicContext: String? = null,
)

/**
 * 本地路径判断工具：systemPrompt 是否只含固定部分（即 base + tools）。
 * 用于 ReactiveCompanion 区分新旧两种 prompt 结构。
 */
fun BuiltPrompt.hasSplitDynamicContext(): Boolean = !dynamicContext.isNullOrEmpty()

class PromptBuilder {

    fun build(
        input: UserInput,
        emotionContext: String? = null,
        relationshipContext: String? = null,
        recentConversation: List<String> = emptyList(),
        memories: List<String> = emptyList(),
        summaries: List<String> = emptyList(),
        locationContext: String? = null,
        /**
         * 本地 LLM 路径专用：true 时 systemPrompt 仅含 base + tools（固定，命中 prefix cache），
         * 动态部分（emotion/relationship/memories/summaries/recent/location）放进 [BuiltPrompt.dynamicContext]，
         * 由 ReactiveCompanion 拼到 userMessage 前面。
         *
         * 云端路径保持 false（默认）：systemPrompt = 固定 + 动态 全量拼接，dynamicContext = null。
         */
        splitForCache: Boolean = false,
    ): BuiltPrompt {
        val systemPrompt = if (splitForCache) {
            buildCacheableSystemPrompt()
        } else {
            buildSystemPrompt(
                emotionContext = emotionContext,
                relationshipContext = relationshipContext,
                recentConversation = recentConversation,
                memories = memories,
                summaries = summaries,
                locationContext = locationContext,
            )
        }
        val dynamicContext = if (splitForCache) {
            buildDynamicContext(
                emotionContext = emotionContext,
                relationshipContext = relationshipContext,
                recentConversation = recentConversation,
                memories = memories,
                summaries = summaries,
                locationContext = locationContext,
            ).takeIf { it.isNotBlank() }
        } else {
            null
        }
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
            "splitForCache" to splitForCache,
            "dynamicContextLength" to (dynamicContext?.length ?: 0),
        )
        return when (input) {
            is UserInput.Text -> BuiltPrompt(systemPrompt, input.content, dynamicContext = dynamicContext)
            is UserInput.Vision -> BuiltPrompt(
                systemPrompt = systemPrompt,
                userMessage = input.text,
                hasImage = true,
                imageBase64 = input.imageBase64,
                imageMediaType = input.mediaType,
                dynamicContext = dynamicContext,
            )
            is UserInput.Speech -> BuiltPrompt(systemPrompt, input.transcript, dynamicContext = dynamicContext)
        }
    }

    /**
     * 本地路径：固定前缀，仅 base 人设 + tools 工具说明，永远命中 prefix cache。
     */
    private fun buildCacheableSystemPrompt(): String {
        val sb = StringBuilder(SystemPersona.base)
        if (SystemPersona.toolsSectionTemplate.isNotEmpty()) {
            sb.append(replacePlaceholders(SystemPersona.toolsSectionTemplate, emptyMap()))
        }
        return sb.toString()
    }

    /**
     * 本地路径：动态后缀，由 ReactiveCompanion 拼到 userMessage 前面，
     * 每轮重新 prefill 但很短（emotion + relationship + memories + summaries + recent + location）。
     */
    private fun buildDynamicContext(
        emotionContext: String?,
        relationshipContext: String?,
        recentConversation: List<String>,
        memories: List<String>,
        summaries: List<String>,
        locationContext: String?,
    ): String {
        val sb = StringBuilder()
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
        return sb.toString()
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
