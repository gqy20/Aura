package com.xiaoqi.companion.core.tools

import com.xiaoqi.companion.core.context.ContextPermissionReader
import com.xiaoqi.companion.data.datastore.AppPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetUserContextSettingsToolTest {

    @Test
    fun execute_returnsSettingsAndPermissionState() = runTest {
        val tool = GetUserContextSettingsTool(
            appPreferences = preferences(),
            permissionReader = permissions(
                coarse = true,
                fine = false,
                notifications = true,
            ),
        )

        val result = tool.execute(GetUserContextSettingsTool.Args())

        assertTrue(result.contains(""""deviceStatusContextEnabled":true"""))
        assertTrue(result.contains(""""locationContextEnabled":true"""))
        assertTrue(result.contains(""""weatherContextEnabled":true"""))
        assertTrue(result.contains(""""reminderToolEnabled":true"""))
        assertTrue(result.contains(""""hasCoarseLocationPermission":true"""))
        assertTrue(result.contains(""""hasFineLocationPermission":false"""))
        assertTrue(result.contains(""""hasNotificationPermission":true"""))
    }

    @Test
    fun descriptor_exposesKoogToolMetadata() {
        val tool = GetUserContextSettingsTool(preferences(), permissions())

        assertEquals("get_user_context_settings", tool.name)
        assertTrue(tool.descriptor.description.contains("context", ignoreCase = true))
    }

    private fun preferences(): AppPreferences =
        mockk {
            every { deviceStatusContextEnabled } returns flowOf(true)
            every { locationContextEnabled } returns flowOf(true)
            every { weatherContextEnabled } returns flowOf(true)
            every { reminderToolEnabled } returns flowOf(true)
            every { notificationEnabled } returns flowOf(true)
        }

    private fun permissions(
        coarse: Boolean = false,
        fine: Boolean = false,
        notifications: Boolean = false,
    ): ContextPermissionReader =
        object : ContextPermissionReader {
            override fun hasCoarseLocation() = coarse
            override fun hasFineLocation() = fine
            override fun hasPostNotifications() = notifications
        }
}
