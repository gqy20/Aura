package com.xiaoqi.companion.core.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import com.xiaoqi.companion.data.db.converter.SummaryType
import com.xiaoqi.companion.data.db.dao.MemorySummaryDao
import com.xiaoqi.companion.data.db.entity.MemorySummaryEntity
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SaveSummaryTool @Inject constructor(
    private val summaryDao: MemorySummaryDao,
) : SimpleTool<SaveSummaryTool.Args>(
    typeToken<Args>(),
    name = "save_summary",
    description = "Save a condensed summary of a session, topic, project, day, or relationship arc.",
) {

    @Serializable
    data class Args(
        @param:LLMDescription("Summary type: DAILY, SESSION, TOPIC, PROJECT, or RELATIONSHIP.")
        val type: String = "TOPIC",
        @param:LLMDescription("Short title for the summary.")
        val title: String,
        @param:LLMDescription("The condensed summary. Preserve important nuance and unresolved next steps.")
        val summary: String,
        @param:LLMDescription("Keywords that should retrieve this summary later.")
        val keywords: List<String> = emptyList(),
        @param:LLMDescription("Raw message ids this summary is based on, when available.")
        val sourceMessageIds: List<String> = emptyList(),
        @param:LLMDescription("Start time covered by the summary as epoch millis, when known.")
        val startAt: Long? = null,
        @param:LLMDescription("End time covered by the summary as epoch millis, when known.")
        val endAt: Long? = null,
        @param:LLMDescription("Importance from 0.0 to 1.0.")
        val importance: Float = 0.5f,
    )

    override suspend fun execute(args: Args): String =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val summaryId = UUID.randomUUID().toString()
            val normalizedType = SummaryType.entries.firstOrNull { it.name == args.type.uppercase() }
                ?: return@withContext invalidType(args.type)
            summaryDao.insert(
                MemorySummaryEntity(
                    id = summaryId,
                    type = normalizedType,
                    title = args.title.trim(),
                    summary = args.summary.trim(),
                    keywords = json.encodeToString(args.keywords.map { it.trim() }.filter { it.isNotBlank() }),
                    sourceMessageIds = json.encodeToString(args.sourceMessageIds.map { it.trim() }.filter { it.isNotBlank() }),
                    startAt = args.startAt,
                    endAt = args.endAt,
                    importance = args.importance.coerceIn(0f, 1f),
                    createdAt = now,
                    updatedAt = now,
                    lastAccessed = now,
                )
            )
            """{"status":"saved","summaryId":"$summaryId"}"""
        }

    private companion object {
        val json = Json { encodeDefaults = true }
    }

    private fun invalidType(type: String): String =
        buildJsonObject {
            put("status", "error")
            put("reason", "invalid_summary_type")
            put("type", type)
            put("allowedTypes", SummaryType.entries.joinToString(",") { it.name })
        }.toString()
}
