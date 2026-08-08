package com.xiaoqi.companion.core.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpServerRouterTest {
    private val amap = McpServerConfig(
        id = "amap",
        displayName = "高德地图",
        providerId = "amap",
        apiKey = "key",
    )
    private val rail = custom("rail", "12306", "https://example.com/rail")
    private val bing = custom("bing", "必应搜索", "https://example.com/bing")
    private val luckin = custom("luckin", "瑞幸", "https://example.com/luckin")
    private val servers = listOf(amap, rail, bing, luckin)

    @Test
    fun mapQuerySelectsOnlyMapServer() {
        val selected = McpServerRouter.select("帮我查西湖附近的咖啡店和步行路线", servers)

        assertEquals(listOf("amap"), selected.map { it.id })
    }

    @Test
    fun unrelatedConversationSkipsRemoteServers() {
        assertTrue(McpServerRouter.select("我今天心情不太好", servers).isEmpty())
    }

    @Test
    fun explicitServiceQueriesSelectTheirOwnServers() {
        assertEquals(listOf("rail"), McpServerRouter.select("查一下 12306 余票", servers).map { it.id })
        assertEquals(listOf("bing"), McpServerRouter.select("用必应搜索最新新闻", servers).map { it.id })
        assertEquals(listOf("luckin"), McpServerRouter.select("瑞幸有什么咖啡", servers).map { it.id })
    }

    @Test
    fun naturalEnglishRouteQuerySelectsMapServer() {
        val selected = McpServerRouter.select("Walk route from Hangzhou East to West Lake", servers)

        assertEquals(listOf("amap"), selected.map { it.id })
    }

    private fun custom(id: String, name: String, url: String) = McpServerConfig(
        id = id,
        displayName = name,
        providerId = "custom",
        customUrl = url,
    )
}
