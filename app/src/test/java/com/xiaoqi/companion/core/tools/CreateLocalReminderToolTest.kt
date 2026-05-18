package com.xiaoqi.companion.core.tools

import com.xiaoqi.companion.core.context.ContextPermissionReader
import com.xiaoqi.companion.core.reminder.ReminderRequest
import com.xiaoqi.companion.core.reminder.ReminderScheduler
import com.xiaoqi.companion.core.reminder.ScheduledReminder
import com.xiaoqi.companion.data.datastore.AppPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateLocalReminderToolTest {

    @Test
    fun execute_schedulesReminderFromDelay() = runTest {
        val scheduler = FakeReminderScheduler(now = 1_000L)
        val tool = CreateLocalReminderTool(
            appPreferences = preferences(reminders = true, notifications = true),
            permissionReader = permissions(notifications = true),
            reminderScheduler = scheduler,
            nowProvider = { 1_000L },
        )

        val result = tool.execute(
            CreateLocalReminderTool.Args(
                title = "Review",
                message = "Time to review",
                delayMinutes = 10,
            )
        )

        assertTrue(result.contains(""""status":"scheduled""""))
        assertEquals(601_000L, scheduler.lastRequest!!.triggerAtMillis)
        assertEquals(false, scheduler.lastRequest!!.exact)
    }

    @Test
    fun execute_schedulesExactReminderWhenPermissionAvailable() = runTest {
        val scheduler = FakeReminderScheduler(now = 1_000L, exactAvailable = true)
        val tool = CreateLocalReminderTool(
            appPreferences = preferences(reminders = true, notifications = true),
            permissionReader = permissions(notifications = true),
            reminderScheduler = scheduler,
            nowProvider = { 1_000L },
        )

        val result = tool.execute(
            CreateLocalReminderTool.Args(
                title = "Stand up",
                message = "Move a bit",
                delayMinutes = 5,
                exact = true,
            )
        )

        assertTrue(result.contains(""""status":"scheduled""""))
        assertTrue(result.contains(""""exact":true"""))
        assertEquals(true, scheduler.lastRequest!!.exact)
    }

    @Test
    fun execute_returnsDisabledWhenExactAlarmPermissionMissing() = runTest {
        val scheduler = FakeReminderScheduler(now = 1_000L, exactAvailable = false)
        val tool = CreateLocalReminderTool(
            appPreferences = preferences(reminders = true, notifications = true),
            permissionReader = permissions(notifications = true),
            reminderScheduler = scheduler,
            nowProvider = { 1_000L },
        )

        val result = tool.execute(
            CreateLocalReminderTool.Args(
                title = "Stand up",
                message = "Move a bit",
                delayMinutes = 5,
                exact = true,
            )
        )

        assertTrue(result.contains(""""status":"disabled""""))
        assertTrue(result.contains("exact_alarm_permission_missing"))
        assertEquals(null, scheduler.lastRequest)
    }

    @Test
    fun execute_returnsDisabledWhenNotificationPermissionMissing() = runTest {
        val tool = CreateLocalReminderTool(
            appPreferences = preferences(reminders = true, notifications = true),
            permissionReader = permissions(notifications = false),
            reminderScheduler = FakeReminderScheduler(now = 1_000L),
            nowProvider = { 1_000L },
        )

        val result = tool.execute(CreateLocalReminderTool.Args(title = "Review", message = "Time", delayMinutes = 10))

        assertTrue(result.contains(""""status":"disabled""""))
        assertTrue(result.contains("notification_permission_missing"))
    }

    @Test
    fun descriptor_exposesKoogToolMetadata() {
        val tool = CreateLocalReminderTool(
            appPreferences = preferences(reminders = true, notifications = true),
            permissionReader = permissions(notifications = true),
            reminderScheduler = FakeReminderScheduler(now = 1_000L),
            nowProvider = { 1_000L },
        )

        assertEquals("create_local_reminder", tool.name)
        assertTrue(tool.descriptor.description.contains("reminder", ignoreCase = true))
    }

    private fun preferences(reminders: Boolean, notifications: Boolean): AppPreferences =
        mockk {
            every { reminderToolEnabled } returns flowOf(reminders)
            every { notificationEnabled } returns flowOf(notifications)
        }

    private fun permissions(notifications: Boolean): ContextPermissionReader =
        object : ContextPermissionReader {
            override fun hasCoarseLocation() = false
            override fun hasFineLocation() = false
            override fun hasPostNotifications() = notifications
        }

    private class FakeReminderScheduler(
        private val now: Long,
        private val exactAvailable: Boolean = true,
    ) : ReminderScheduler {
        var lastRequest: ReminderRequest? = null

        override fun canScheduleExactReminders(): Boolean = exactAvailable

        override fun schedule(request: ReminderRequest): ScheduledReminder {
            lastRequest = request
            return ScheduledReminder(
                id = "reminder-1",
                title = request.title,
                triggerAtMillis = request.triggerAtMillis,
                delayMillis = request.triggerAtMillis - now,
                exact = request.exact,
            )
        }
    }
}
