package com.xiaoqi.companion.core.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import com.xiaoqi.companion.data.db.dao.MessageDao
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class GetRecentInteractionContextTool(
    private val messageDao: MessageDao,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val zoneProvider: () -> ZoneId = { ZoneId.systemDefault() },
) : SimpleTool<GetRecentInteractionContextTool.Args>(
    typeToken<Args>(),
    name = "get_recent_interaction_context",
    description = "Get recent chat activity context, including last interaction time and today's message counts.",
) {

    @Inject
    constructor(
        messageDao: MessageDao,
    ) : this(
        messageDao = messageDao,
        nowProvider = { System.currentTimeMillis() },
        zoneProvider = { ZoneId.systemDefault() },
    )

    @Serializable
    data class Args(
        @param:LLMDescription("Chat session ID. Empty uses the default session.")
        val sessionId: String = DEFAULT_SESSION_ID,
    )

    override suspend fun execute(args: Args): String =
        withContext(Dispatchers.IO) {
            val sessionId = args.sessionId.ifBlank { DEFAULT_SESSION_ID }
            val now = nowProvider()
            val startOfToday = Instant.ofEpochMilli(now)
                .atZone(zoneProvider())
                .toLocalDate()
                .atStartOfDay(zoneProvider())
                .toInstant()
                .toEpochMilli()
            val summary = messageDao.getInteractionSummary(sessionId, startOfToday)

            buildJsonObject {
                put("sessionId", sessionId)
                put("messageCount", summary.messageCount)
                put("userMessageCount", summary.userMessageCount)
                put("assistantMessageCount", summary.assistantMessageCount)
                put("messagesToday", summary.messagesToday)
                put("userMessagesToday", summary.userMessagesToday)
                put("assistantMessagesToday", summary.assistantMessagesToday)
                put("hasPreviousInteraction", summary.messageCount > 0)
                put("lastMessageRole", summary.lastMessageRole ?: "")
                put("lastMessageAt", summary.lastMessageAt)
                put("minutesSinceLastMessage", summary.lastMessageAt.takeIf { it > 0L }?.let { minutesBetween(it, now) } ?: -1L)
                put("lastUserMessageAt", summary.lastUserMessageAt)
                put("minutesSinceLastUserMessage", summary.lastUserMessageAt.takeIf { it > 0L }?.let { minutesBetween(it, now) } ?: -1L)
            }.toString()
        }

    private fun minutesBetween(thenMillis: Long, nowMillis: Long): Long =
        ((nowMillis - thenMillis).coerceAtLeast(0L)) / MILLIS_PER_MINUTE

    private companion object {
        const val DEFAULT_SESSION_ID = "default"
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
