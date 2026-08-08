package com.xiaoqi.companion.core.mcp

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpToolSelectorTest {
    private val tools = listOf(
        tool("maps_geo", "Convert an address to coordinates"),
        tool("maps_regeocode", "Convert coordinates to an address"),
        tool("maps_ip_location", "Locate an IP address"),
        tool("maps_weather", "Query city weather"),
        tool("maps_search_detail", "Get POI details"),
        tool("maps_text_search", "Search POIs by keywords"),
        tool("maps_around_search", "Search nearby POIs around a location"),
        tool("maps_direction_walking", "Plan a walking route"),
        tool("maps_direction_bicycling", "Plan a cycling route"),
        tool("maps_direction_driving", "Plan a driving route"),
        tool("maps_direction_transit_integrated", "Plan a public transit route"),
        tool("maps_distance", "Calculate distance between coordinates"),
    )

    @Test
    fun nearbyWalkingQuery_selectsSmallRelevantSubset() {
        val selected = McpToolSelector.select("查西湖附近的咖啡店，再规划步行路线", tools)
        val names = selected.map { it.name }

        assertTrue(selected.size <= McpToolSelector.DEFAULT_LIMIT)
        assertTrue("maps_around_search" in names)
        assertTrue("maps_direction_walking" in names)
        assertTrue("maps_geo" in names)
        assertTrue("maps_weather" !in names)
        assertTrue("maps_ip_location" !in names)
        assertTrue(selected.sumOf { it.inputSchema.toString().length } < tools.sumOf { it.inputSchema.toString().length })
    }

    @Test
    fun specificIntent_prioritizesMatchingTool() {
        assertEquals("maps_weather", McpToolSelector.select("杭州今天会下雨吗，查一下天气", tools).first().name)
        assertEquals("maps_regeocode", McpToolSelector.select("120.1,30.2 这个坐标在哪", tools).first().name)
        assertEquals("maps_direction_driving", McpToolSelector.select("从公司开车回家怎么走", tools).first().name)
    }

    @Test
    fun vagueOrExplicitAllQuery_preservesFullCatalog() {
        assertEquals(tools, McpToolSelector.select("使用高德处理这个请求", tools))
        assertEquals(tools, McpToolSelector.select("列出全部工具", tools))
    }

    @Test
    fun selection_isDeterministicAndBounded() {
        val first = McpToolSelector.select("附近咖啡店", tools)

        repeat(100) {
            assertEquals(first, McpToolSelector.select("附近咖啡店", tools))
        }
        assertTrue(first.size <= McpToolSelector.DEFAULT_LIMIT)
    }

    private fun tool(name: String, description: String) = McpToolSpec(
        name = name,
        description = description,
        inputSchema = buildJsonObject {
            put("type", "object")
            put("descriptionPadding", "x".repeat(300))
        },
    )
}
