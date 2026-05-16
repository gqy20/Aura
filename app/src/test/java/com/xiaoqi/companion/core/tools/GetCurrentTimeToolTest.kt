package com.xiaoqi.companion.core.tools

import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetCurrentTimeToolTest {

    @Test
    fun execute_returnsCurrentTimeInDefaultTimezone() = runTest {
        val tool = GetCurrentTimeTool(
            nowProvider = { 1_700_000_000_000L },
            defaultZoneProvider = { ZoneId.of("Asia/Shanghai") },
        )

        val result = tool.execute(GetCurrentTimeTool.Args())

        assertTrue(result.contains(""""timezone":"Asia/Shanghai""""))
        assertTrue(result.contains(""""date":"2023-11-15""""))
        assertTrue(result.contains(""""time":"06:13:20""""))
        assertTrue(result.contains(""""dayOfWeek":"WEDNESDAY""""))
    }

    @Test
    fun execute_usesRequestedTimezoneWhenValid() = runTest {
        val tool = GetCurrentTimeTool(
            nowProvider = { 1_700_000_000_000L },
            defaultZoneProvider = { ZoneId.of("Asia/Shanghai") },
        )

        val result = tool.execute(GetCurrentTimeTool.Args(timezone = "UTC"))

        assertTrue(result.contains(""""timezone":"UTC""""))
        assertTrue(result.contains(""""date":"2023-11-14""""))
        assertTrue(result.contains(""""time":"22:13:20""""))
    }

    @Test
    fun descriptor_exposesKoogToolMetadata() {
        val tool = GetCurrentTimeTool()

        assertEquals("get_current_time", tool.name)
        assertTrue(tool.descriptor.description.contains("time", ignoreCase = true))
    }
}
