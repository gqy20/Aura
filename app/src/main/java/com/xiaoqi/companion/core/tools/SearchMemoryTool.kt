package com.xiaoqi.companion.core.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import com.xiaoqi.companion.data.db.converter.MemoryType
import com.xiaoqi.companion.data.repository.MemoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

class SearchMemoryTool @Inject constructor(
    private val memoryRepository: MemoryRepository,
) : SimpleTool<SearchMemoryTool.Args>(
    typeToken<Args>(),
    name = "search_memory",
    description = """
        Recall the user's long-term FACTS, PREFERENCES, and PROFILE attributes (FACT/PROCEDURAL memory type).
        Use this when the user asks about themselves, their habits, traits, or 'what do you know about me'.
        Returns concise single-line entries (no surrounding chat context).
        Do NOT use for 'what did I say about X' or 'find a specific past message' — call search_records instead.
        Do NOT use for high-level time-windowed digests — call search_summaries instead.
        Call only ONE of search_memory / search_records / search_summaries per question; pick the most specific.
    """.trimIndent(),
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
            val filterType = args.type.uppercase()
                .takeIf { it.isNotBlank() }
                ?.let { type ->
                    MemoryType.entries.firstOrNull { it.name == type }
                        ?: return@withContext invalidType(args.type)
            }
            val limit = args.limit.coerceIn(1, MAX_RESULTS)
            val results = memoryRepository.searchMemories(
                query = query,
                type = filterType,
                limit = limit,
            ).memories
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
                            confidence = it.confidence,
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
        val confidence: Float,
    )

    private companion object {
        const val MAX_RESULTS = 50
        val json = Json { encodeDefaults = true }
    }

    private fun invalidType(type: String): String =
        encode(ToolEnvelopeFactory.invalidMemoryType(type))
}
