package com.xiaoqi.companion.core.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import com.xiaoqi.companion.data.db.converter.MemoryType
import com.xiaoqi.companion.data.db.dao.MemoryDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

class SearchMemoryTool @Inject constructor(
    private val memoryDao: MemoryDao,
) : SimpleTool<SearchMemoryTool.Args>(
    typeToken<Args>(),
    name = "search_memory",
    description = "Search stored memories by keyword, optionally filtered by type and limited in count.",
) {

    @Serializable
    data class Args(
        @param:LLMDescription("Search query keyword to find relevant memories.")
        val query: String,
        @param:LLMDescription("Filter by memory type: FACT, EPISODE, or PROCEDURAL. Empty means all types.")
        val type: String = "",
        @param:LLMDescription("Maximum number of results to return. Default 10.")
        val limit: Int = 10,
    )

    override suspend fun execute(args: Args): String =
        withContext(Dispatchers.IO) {
            val query = args.query.trim()
            val filterType = args.type.uppercase().takeIf { it.isNotBlank() }?.let { runCatching { MemoryType.valueOf(it) }.getOrNull() }
            val limit = args.limit.coerceIn(1, MAX_RESULTS)
            val candidateLimit = (limit * CANDIDATE_MULTIPLIER).coerceAtMost(MAX_CANDIDATES)
            val candidates = memoryDao.searchByContent(
                pattern = query.toLikePattern(),
                type = filterType,
                limit = candidateLimit,
            )
            val results = candidates
                .map { memory -> MemorySearchHit(memory, scoreMemory(memory.content, query, memory.importance)) }
                .sortedWith(compareByDescending<MemorySearchHit> { it.score }.thenByDescending { it.memory.lastAccessed })
                .take(limit)
                .map { it.memory }
            json.encodeToString(
                SearchMemoryResult(
                    count = results.size,
                    query = query,
                    results = results.map {
                        SearchMemoryItem(
                            id = it.id,
                            type = it.type.name,
                            content = it.content,
                            importance = it.importance,
                        )
                    },
                )
            )
        }

    @Serializable
    private data class SearchMemoryResult(
        val count: Int,
        val query: String,
        val results: List<SearchMemoryItem>,
    )

    @Serializable
    private data class SearchMemoryItem(
        val id: String,
        val type: String,
        val content: String,
        val importance: Float,
    )

    private data class MemorySearchHit(
        val memory: com.xiaoqi.companion.data.db.entity.MemoryEntity,
        val score: Float,
    )

    private companion object {
        const val MAX_RESULTS = 50
        const val MAX_CANDIDATES = 200
        const val CANDIDATE_MULTIPLIER = 4
        val json = Json { encodeDefaults = true }
    }
}

private fun String.toLikePattern(): String =
    "%${replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")}%"

private fun scoreMemory(content: String, query: String, importance: Float): Float {
    if (query.isBlank()) return importance
    val normalizedContent = content.lowercase()
    val normalizedQuery = query.lowercase()
    val terms = normalizedQuery.split(Regex("\\s+")).filter { it.isNotBlank() }
    var score = importance * 3f
    if (normalizedContent.contains(normalizedQuery)) score += 20f
    terms.forEach { term ->
        if (normalizedContent.contains(term)) score += 5f
    }
    if (normalizedContent.startsWith(normalizedQuery)) score += 4f
    return score
}
