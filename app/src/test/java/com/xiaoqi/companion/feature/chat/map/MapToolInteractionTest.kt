package com.xiaoqi.companion.feature.chat.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapToolInteractionTest {
    @Test
    fun `parse geo result from real amap payload`() {
        val result = "\"{\"results\":[{\"province\":\"浙江省\",\"city\":\"杭州市\",\"district\":\"西湖区\",\"location\":\"120.130396,30.259242\"}]}\""

        val interaction = MapToolInteractionParser.parse(
            toolName = "mcp__tool__maps_geo",
            argumentsJson = """{"address":"西湖","city":"杭州"}""",
            resultJson = result,
        ) as MapToolInteraction.Place

        assertEquals("西湖", interaction.name)
        assertEquals("浙江省, 杭州市, 西湖区", interaction.address)
        assertEquals(120.130396, interaction.coordinate.longitude, 0.0)
        assertTrue(MapLaunchUrlBuilder.amapPlace(interaction).startsWith("androidamap://viewMap"))
    }

    @Test
    fun `parse walking route with metrics`() {
        val interaction = MapToolInteractionParser.parse(
            toolName = "mcp__tool__maps_direction_walking",
            argumentsJson = """{"origin":"120.212600,30.290851","destination":"120.130396,30.259242"}""",
            resultJson = """{"route":{"origin":"120.212600,30.290851","destination":"120.130396,30.259242","paths":[{"distance":10106,"duration":8085}]}}""",
        ) as MapToolInteraction.Route

        assertEquals(MapTravelMode.WALKING, interaction.travelMode)
        assertEquals(10106L, interaction.distanceMeters)
        assertEquals(8085L, interaction.durationSeconds)
        assertTrue(MapLaunchUrlBuilder.amapRoute(interaction).contains("t=2"))
        assertTrue(MapLaunchUrlBuilder.webRoute(interaction).contains("mode=walk"))
    }

    @Test
    fun `ignore non-map and malformed coordinates`() {
        assertNull(MapToolInteractionParser.parse("get_current_time", "{}", "{}"))
        assertNull(
            MapToolInteractionParser.parse(
                "maps_geo",
                """{"address":"未知"}""",
                """{"results":[{"location":"invalid"}]}""",
            )
        )
    }

    @Test
    fun `reroute prompt keeps edited endpoints and mode`() {
        val prompt = MapRoutePromptBuilder.build(
            MapRouteDraft(origin = "杭州东站", destination = "西湖", travelMode = MapTravelMode.TRANSIT)
        )

        assertTrue(prompt.contains("起点：杭州东站"))
        assertTrue(prompt.contains("终点：西湖"))
        assertTrue(prompt.contains("出行方式：公共交通"))
        assertTrue(prompt.contains("调用地图工具"))
    }
}
