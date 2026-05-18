package com.xiaoqi.companion.core.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.data.db.dao.ReminderDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ReminderNotificationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val reminderDao: ReminderDao,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val reminderId = inputData.getString(KEY_REMINDER_ID)
        val title = inputData.getString(KEY_TITLE)?.takeIf { it.isNotBlank() } ?: "Aura reminder"
        val message = inputData.getString(KEY_MESSAGE)?.takeIf { it.isNotBlank() } ?: "You asked me to remind you."
        AppLogger.info(LogTags.Reminder, "reminder_worker_started", "reminderId" to reminderId)
        ReminderNotificationPoster.post(applicationContext, title, message)
        reminderId?.let { reminderDao.markFired(it, System.currentTimeMillis()) }
        AppLogger.info(LogTags.Reminder, "reminder_worker_finished", "reminderId" to reminderId)
        return Result.success()
    }

    companion object {
        const val KEY_REMINDER_ID = "reminder_id"
        const val KEY_TITLE = "title"
        const val KEY_MESSAGE = "message"
        const val KEY_TRIGGER_AT = "trigger_at"
    }
}
