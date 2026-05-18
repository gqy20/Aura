package com.xiaoqi.companion.core.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xiaoqi.companion.data.db.dao.ReminderDao
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ReminderAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var reminderDao: ReminderDao

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID)
        val title = intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: "Aura reminder"
        val message = intent.getStringExtra(EXTRA_MESSAGE)?.takeIf { it.isNotBlank() } ?: "You asked me to remind you."
        ReminderNotificationPoster.post(context, title, message)
        reminderId?.let { id ->
            val pending = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                runCatching { reminderDao.markFired(id, System.currentTimeMillis()) }
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_MESSAGE = "message"
    }
}
