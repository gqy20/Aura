package com.xiaoqi.companion.core.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpSettingsSnapshotTest {

    @Test
    fun `from returns amap defaults when all inputs are blank`() {
        val s = McpSettingsSnapshot.from(null, null, null, null)
        assertEquals("amap", s.providerId)
        assertTrue(s.provider is TemplatedMcpServerPreset)
        assertEquals("", s.apiKey)
        assertEquals("高德地图", s.name)
        // url 空 = "未配置"(用户还没填 key),不是半成品
        assertEquals("", s.url)
    }

    @Test
    fun `from migrates amap url to provider and key when providerId is unset`() {
        val s = McpSettingsSnapshot.from(
            rawProviderId = null,
            rawApiKey = null,
            rawName = "OldAmap",
            rawUrl = "https://mcp.amap.com/mcp?key=abc123",
        )
        assertEquals("amap", s.providerId)
        assertEquals("abc123", s.apiKey)
        assertEquals("https://mcp.amap.com/mcp?key=abc123", s.url)
        assertEquals("OldAmap", s.name)
    }

    @Test
    fun `from migrates custom url to custom preset when providerId is unset`() {
        val s = McpSettingsSnapshot.from(
            rawProviderId = null,
            rawApiKey = null,
            rawName = null,
            rawUrl = "https://my-mcp.example.com/sse",
        )
        assertEquals("custom", s.providerId)
        assertTrue(s.provider is CustomMcpServerPreset)
        assertEquals("https://my-mcp.example.com/sse", s.url)
        assertEquals("", s.apiKey)
    }

    @Test
    fun `from preserves explicit provider choice over url detection`() {
        val s = McpSettingsSnapshot.from(
            rawProviderId = "custom",
            rawApiKey = null,
            rawName = null,
            rawUrl = "https://mcp.amap.com/mcp?key=abc123",
        )
        // 显式 provider=custom → 信任用户选择,即便 url 长得像 amap 也按 custom 处理
        assertEquals("custom", s.providerId)
        assertEquals("https://mcp.amap.com/mcp?key=abc123", s.url)
        assertEquals("", s.apiKey)
    }

    @Test
    fun `from uses explicit apiKey when provider is templated`() {
        val s = McpSettingsSnapshot.from(
            rawProviderId = "amap",
            rawApiKey = "mykey",
            rawName = null,
            rawUrl = "",
        )
        assertEquals("amap", s.providerId)
        assertEquals("mykey", s.apiKey)
        assertEquals("https://mcp.amap.com/mcp?key=mykey", s.url)
    }

    @Test
    fun `from falls back name to displayName when blank`() {
        val s = McpSettingsSnapshot.from("amap", "k", null, "")
        assertEquals("高德地图", s.name)
    }

    @Test
    fun `from keeps explicit custom name`() {
        val s = McpSettingsSnapshot.from("amap", "k", "MyAmap", "")
        assertEquals("MyAmap", s.name)
    }

    @Test
    fun `from does not migrate when providerId is explicitly blank but url is also blank`() {
        // url 为空时迁移无意义,直接按 providerId 走 (默认 amap)
        val s = McpSettingsSnapshot.from("", "", "", "")
        assertEquals("amap", s.providerId)
        assertEquals("", s.url)
    }
}
