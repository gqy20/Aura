package com.xiaoqi.companion.core.tools

import com.xiaoqi.companion.core.context.ContextPermissionReader
import com.xiaoqi.companion.core.reminder.ScheduledReminder
import com.xiaoqi.companion.data.datastore.AppPreferences
import com.xiaoqi.companion.data.db.entity.ReminderEntity
import com.xiaoqi.companion.data.repository.ReminderRepository
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
        val reminders = FakeReminderRepository(now = 1_000L)
        val tool = CreateLocalReminderTool(
            appPreferences = preferences(reminders = true, notifications = true),
            permissionReader = permissions(notifications = true),
            reminderRepository = reminders,
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
        assertEquals(601_000L, reminders.lastTriggerAtMillis)
        assertEquals(false, reminders.lastExact)
    }

    @Test
    fun execute_schedulesExactReminderWhenPermissionAvailable() = runTest {
        val reminders = FakeReminderRepository(now = 1_000L, exactAvailable = true)
        val tool = CreateLocalReminderTool(
            appPreferences = preferences(reminders = true, notifications = true),
            permissionReader = permissions(notifications = true),
            reminderRepository = reminders,
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
        assertEquals(true, reminders.lastExact)
    }

    @Test
    fun execute_returnsDisabledWhenExactAlarmPermissionMissing() = runTest {
        val reminders = FakeReminderRepository(now = 1_000L, exactAvailable = false)
        val tool = CreateLocalReminderTool(
            appPreferences = preferences(reminders = true, notifications = true),
            permissionReader = permissions(notifications = true),
            reminderRepository = reminders,
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

        assertTrue(result.contains(""""status":"error""""))
        assertTrue(result.contains(""""reason":"permission_missing""""))
        assertTrue(result.contains("SCHEDULE_EXACT_ALARM"))
        assertEquals(null, reminders.lastTriggerAtMillis)
    }

    @Test
    fun execute_returnsDisabledWhenNotificationPermissionMissing() = runTest {
        val tool = CreateLocalReminderTool(
            appPreferences = preferences(reminders = true, notifications = true),
            permissionReader = permissions(notifications = false),
            reminderRepository = FakeReminderRepository(now = 1_000L),
            nowProvider = { 1_000L },
        )

        val result = tool.execute(CreateLocalReminderTool.Args(title = "Review", message = "Time", delayMinutes = 10))

        assertTrue(result.contains(""""status":"error""""))
        assertTrue(result.contains(""""reason":"permission_missing""""))
        assertTrue(result.contains("POST_NOTIFICATIONS"))
    }

    @Test
    fun descriptor_exposesKoogToolMetadata() {
        val tool = CreateLocalReminderTool(
            appPreferences = preferences(reminders = true, notifications = true),
            permissionReader = permissions(notifications = true),
            reminderRepository = FakeReminderRepository(now = 1_000L),
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

    private class FakeReminderRepository(
        private val now: Long,
        private val exactAvailable: Boolean = true,
    ) : ReminderRepository {
        var lastTriggerAtMillis: Long? = null
        var lastExact: Boolean? = null

        override fun observeReminders() = flowOf(emptyList<ReminderEntity>())

        override fun canScheduleExactReminders(): Boolean = exactAvailable

        override suspend fun createReminder(
            title: String,
            message: String,
            triggerAtMillis: Long,
            exact: Boolean,
            source: String,
        ): ScheduledReminder {
            lastTriggerAtMillis = triggerAtMillis
            lastExact = exact
            return ScheduledReminder(
                id = "reminder-1",
                title = title,
                triggerAtMillis = triggerAtMillis,
                delayMillis = triggerAtMillis - now,
                exact = exact,
            )
        }

        override suspend fun cancelReminder(reminderId: String) = Unit
    }
}
