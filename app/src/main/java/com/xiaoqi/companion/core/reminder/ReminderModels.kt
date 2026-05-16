package com.xiaoqi.companion.core.reminder

data class ReminderRequest(
    val title: String,
    val message: String,
    val triggerAtMillis: Long,
)

data class ScheduledReminder(
    val id: String,
    val title: String,
    val triggerAtMillis: Long,
    val delayMillis: Long,
)

interface ReminderScheduler {
    fun schedule(request: ReminderRequest): ScheduledReminder
}
