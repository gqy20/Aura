package com.xiaoqi.companion.core.prompt

import android.content.Context
import java.io.InputStream

object PromptConfigLoader {

    private const val ASSET_PATH = "prompts/system_persona.yml"

    private var cached: PromptConfig? = null

    fun load(context: Context): PromptConfig {
        cached?.let { return it }
        val config = parse(context.assets.open(ASSET_PATH))
        cached = config
        return config
    }

    fun reload(context: Context) {
        cached = null
        load(context)
    }

    internal fun parse(input: InputStream): PromptConfig {
        val lines = input.bufferedReader().readLines()
        return parseLines(lines)
    }

    internal fun parseLines(lines: List<String>): PromptConfig {
        val builder = ConfigBuilder()
        var multilineKey: String? = null
        var multilineSectionName: String? = null
        var multilineBuffer = StringBuilder()
        var multilineIndent = 0
        var inMultiline = false
        var sectionName: String? = null

        var i = 0
        while (i < lines.size) {
            val rawLine = lines[i]
            val line = rawLine.replace("\r", "")

            // Blank lines inside multiline blocks preserve formatting
            if (line.isBlank()) {
                if (inMultiline) multilineBuffer.appendLine()
                i++
                continue
            }
            // Skip comments
            if (line.startsWith("#")) {
                i++
                continue
            }

            val indent = line.takeWhile { it == ' ' }.length

            if (inMultiline) {
                // Still inside multiline block?
                if (indent >= multilineIndent) {
                    multilineBuffer.appendLine(line.trimStart())
                    i++
                    continue
                }
                // Multiline block ended — flush and reprocess this line
                builder.setMultiline(
                    section = multilineSectionName,
                    key = multilineKey!!,
                    value = multilineBuffer.toString().trimEnd('\n'),
                )
                inMultiline = false
                multilineKey = null
                multilineSectionName = null
                // Don't increment i; fall through to process lines[i] as normal
            }

            // Normal key-value parsing
            val colonIndex = line.indexOf(':')
            if (colonIndex < 0) { i++; continue }

            val key = line.substring(0, colonIndex).trim()
            val rest = line.substring(colonIndex + 1).trim()

            when {
                // "sections:" map opener
                key == "sections" -> {
                    sectionName = ""
                }

                // Section name entry (e.g. "emotion:")
                sectionName != null && indent == 2 && rest.isEmpty() -> {
                    sectionName = key
                }

                // Section multi-line field (e.g. placeholder: |)
                sectionName != null && indent >= 4 && key in listOf("title", "placeholder") && rest == "|" -> {
                    multilineKey = key
                    multilineSectionName = sectionName
                    multilineBuffer = StringBuilder()
                    multilineIndent = indent + 2
                    inMultiline = true
                }

                // Section field (4+ spaces under a section name)
                sectionName != null && indent >= 4 -> {
                    if (key in listOf("title", "placeholder") && rest.isNotEmpty()) {
                        builder.setSectionField(sectionName, key, rest.unquote())
                    }
                }

                // Multi-line scalar ("key: |")
                rest == "|" -> {
                    multilineKey = key
                    multilineSectionName = null
                    multilineBuffer = StringBuilder()
                    multilineIndent = indent + 2
                    inMultiline = true
                }

                // Simple scalar ("key: value")
                else -> {
                    if (sectionName.isNullOrEmpty()) {
                        builder.setTopLevel(key, rest.unquote())
                    } else {
                        sectionName = null
                    }
                }
            }
            i++
        }

        // Flush trailing multiline if file ends mid-block
        if (inMultiline && multilineKey != null) {
            builder.setMultiline(
                section = multilineSectionName,
                key = multilineKey,
                value = multilineBuffer.toString().trimEnd('\n'),
            )
        }

        return builder.build()
    }

    private fun String.unquote(): String =
        removeSurrounding("\"").removeSurrounding("'")

    private class ConfigBuilder {
        var name = "Companion"
        var base = ""
        val sections = mutableMapOf<String, MutableMap<String, String>>()

        fun setTopLevel(key: String, value: String) {
            when (key) {
                "name" -> name = value
                "base" -> base = value
            }
        }

        fun setSectionField(section: String, field: String, value: String) {
            sections.getOrPut(section) { mutableMapOf() }[field] = value
        }

        fun setMultiline(section: String?, key: String, value: String) {
            if (section != null) {
                setSectionField(section, key, value)
            } else {
                setTopLevel(key, value)
            }
        }

        fun build(): PromptConfig {
            val parsedSections = sections.mapValues { (_, fields) ->
                PromptConfig.SectionTemplate(
                    title = fields["title"] ?: "",
                    placeholder = fields["placeholder"] ?: "",
                )
            }
            return PromptConfig(name = name, base = base, sections = parsedSections)
        }
    }
}
