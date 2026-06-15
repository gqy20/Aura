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
                return@withContext disabled(
                    reason = "reminder_tool_disabled",
                    hint = "用户在 Settings > 工具能力 中关闭了提醒功能。请建议用户在设置中重新开启后再试。",
                )
            }
            if (!appPreferences.notificationEnabled.first()) {
                return@withContext disabled(
                    reason = "notifications_disabled_in_settings",
                    hint = "用户关闭了系统通知,即使提醒创建成功也不会响铃或震动。请建议用户在系统设置里重新允许 Aura 通知。",
                )
            }
            if (!permissionReader.hasPostNotifications()) {
                return@withContext ToolEnvelopeFactory.permissionMissing(
                    permission = "POST_NOTIFICATIONS",
                    hint = "应用尚未被授予 POST_NOTIFICATIONS 权限。无法安排系统通知,请引导用户到系统设置中允许通知。",
                ).asString()
            }

            val now = nowProvider()
            val triggerAt = args.triggerAtEpochMillis
                ?: args.delayMinutes?.let {
                    if (it < MIN_DELAY_MINUTES) return@withContext disabled(
                        reason = "delay_too_small_minute",
                        hint = "delayMinutes 必须 >= $MIN_DELAY_MINUTES。请改用更大的 delayMinutes 或指定 triggerAtEpochMillis。",
                    )
                    now + it * MILLIS_PER_MINUTE
                }
                ?: return@withContext disabled(
                    reason = "missing_trigger_time",
                    hint = "必须提供 delayMinutes 或 triggerAtEpochMillis 之一。两者都没传。",
                )
            if (triggerAt <= now) return@withContext disabled(
                reason = "trigger_time_must_be_future",
                hint = "triggerAt 必须晚于当前时间。triggerAtEpochMillis=$triggerAt 早于 now=$now。",
            )
            if (args.exact && !reminderRepository.canScheduleExactReminders()) {
                return@withContext ToolEnvelopeFactory.permissionMissing(
                    permission = "SCHEDULE_EXACT_ALARM",
                    hint = "用户没授予精准闹钟权限(SCHEDULE_EXACT_ALARM)。要么引导用户到系统设置里授权,要么把 exact 改为 false 改用非精准闹钟。",
                ).asString()
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

    private fun disabled(reason: String, hint: String): String {
        AppLogger.warn(LogTags.Tools, "reminder_tool_disabled", "reason" to reason)
        return encode(ToolEnvelopeFactory.disabled(reason, hint))
    }

    private fun ToolEnvelope.Error.asString(): String = encode(this)

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
        const val MIN_DELAY_MINUTES = 1L
    }
}
