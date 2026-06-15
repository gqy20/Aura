package com.xiaoqi.companion.core.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McpServerPresetsTest {

    // --- byId ---------------------------------------------------------------

    @Test
    fun `byId returns Amap preset for amap id`() {
        val p = McpServerPresets.byId("amap")
        assertEquals("amap", p.id)
        assertTrue(p is TemplatedMcpServerPreset)
    }

    @Test
    fun `byId returns Custom preset for unknown id`() {
        val p = McpServerPresets.byId("not-a-preset")
        assertTrue(p is CustomMcpServerPreset)
    }

    @Test
    fun `byId returns Custom preset for null id`() {
        val p = McpServerPresets.byId(null)
        assertTrue(p is CustomMcpServerPreset)
    }

    // --- resolveUrl ---------------------------------------------------------

    @Test
    fun `Amap resolveUrl injects key into template`() {
        val url = McpServerPresets.Amap.resolveUrl("abc123", "")
        assertEquals("https://mcp.amap.com/mcp?key=abc123", url)
    }

    @Test
    fun `Amap resolveUrl trims surrounding whitespace in key`() {
        val url = McpServerPresets.Amap.resolveUrl("  abc123  ", "")
        assertEquals("https://mcp.amap.com/mcp?key=abc123", url)
    }

    @Test
    fun `Amap resolveUrl returns empty url when key is blank`() {
        // 空 key 表达"未配置",不返回半成品 URL
        assertEquals("", McpServerPresets.Amap.resolveUrl("", ""))
        assertEquals("", McpServerPresets.Amap.resolveUrl("   ", ""))
    }

    @Test
    fun `Custom resolveUrl returns trimmed customUrl`() {
        val url = McpServerPresets.Custom.resolveUrl("", "  https://example.com/mcp  ")
        assertEquals("https://example.com/mcp", url)
    }

    // --- resolveName --------------------------------------------------------

    @Test
    fun `Amap resolveName falls back to displayName when blank`() {
        assertEquals("高德地图", McpServerPresets.Amap.resolveName(""))
    }

    @Test
    fun `Amap resolveName uses custom name when present`() {
        assertEquals("MyAmap", McpServerPresets.Amap.resolveName("MyAmap"))
    }

    // --- detectFromUrl (老数据迁移: URL → preset) ---------------------------

    @Test
    fun `detectFromUrl returns Amap for amap-shaped url`() {
        val p = McpServerPresets.detectFromUrl("https://mcp.amap.com/mcp?key=xxx")
        assertEquals("amap", p.id)
    }

    @Test
    fun `detectFromUrl returns Custom for unrelated url`() {
        val p = McpServerPresets.detectFromUrl("https://other.example.com/mcp")
        assertTrue(p is CustomMcpServerPreset)
    }

    @Test
    fun `detectFromUrl returns Custom for blank input`() {
        val p = McpServerPresets.detectFromUrl("")
        assertTrue(p is CustomMcpServerPreset)
    }

    @Test
    fun `detectFromUrl returns Custom for null input`() {
        val p = McpServerPresets.detectFromUrl(null)
        assertTrue(p is CustomMcpServerPreset)
    }

    // --- extractApiKey (老数据迁移: URL → key) -------------------------------

    @Test
    fun `extractApiKey returns key from amap url`() {
        val key = McpServerPresets.extractApiKey(
            McpServerPresets.Amap,
            "https://mcp.amap.com/mcp?key=abc123",
        )
        assertEquals("abc123", key)
    }

    @Test
    fun `extractApiKey cuts at ampersand when url has extra query params`() {
        val key = McpServerPresets.extractApiKey(
            McpServerPresets.Amap,
            "https://mcp.amap.com/mcp?key=abc123&extra=foo",
        )
        assertEquals("abc123", key)
    }

    @Test
    fun `extractApiKey cuts at hash when url has fragment`() {
        val key = McpServerPresets.extractApiKey(
            McpServerPresets.Amap,
            "https://mcp.amap.com/mcp?key=abc123#section",
        )
        assertEquals("abc123", key)
    }

    @Test
    fun `extractApiKey returns null for Custom preset`() {
        val key = McpServerPresets.extractApiKey(
            McpServerPresets.Custom,
            "https://example.com/mcp",
        )
        assertNull(key)
    }

    @Test
    fun `extractApiKey returns null for null url`() {
        val key = McpServerPresets.extractApiKey(McpServerPresets.Amap, null)
        assertNull(key)
    }

    @Test
    fun `extractApiKey returns null for non-matching url`() {
        val key = McpServerPresets.extractApiKey(
            McpServerPresets.Amap,
            "https://other.com/mcp?key=abc",
        )
        assertNull(key)
    }

    // --- 注册表完整性 -------------------------------------------------------

    @Test
    fun `all list contains both presets`() {
        val ids = McpServerPresets.all.map { it.id }
        assertEquals(listOf("amap", "custom"), ids)
    }

    @Test
    fun `every preset has unique id`() {
        val ids = McpServerPresets.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }
}
