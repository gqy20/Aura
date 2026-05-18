package com.xiaoqi.companion.core.prompt.templates

import android.content.Context
import com.xiaoqi.companion.core.prompt.PromptConfigLoader

object SystemPersona {

    var name: String = "Companion"
        private set

    var base: String = ""
        private set

    var emotionSectionTemplate: String = ""
        private set

    var relationshipSectionTemplate: String = ""
        private set

    var memorySectionTemplate: String = ""
        private set

    var toolsSectionTemplate: String = ""
        private set

    var isInitialized: Boolean = false
        private set

    fun init(context: Context) {
        val config = PromptConfigLoader.load(context)
        applyConfig(config)
    }

    fun reload(context: Context) {
        PromptConfigLoader.reload(context)
        init(context)
    }

    internal fun initForTesting(config: com.xiaoqi.companion.core.prompt.PromptConfig) {
        applyConfig(config)
    }

    private fun applyConfig(config: com.xiaoqi.companion.core.prompt.PromptConfig) {
        name = config.name
        base = config.base.replace("{{name}}", config.name)
        emotionSectionTemplate = buildSectionRaw(config, "emotion")
        relationshipSectionTemplate = buildSectionRaw(config, "relationship")
        memorySectionTemplate = buildSectionRaw(config, "memory")
        toolsSectionTemplate = buildSectionRaw(config, "tools")
        isInitialized = true
    }

    private fun buildSectionRaw(config: com.xiaoqi.companion.core.prompt.PromptConfig, key: String): String {
        val section = config.sections[key] ?: return ""
        return buildString {
            appendLine()
            appendLine("## ${section.title}")
            append(section.placeholder)
        }
    }

    private fun renderSection(config: com.xiaoqi.companion.core.prompt.PromptConfig, key: String): String =
        config.sections[key]?.render("") ?: ""
}
