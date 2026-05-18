package com.xiaoqi.companion.core.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import com.xiaoqi.companion.core.context.ContextPermissionReader
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.data.datastore.AppPreferences
import com.xiaoqi.companion.data.repository.ReminderRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class CreateLocalReminderTool(
    private val appPreferences: AppPreferences,
    private val permissionReader: ContextPermissionReader,
    private val reminderRepository: ReminderRepository,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) : SimpleTool<CreateLocalReminderTool.Args>(
    typeToken<Args>(),
    name = "create_local_reminder",
    description = "Schedule a local reminder notification on this Android device.",
) {

    @Inject
    constructor(
        appPreferences: AppPreferences,
        permissionReader: ContextPermissionReader,
        reminderRepository: ReminderRepository,
    ) : this(
        appPreferences = appPreferences,
        permissionReader = permissionReader,
        reminderRepository = reminderRepository,
        nowProvider = { System.currentTimeMillis() },
    )

    @Serializable
    data class Args(
        @param:LLMDescription("Reminder title shown in the notification.")
        val title: String,
        @param:LLMDescription("Reminder body shown in the notification.")
        val message: String,
        @param:LLMDescription("Delay in minutes from now. Use this for relative reminders.")
        val delayMinutes: Long? = null,
        @param:LLMDescription("Absolute trigger time in epoch milliseconds. Use this for exact reminders.")
        val triggerAtEpochMillis: Long? = null,
        @param:LLMDescription("Set true only when the user asks for a precise alarm-style reminder.")
        val exact: Boolean = false,
    )

    override suspend fun execute(args: Args): String =
        withContext(Dispatchers.Default) {
            AppLogger.info(
                LogTags.Tools,
                "reminder_tool_requested",
                "delayMinutes" to args.delayMinutes,
                "triggerAtEpochMillis" to args.triggerAtEpochMillis,
                "exact" to args.exact,
            )
            if (!appPreferences.reminderToolEnabled.first()) {
                return@withContext disabled("reminder_tool_disabled")
            }
            if (!appPreferences.notificationEnabled.first()) {
                return@withContext disabled("notifications_disabled_in_settings")
            }
            if (!permissionReader.hasPostNotifications()) {
                return@withContext disabled("notification_permission_missing")
            }

            val now = nowProvider()
            val triggerAt = args.triggerAtEpochMillis
                ?: args.delayMinutes?.let { now + it.coerceAtLeast(1L) * MILLIS_PER_MINUTE }
                ?: return@withContext disabled("missing_trigger_time")
            if (triggerAt <= now) return@withContext disabled("trigger_time_must_be_future")
            if (args.exact && !reminderRepository.canScheduleExactReminders()) {
                return@withContext disabled("exact_alarm_permission_missing")
            }

            val scheduled = reminderRepository.createReminder(
                title = args.title,
                message = args.message,
                triggerAtMillis = triggerAt,
                exact = args.exact,
            )
            AppLogger.info(
                LogTags.Tools,
                "reminder_tool_scheduled",
                "reminderId" to scheduled.id,
                "triggerAtMillis" to scheduled.triggerAtMillis,
                "delayMillis" to scheduled.delayMillis,
                "exact" to scheduled.exact,
            )
            buildJsonObject {
                put("status", "scheduled")
                put("reminderId", scheduled.id)
                put("title", scheduled.title)
                put("triggerAtEpochMillis", scheduled.triggerAtMillis)
                put("delayMillis", scheduled.delayMillis)
                put("exact", scheduled.exact)
            }.toString()
        }

    private fun disabled(reason: String): String {
        AppLogger.warn(LogTags.Tools, "reminder_tool_disabled", "reason" to reason)
        return buildJsonObject {
            put("status", "disabled")
            put("reason", reason)
        }.toString()
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
