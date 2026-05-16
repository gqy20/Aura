package com.xiaoqi.companion.core.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import com.xiaoqi.companion.data.db.converter.MessageRole
import com.xiaoqi.companion.data.db.dao.MessageDao
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
            val messages = messageDao.observeBySession(sessionId).first()
            val now = nowProvider()
            val startOfToday = Instant.ofEpochMilli(now)
                .atZone(zoneProvider())
                .toLocalDate()
                .atStartOfDay(zoneProvider())
                .toInstant()
                .toEpochMilli()
            val lastMessage = messages.maxByOrNull { it.timestamp }
            val lastUserMessage = messages
                .filter { it.role == MessageRole.USER }
                .maxByOrNull { it.timestamp }
            val todayMessages = messages.filter { it.timestamp >= startOfToday }

            buildJsonObject {
                put("sessionId", sessionId)
                put("messageCount", messages.size)
                put("userMessageCount", messages.count { it.role == MessageRole.USER })
                put("assistantMessageCount", messages.count { it.role == MessageRole.ASSISTANT })
                put("messagesToday", todayMessages.size)
                put("userMessagesToday", todayMessages.count { it.role == MessageRole.USER })
                put("assistantMessagesToday", todayMessages.count { it.role == MessageRole.ASSISTANT })
                put("hasPreviousInteraction", lastMessage != null)
                put("lastMessageRole", lastMessage?.role?.name ?: "")
                put("lastMessageAt", lastMessage?.timestamp ?: 0L)
                put("minutesSinceLastMessage", lastMessage?.let { minutesBetween(it.timestamp, now) } ?: -1L)
                put("lastUserMessageAt", lastUserMessage?.timestamp ?: 0L)
                put("minutesSinceLastUserMessage", lastUserMessage?.let { minutesBetween(it.timestamp, now) } ?: -1L)
            }.toString()
        }

    private fun minutesBetween(thenMillis: Long, nowMillis: Long): Long =
        ((nowMillis - thenMillis).coerceAtLeast(0L)) / MILLIS_PER_MINUTE

    private companion object {
        const val DEFAULT_SESSION_ID = "default"
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
