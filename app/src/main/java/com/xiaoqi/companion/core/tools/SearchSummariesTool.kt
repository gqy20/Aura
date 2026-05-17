package com.xiaoqi.companion.core.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import com.xiaoqi.companion.data.db.converter.SummaryType
import com.xiaoqi.companion.data.db.dao.MemorySummaryDao
import com.xiaoqi.companion.data.db.entity.MemorySummaryEntity
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SearchSummariesTool @Inject constructor(
    private val summaryDao: MemorySummaryDao,
) : SimpleTool<SearchSummariesTool.Args>(
    typeToken<Args>(),
    name = "search_summaries",
    description = "Search condensed summaries separately from raw records and long-term memories.",
) {

    @Serializable
    data class Args(
        @param:LLMDescription("Keyword or phrase to search in summary title, body, or keywords.")
        val query: String,
        @param:LLMDescription("Filter by summary type: DAILY, SESSION, TOPIC, PROJECT, or RELATIONSHIP. Empty means all.")
        val type: String = "",
        @param:LLMDescription("Maximum number of summaries to return. Default 8.")
        val limit: Int = 8,
    )

    override suspend fun execute(args: Args): String =
        withContext(Dispatchers.IO) {
            val query = args.query.trim()
            val filterType = args.type.uppercase()
                .takeIf { it.isNotBlank() }
                ?.let { type ->
                    SummaryType.entries.firstOrNull { it.name == type }
                        ?: return@withContext invalidType(args.type)
                }
            val limit = args.limit.coerceIn(1, MAX_RESULTS)
            val candidates = summaryDao.searchByText(
                pattern = query.toSummaryLikePattern(),
                type = filterType,
                limit = (limit * CANDIDATE_MULTIPLIER).coerceAtMost(MAX_CANDIDATES),
            )
            val results = candidates
                .map { summary -> SummarySearchHit(summary, scoreSummary(summary, query)) }
                .sortedWith(compareByDescending<SummarySearchHit> { it.score }.thenByDescending { it.summary.lastAccessed })
                .take(limit)
                .map { it.summary }
            val accessedAt = System.currentTimeMillis()
            results.forEach { summaryDao.updateLastAccessed(it.id, accessedAt) }

            json.encodeToString(
                SearchSummariesResult(
                    count = results.size,
                    query = query,
                    results = results.map { it.toSearchItem() },
                )
            )
        }

    @Serializable
    private data class SearchSummariesResult(
        val count: Int,
        val query: String,
        val results: List<SummarySearchItem>,
    )

    @Serializable
    private data class SummarySearchItem(
        val id: String,
        val type: String,
        val title: String,
        val summary: String,
        val keywords: List<String>,
        val sourceMessageIds: List<String>,
        val startAt: Long?,
        val endAt: Long?,
        val importance: Float,
    )

    private data class SummarySearchHit(
        val summary: MemorySummaryEntity,
        val score: Float,
    )

    private fun MemorySummaryEntity.toSearchItem(): SummarySearchItem =
        SummarySearchItem(
            id = id,
            type = type.name,
            title = title,
            summary = summary,
            keywords = keywords.decodeStringList(),
            sourceMessageIds = sourceMessageIds.decodeStringList(),
            startAt = startAt,
            endAt = endAt,
            importance = importance,
        )

    private companion object {
        const val MAX_RESULTS = 30
        const val MAX_CANDIDATES = 120
        const val CANDIDATE_MULTIPLIER = 3
        val json = Json { encodeDefaults = true }
    }
}

private fun String.toSummaryLikePattern(): String =
    if (isBlank()) "%" else "%${replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")}%"

private fun scoreSummary(summary: MemorySummaryEntity, query: String): Float {
    if (query.isBlank()) return summary.importance
    val haystack = "${summary.title}\n${summary.summary}\n${summary.keywords}".lowercase()
    val normalizedQuery = query.lowercase()
    val terms = normalizedQuery.split(Regex("\\s+")).filter { it.isNotBlank() }
    var score = summary.importance * 3f
    if (haystack.contains(normalizedQuery)) score += 20f
    terms.forEach { term ->
        if (haystack.contains(term)) score += 5f
    }
    if (summary.title.lowercase().contains(normalizedQuery)) score += 6f
    return score
}

private fun String.decodeStringList(): List<String> =
    Json.decodeFromString(this)

private fun invalidType(type: String): String =
    buildJsonObject {
        put("status", "error")
        put("reason", "invalid_summary_type")
        put("type", type)
        put("allowedTypes", SummaryType.entries.joinToString(",") { it.name })
    }.toString()
