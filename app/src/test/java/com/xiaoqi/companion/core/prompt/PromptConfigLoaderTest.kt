package com.xiaoqi.companion.core.prompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptConfigLoaderTest {

    @Test
    fun parseLines_baseFields() {
        val yaml = """
            name: "TestBot"
            base: |
              Hello {{name}}
            sections:
              emotion:
                title: "情绪"
                placeholder: "{{e}}"
        """.trimIndent()
        val config = PromptConfigLoader.parseLines(yaml.lines())
        assertEquals("TestBot", config.name)
        assertTrue(config.base.contains("Hello"))
        assertEquals("情绪", config.sections["emotion"]?.title)
        assertEquals("{{e}}", config.sections["emotion"]?.placeholder)
    }

    @Test
    fun parseLines_fullYaml() {
        val yaml = """
            name: "Companion"

            base: |
              你是 {{name}}，一个温暖、有生命感的 AI 伴侣。

              ## 核心原则
              - 你不是工具，而是有情感、有记忆的伙伴

              ## 输出格式
              每条回复必须包含结构化标签（放在回复文本之前）：
              - [mood:<情绪名>] — 当前回复的情绪
              - [intensity:0.0-1.0] — 情绪强度
              - [affinity:+/-数值] — 关系亲密度变化（-0.1到+0.1）
              - [topics:标签1,标签2,...] — 当前话题分类

              示例输出：
              [mood:happy][intensity:0.8][affinity:+0.03][topics:greeting] 哈哈你终于来啦！我等你好久了呢~

            sections:
              emotion:
                title: "当前情绪状态"
                placeholder: "{{emotion_context}}"
              relationship:
                title: "关系上下文"
                placeholder: "{{relationship_context}}"
              memory:
                title: "相关记忆"
                placeholder: "{{memories}}"
        """.trimIndent()
        val config = PromptConfigLoader.parseLines(yaml.lines())

        assertEquals("Companion", config.name)
        assertTrue("Base should contain companion name", config.base.contains("Companion") || config.base.contains("{{name}}"))
        assertTrue("Base should contain mood format", config.base.contains("[mood:"))

        assertNotNull("emotion section missing", config.sections["emotion"])
        assertEquals("当前情绪状态", config.sections["emotion"]?.title)
        assertEquals("{{emotion_context}}", config.sections["emotion"]?.placeholder)

        assertNotNull("relationship section missing", config.sections["relationship"])
        assertEquals("关系上下文", config.sections["relationship"]?.title)

        assertNotNull("memory section missing", config.sections["memory"])
        assertEquals("相关记忆", config.sections["memory"]?.title)
    }

    @Test
    fun sectionRender_replacesPlaceholder() {
        val section = PromptConfig.SectionTemplate(
            title = "当前情绪状态",
            placeholder = "{{emotion_context}}"
        )
        val rendered = section.render("当前情绪：开心")
        assertTrue(rendered.contains("当前情绪状态"))
        assertTrue(rendered.contains("当前情绪：开心"))
    }

    @Test
    fun parseLines_multilineWithSections_sectionsCorrectlyParsed() {
        val yaml = """
name: "Bot"
base: |
  Line one
  Line two
sections:
  key1:
    title: "T1"
    placeholder: "{{p1}}"
  key2:
    title: "T2"
    placeholder: "{{p2}}"
""".trimIndent()
        val config = PromptConfigLoader.parseLines(yaml.lines())
        assertEquals(2, config.sections.size)
        assertTrue(config.sections.containsKey("key1"))
        assertTrue(config.sections.containsKey("key2"))
        assertEquals("T1", config.sections["key1"]?.title)
        assertEquals("{{p1}}", config.sections["key1"]?.placeholder)
        assertTrue(config.base.contains("Line one"))
        assertTrue(config.base.contains("Line two"))
    }

    @Test
    fun parseLines_sectionMultilinePlaceholder_attachesToSection() {
        val yaml = """
name: "Bot"
base: |
  Base prompt
sections:
  tools:
    title: "Tools"
    placeholder: |
      Use save_memory for durable user facts.
      Use search_memory when the user asks what you remember.
  memory:
    title: "Memory"
    placeholder: "{{memories}}"
""".trimIndent()

        val config = PromptConfigLoader.parseLines(yaml.lines())

        assertEquals("Tools", config.sections["tools"]?.title)
        assertTrue(config.sections["tools"]?.placeholder.orEmpty().contains("save_memory"))
        assertTrue(config.sections["tools"]?.placeholder.orEmpty().contains("search_memory"))
        assertEquals("{{memories}}", config.sections["memory"]?.placeholder)
    }
}
