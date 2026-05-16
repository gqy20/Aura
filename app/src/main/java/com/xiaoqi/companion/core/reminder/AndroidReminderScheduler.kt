package com.xiaoqi.companion.core.reminder

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
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

    override fun schedule(request: ReminderRequest): ScheduledReminder {
        val now = nowProvider()
        val delayMillis = (request.triggerAtMillis - now).coerceAtLeast(MIN_DELAY_MILLIS)
        val reminderId = UUID.randomUUID().toString()
        val work = OneTimeWorkRequestBuilder<ReminderNotificationWorker>()
            .setId(UUID.fromString(reminderId))
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putString(ReminderNotificationWorker.KEY_TITLE, request.title)
                    .putString(ReminderNotificationWorker.KEY_MESSAGE, request.message)
                    .putLong(ReminderNotificationWorker.KEY_TRIGGER_AT, request.triggerAtMillis)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueue(work)
        return ScheduledReminder(
            id = reminderId,
            title = request.title,
            triggerAtMillis = request.triggerAtMillis,
            delayMillis = delayMillis,
        )
    }

    private companion object {
        const val MIN_DELAY_MILLIS = 1_000L
    }
}
