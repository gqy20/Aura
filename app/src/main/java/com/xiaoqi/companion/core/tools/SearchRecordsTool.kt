package com.xiaoqi.companion.core.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import com.xiaoqi.companion.data.db.converter.MessageRole
import com.xiaoqi.companion.data.db.dao.MessageDao
import com.xiaoqi.companion.data.db.entity.MessageEntity
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SearchRecordsTool @Inject constructor(
    private val messageDao: MessageDao,
) : SimpleTool<SearchRecordsTool.Args>(
    typeToken<Args>(),
    name = "search_records",
    description = "Search raw chat records and return original message snippets with nearby context.",
) {

    @Serializable
    data class Args(
        @param:LLMDescription("Keyword or phrase to search in raw message content. Empty means recent records.")
        val query: String = "",
        @param:LLMDescription("Session id to search. Use default unless the user explicitly asks for another session.")
        val sessionId: String = DEFAULT_SESSION_ID,
        @param:LLMDescription("Filter by role: USER, ASSISTANT, or SYSTEM. Empty means all roles.")
        val role: String = "",
        @param:LLMDescription("Only include messages at or after this epoch millis timestamp.")
        val after: Long? = null,
        @param:LLMDescription("Only include messages at or before this epoch millis timestamp.")
        val before: Long? = null,
        @param:LLMDescription("When true only messages with images; when false only text-only messages; null means both.")
        val hasImage: Boolean? = null,
        @param:LLMDescription("Maximum matching messages to return. Default 8.")
        val limit: Int = 8,
        @param:LLMDescription("Number of raw messages before and after each hit to include. Default 1.")
        val contextMessages: Int = 1,
    )

    override suspend fun execute(args: Args): String =
        withContext(Dispatchers.IO) {
            val query = args.query.trim()
            val role = args.role.uppercase()
                .takeIf { it.isNotBlank() }
                ?.let { role ->
                    MessageRole.entries.firstOrNull { it.name == role }
                        ?: return@withContext invalidRole(args.role)
                }
            val limit = args.limit.coerceIn(1, MAX_RESULTS)
            val contextLimit = args.contextMessages.coerceIn(0, MAX_CONTEXT_MESSAGES)
            val matches = messageDao.searchRecords(
                sessionId = args.sessionId,
                pattern = query.toRecordLikePattern(),
                role = role,
                after = args.after,
                before = args.before,
                hasImage = args.hasImage,
                limit = (limit * CANDIDATE_MULTIPLIER).coerceAtMost(MAX_CANDIDATES),
            )
                .map { message -> RecordSearchHit(message, scoreRecord(message.content, query)) }
                .sortedWith(compareByDescending<RecordSearchHit> { it.score }.thenByDescending { it.message.timestamp })
                .take(limit)

            val items = matches.map { hit ->
                val beforeContext = if (contextLimit == 0) {
                    emptyList()
                } else {
                    messageDao.getMessagesBefore(hit.message.sessionId, hit.message.timestamp, contextLimit)
                        .asReversed()
                        .map { it.toRecordContextItem() }
                }
                val afterContext = if (contextLimit == 0) {
                    emptyList()
                } else {
                    messageDao.getMessagesAfter(hit.message.sessionId, hit.message.timestamp, contextLimit)
                        .map { it.toRecordContextItem() }
                }
                hit.message.toRecordSearchItem(score = hit.score, beforeContext = beforeContext, afterContext = afterContext)
            }

            json.encodeToString(
                SearchRecordsResult(
                    count = items.size,
                    query = query,
                    sessionId = args.sessionId,
                    results = items,
                )
            )
        }

    @Serializable
    private data class SearchRecordsResult(
        val count: Int,
        val query: String,
        val sessionId: String,
        val results: List<RecordSearchItem>,
    )

    @Serializable
    private data class RecordSearchItem(
        val id: String,
        val role: String,
        val content: String,
        val timestamp: Long,
        val hasImage: Boolean,
        val score: Float,
        val contextBefore: List<RecordContextItem>,
        val contextAfter: List<RecordContextItem>,
    )

    @Serializable
    private data class RecordContextItem(
        val id: String,
        val role: String,
        val content: String,
        val timestamp: Long,
        val hasImage: Boolean,
    )

    private data class RecordSearchHit(
        val message: MessageEntity,
        val score: Float,
    )

    private fun MessageEntity.toRecordSearchItem(
        score: Float,
        beforeContext: List<RecordContextItem>,
        afterContext: List<RecordContextItem>,
    ): RecordSearchItem =
        RecordSearchItem(
            id = id,
            role = role.name,
            content = content.truncateRecordContent(),
            timestamp = timestamp,
            hasImage = imageBase64 != null,
            score = score,
            contextBefore = beforeContext,
            contextAfter = afterContext,
        )

    private fun MessageEntity.toRecordContextItem(): RecordContextItem =
        RecordContextItem(
            id = id,
            role = role.name,
            content = content.truncateRecordContent(),
            timestamp = timestamp,
            hasImage = imageBase64 != null,
        )

    private companion object {
        const val DEFAULT_SESSION_ID = "default"
        const val MAX_RESULTS = 30
        const val MAX_CANDIDATES = 120
        const val CANDIDATE_MULTIPLIER = 3
        const val MAX_CONTEXT_MESSAGES = 3
        val json = Json { encodeDefaults = true }
    }

    private fun invalidRole(role: String): String =
        buildJsonObject {
            put("status", "error")
            put("reason", "invalid_message_role")
            put("role", role)
            put("allowedRoles", MessageRole.entries.joinToString(",") { it.name })
        }.toString()
}

private fun String.toRecordLikePattern(): String =
    if (isBlank()) "%" else "%${replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")}%"

private fun scoreRecord(content: String, query: String): Float {
    if (query.isBlank()) return 1f
    val normalizedContent = content.lowercase()
    val normalizedQuery = query.lowercase()
    val terms = normalizedQuery.split(Regex("\\s+")).filter { it.isNotBlank() }
    var score = 0f
    if (normalizedContent.contains(normalizedQuery)) score += 20f
    terms.forEach { term ->
        if (normalizedContent.contains(term)) score += 4f
    }
    if (normalizedContent.startsWith(normalizedQuery)) score += 3f
    return score
}

private fun String.truncateRecordContent(): String =
    if (length <= MAX_RECORD_CONTENT_CHARS) this else take(MAX_RECORD_CONTENT_CHARS).trimEnd() + "..."

private const val MAX_RECORD_CONTENT_CHARS = 600
