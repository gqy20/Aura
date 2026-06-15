package com.xiaoqi.companion.core.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpServerConfigTest {

    @Test
    fun `resolvedUrl is empty for templated preset with blank key`() {
        val c = McpServerConfig(providerId = "amap", apiKey = "")
        assertEquals("", c.resolvedUrl)
    }

    @Test
    fun `resolvedUrl is amap template when key is set`() {
        val c = McpServerConfig(providerId = "amap", apiKey = "k123")
        assertEquals("https://mcp.amap.com/mcp?key=k123", c.resolvedUrl)
    }

    @Test
    fun `resolvedUrl is custom url for custom preset`() {
        val c = McpServerConfig(providerId = "custom", customUrl = "https://x.com/mcp")
        assertEquals("https://x.com/mcp", c.resolvedUrl)
    }

    @Test
    fun `resolvedName falls back to preset displayName when displayName is blank`() {
        val c = McpServerConfig(providerId = "amap", displayName = "")
        assertEquals("高德地图", c.resolvedName)
    }

    @Test
    fun `resolvedName uses custom displayName when set`() {
        val c = McpServerConfig(providerId = "amap", displayName = "MyMap")
        assertEquals("MyMap", c.resolvedName)
    }

    @Test
    fun `isReady reflects whether resolvedUrl is non-blank`() {
        assertFalse(McpServerConfig(providerId = "amap", apiKey = "").isReady)
        assertTrue(McpServerConfig(providerId = "amap", apiKey = "k").isReady)
        assertTrue(McpServerConfig(providerId = "custom", customUrl = "https://x.com").isReady)
        assertFalse(McpServerConfig(providerId = "custom", customUrl = "").isReady)
    }

    @Test
    fun `default values are sane for a new server`() {
        val c = McpServerConfig()
        assertEquals("amap", c.providerId)
        assertEquals("", c.apiKey)
        assertEquals("", c.customUrl)
        assertTrue(c.enabled)
        assertTrue(c.id.isNotBlank())  // UUID 自动生成
    }

    @Test
    fun `copy preserves id by default`() {
        val c = McpServerConfig(id = "abc-123", apiKey = "k1")
        val c2 = c.copy(apiKey = "k2")
        assertEquals("abc-123", c2.id)
        assertEquals("k2", c2.apiKey)
    }
}
