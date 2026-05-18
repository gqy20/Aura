package com.xiaoqi.companion.core.reminder

data class ReminderRequest(
    val id: String,
    val title: String,
    val message: String,
    val triggerAtMillis: Long,
    val exact: Boolean = false,
)

data class ScheduledReminder(
    val id: String,
    val title: String,
    val triggerAtMillis: Long,
    val delayMillis: Long,
    val exact: Boolean = false,
)

interface ReminderScheduler {
    fun canScheduleExactReminders(): Boolean = true
    fun schedule(request: ReminderRequest): ScheduledReminder
    fun cancel(reminderId: String)
}
