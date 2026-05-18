package com.xiaoqi.companion.core.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: "Aura reminder"
        val message = intent.getStringExtra(EXTRA_MESSAGE)?.takeIf { it.isNotBlank() } ?: "You asked me to remind you."
        ReminderNotificationPoster.post(context, title, message)
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_MESSAGE = "message"
    }
}
