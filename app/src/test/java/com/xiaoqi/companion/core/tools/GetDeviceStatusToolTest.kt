package com.xiaoqi.companion.core.tools

import com.xiaoqi.companion.core.context.DeviceStatus
import com.xiaoqi.companion.core.context.DeviceStatusProvider
import com.xiaoqi.companion.data.datastore.AppPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetDeviceStatusToolTest {

    @Test
    fun execute_returnsDeviceStatusWhenEnabled() = runTest {
        val tool = GetDeviceStatusTool(
            appPreferences = preferences(enabled = true),
            deviceStatusProvider = provider(),
        )

        val result = tool.execute(GetDeviceStatusTool.Args())

        assertTrue(result.contains(""""status":"ok""""))
        assertTrue(result.contains(""""batteryPercent":42"""))
        assertTrue(result.contains(""""isCharging":true"""))
        assertTrue(result.contains(""""networkType":"wifi""""))
    }

    @Test
    fun execute_returnsDisabledWhenSettingOff() = runTest {
        val tool = GetDeviceStatusTool(
            appPreferences = preferences(enabled = false),
            deviceStatusProvider = provider(),
        )

        val result = tool.execute(GetDeviceStatusTool.Args())

        assertTrue(result.contains(""""status":"error""""))
        assertTrue(result.contains("device_status_context_disabled"))
    }

    @Test
    fun descriptor_exposesKoogToolMetadata() {
        val tool = GetDeviceStatusTool(preferences(enabled = true), provider())

        assertEquals("get_device_status", tool.name)
        assertTrue(tool.descriptor.description.contains("device", ignoreCase = true))
    }

    private fun preferences(enabled: Boolean): AppPreferences =
        mockk {
            every { deviceStatusContextEnabled } returns flowOf(enabled)
        }

    private fun provider(): DeviceStatusProvider =
        object : DeviceStatusProvider {
            override fun getStatus() = DeviceStatus(
                batteryPercent = 42,
                isCharging = true,
                powerSaveMode = false,
                isOnline = true,
                networkType = "wifi",
            )
        }
}
