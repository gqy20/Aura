package com.xiaoqi.companion.core.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import com.xiaoqi.companion.data.db.converter.MemoryType
import com.xiaoqi.companion.data.db.dao.MemoryDao
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

class SearchMemoryTool(
    private val memoryDao: MemoryDao,
    private val recorder: ToolCallRecorder,
    private val sessionIdProvider: () -> String = { "default" },
) : SimpleTool<SearchMemoryTool.Args>(
    typeToken<Args>(),
    name = "search_memory",
    description = "Search stored memories by keyword, optionally filtered by type and limited in count.",
) {

    @Inject
    constructor(
        memoryDao: MemoryDao,
        recorder: ToolCallRecorder,
    ) : this(
        memoryDao = memoryDao,
        recorder = recorder,
        sessionIdProvider = { "default" },
    )

    @Serializable
    data class Args(
        @param:LLMDescription("Search query keyword to find relevant memories.")
        val query: String,
        @param:LLMDescription("Filter by memory type: FACT, EPISODE, or PROCEDURAL. Empty means all types.")
        val type: String = "",
        @param:LLMDescription("Maximum number of results to return. Default 10.")
        val limit: Int = 10,
    )

    override suspend fun execute(args: Args): String {
        val argumentsJson = json.encodeToString(args)
        return recorder.record(
            sessionId = sessionIdProvider(),
            toolName = name,
            argumentsJson = argumentsJson,
        ) {
            val allMemories = memoryDao.observeAll().first()
            val filterType = args.type.uppercase().takeIf { it.isNotBlank() }?.let { runCatching { MemoryType.valueOf(it) }.getOrNull() }
            val results = allMemories
                .run { if (filterType != null) filter { it.type == filterType } else this }
                .filter { it.content.contains(args.query, ignoreCase = true) }
                .take(args.limit)
            buildString {
                append("""{"count":${results.size},"results":[""")
                results.forEachIndexed { i, m ->
                    if (i > 0) append(",")
                    append("""{"id":"${m.id}","type":"${m.type.name}","content":"${m.content}","importance":${m.importance}}""")
                }
                append("]}")
            }
        }
    }

    private companion object {
        val json = Json { encodeDefaults = true }
    }
}
