package com.xiaoqi.companion.data.repository

import com.xiaoqi.companion.core.reminder.ReminderRequest
import com.xiaoqi.companion.core.reminder.ReminderScheduler
import com.xiaoqi.companion.core.reminder.ScheduledReminder
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.data.db.dao.ReminderDao
import com.xiaoqi.companion.data.db.entity.ReminderEntity
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

interface ReminderRepository {
    fun observeReminders(): Flow<List<ReminderEntity>>
    fun canScheduleExactReminders(): Boolean
    suspend fun createReminder(
        title: String,
        message: String,
        triggerAtMillis: Long,
        exact: Boolean,
        source: String = "tool:create_local_reminder",
    ): ScheduledReminder
    suspend fun cancelReminder(reminderId: String)
}

class ReminderRepositoryImpl @Inject constructor(
    private val reminderDao: ReminderDao,
    private val reminderScheduler: ReminderScheduler,
) : ReminderRepository {

    override fun observeReminders(): Flow<List<ReminderEntity>> =
        reminderDao.observeScheduled()

    override fun canScheduleExactReminders(): Boolean =
        reminderScheduler.canScheduleExactReminders()

    override suspend fun createReminder(
        title: String,
        message: String,
        triggerAtMillis: Long,
        exact: Boolean,
        source: String,
    ): ScheduledReminder = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        AppLogger.info(
            LogTags.Reminder,
            "reminder_create_requested",
            "reminderId" to id,
            "triggerAtMillis" to triggerAtMillis,
            "exact" to exact,
        )
        val scheduled = reminderScheduler.schedule(
            ReminderRequest(
                id = id,
                title = title,
                message = message,
                triggerAtMillis = triggerAtMillis,
                exact = exact,
            )
        )
        val now = System.currentTimeMillis()
        reminderDao.insert(
            ReminderEntity(
                id = scheduled.id,
                title = scheduled.title,
                message = message,
                triggerAtMillis = scheduled.triggerAtMillis,
                delayMillis = scheduled.delayMillis,
                exact = scheduled.exact,
                status = "SCHEDULED",
                source = source,
                createdAt = now,
                updatedAt = now,
            )
        )
        AppLogger.info(
            LogTags.Reminder,
            "reminder_created",
            "reminderId" to scheduled.id,
            "delayMillis" to scheduled.delayMillis,
            "exact" to scheduled.exact,
        )
        scheduled
    }

    override suspend fun cancelReminder(reminderId: String) = withContext(Dispatchers.IO) {
        reminderScheduler.cancel(reminderId)
        reminderDao.markCanceled(reminderId, System.currentTimeMillis())
        AppLogger.info(LogTags.Reminder, "reminder_canceled", "reminderId" to reminderId)
    }
}
