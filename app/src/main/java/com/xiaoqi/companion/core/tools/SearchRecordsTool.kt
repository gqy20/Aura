package com.xiaoqi.companion.core.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.data.db.converter.MessageRole
import com.xiaoqi.companion.data.db.dao.MessageDao
import com.xiaoqi.companion.data.db.dao.MessageSearchDao
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
    private val messageSearchDao: MessageSearchDao,
) : SimpleTool<SearchRecordsTool.Args>(
    typeToken<Args>(),
    name = "search_records",
    description = """
        Find RAW CHAT MESSAGES with before/after context from past conversations.
        Use this when the user asks 'what did I/we say about X', 'when did I mention Y', or wants to recall a specific conversation or message.
        Each hit includes 1 message before and 1 after by default for context.
        Do NOT use for distilled knowledge about the user (preferences, facts) — call search_memory instead.
        Do NOT use for high-level time-windowed digests — call search_summaries instead.
        Call only ONE of search_memory / search_records / search_summaries per question; pick the most specific.
    """.trimIndent(),
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
            val candidateLimit = (limit * CANDIDATE_MULTIPLIER).coerceAtMost(MAX_CANDIDATES)
            val matches = if (query.isBlank()) {
                messageDao.getRecentMessages(
                    sessionId = args.sessionId,
                    limit = candidateLimit,
                )
                    .map { message -> RecordSearchHit(message, score = 1f, source = "recent") }
                    .sortedByDescending { it.message.timestamp }
                    .take(limit)
            } else {
                val ftsMatches = runCatching {
                    messageSearchDao.searchRecordsFts(
                        sessionId = args.sessionId,
                        matchQuery = query.toFtsQuery(),
                        role = role,
                        after = args.after,
                        before = args.before,
                        hasImage = args.hasImage,
                        limit = candidateLimit,
                    )
                }.getOrElse { error ->
                    AppLogger.warn(
                        LogTags.Tools,
                        "search_records_fts_failed",
                        "queryLength" to query.length,
                        "sessionId" to args.sessionId,
                        "error" to (error.message ?: error::class.simpleName.orEmpty()),
                    )
                    return@withContext encode(
                        ToolEnvelopeFactory.ftsFailure(error.message ?: error::class.simpleName.orEmpty())
                    )
                }

                ftsMatches.map { hit ->
                    RecordSearchHit(
                        message = MessageEntity(
                            id = hit.id,
                            sessionId = hit.sessionId,
                            role = hit.role,
                            content = hit.content,
                            imageBase64 = hit.imageBase64,
                            timestamp = hit.timestamp,
                        ),
                        score = normalizeFtsRank(hit.ftsRank),
                        source = "fts5",
                    )
                }
                    .sortedWith(compareByDescending<RecordSearchHit> { it.score }.thenByDescending { it.message.timestamp })
                    .take(limit)
            }

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
                hit.message.toRecordSearchItem(
                    score = hit.score,
                    beforeContext = beforeContext,
                    afterContext = afterContext,
                    source = hit.source,
                )
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
        val source: String,
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
        val source: String,
    )

    private fun MessageEntity.toRecordSearchItem(
        score: Float,
        beforeContext: List<RecordContextItem>,
        afterContext: List<RecordContextItem>,
        source: String,
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
            source = source,
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
        encode(ToolEnvelopeFactory.invalidMessageRole(role))
}

private fun String.toFtsQuery(): String =
    trim()
        .split(Regex("\\s+"))
        .map { it.filterFtsToken() }
        .filter { it.isNotBlank() }
        .joinToString(" OR ")
        .ifBlank { "\"\"" }

private fun String.filterFtsToken(): String =
    filter { it.isLetterOrDigit() || it == '_' || it.code > 127 }

private fun normalizeFtsRank(rank: Double): Float =
    (-rank).coerceIn(0.0001, 100.0).toFloat()

private fun String.truncateRecordContent(): String =
    if (length <= MAX_RECORD_CONTENT_CHARS) this else take(MAX_RECORD_CONTENT_CHARS).trimEnd() + "..."

private const val MAX_RECORD_CONTENT_CHARS = 600
