package com.xiaoqi.companion.core.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class AndroidReminderScheduler(
    @param:ApplicationContext private val context: Context,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) : ReminderScheduler {

    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(
        context = context,
        nowProvider = { System.currentTimeMillis() },
    )

    override fun canScheduleExactReminders(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        return alarmManager.canScheduleExactAlarms()
    }

    override fun schedule(request: ReminderRequest): ScheduledReminder {
        val now = nowProvider()
        val delayMillis = (request.triggerAtMillis - now).coerceAtLeast(MIN_DELAY_MILLIS)
        if (request.exact) {
            scheduleExactAlarm(request, delayMillis)
        } else {
            scheduleWork(request, delayMillis)
        }
        return ScheduledReminder(
            id = request.id,
            title = request.title,
            triggerAtMillis = request.triggerAtMillis,
            delayMillis = delayMillis,
            exact = request.exact,
        )
    }

    override fun cancel(reminderId: String) {
        WorkManager.getInstance(context).cancelWorkById(java.util.UUID.fromString(reminderId))
        cancelExactAlarm(reminderId)
    }

    private fun scheduleWork(request: ReminderRequest, delayMillis: Long) {
        val work = OneTimeWorkRequestBuilder<ReminderNotificationWorker>()
            .setId(java.util.UUID.fromString(request.id))
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putString(ReminderNotificationWorker.KEY_REMINDER_ID, request.id)
                    .putString(ReminderNotificationWorker.KEY_TITLE, request.title)
                    .putString(ReminderNotificationWorker.KEY_MESSAGE, request.message)
                    .putLong(ReminderNotificationWorker.KEY_TRIGGER_AT, request.triggerAtMillis)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueue(work)
    }

    private fun scheduleExactAlarm(request: ReminderRequest, delayMillis: Long) {
        check(canScheduleExactReminders()) { "Exact alarm permission is not available" }
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            request.id.hashCode(),
            Intent(context, ReminderAlarmReceiver::class.java).apply {
                putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, request.id)
                putExtra(ReminderAlarmReceiver.EXTRA_TITLE, request.title)
                putExtra(ReminderAlarmReceiver.EXTRA_MESSAGE, request.message)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val triggerAt = request.triggerAtMillis.coerceAtLeast(System.currentTimeMillis() + MIN_DELAY_MILLIS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun cancelExactAlarm(reminderId: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            Intent(context, ReminderAlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private companion object {
        const val MIN_DELAY_MILLIS = 1_000L
    }
}
