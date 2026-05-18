package com.xiaoqi.companion.core.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ReminderNotificationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE)?.takeIf { it.isNotBlank() } ?: "Aura reminder"
        val message = inputData.getString(KEY_MESSAGE)?.takeIf { it.isNotBlank() } ?: "You asked me to remind you."
        ReminderNotificationPoster.post(applicationContext, title, message)
        return Result.success()
    }

    companion object {
        const val KEY_TITLE = "title"
        const val KEY_MESSAGE = "message"
        const val KEY_TRIGGER_AT = "trigger_at"
    }
}
