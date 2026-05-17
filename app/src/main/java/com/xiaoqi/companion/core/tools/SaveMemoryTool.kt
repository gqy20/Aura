package com.xiaoqi.companion.core.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import com.xiaoqi.companion.data.db.converter.MemoryType
import com.xiaoqi.companion.data.db.dao.MemoryDao
import com.xiaoqi.companion.data.db.entity.MemoryEntity
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SaveMemoryTool @Inject constructor(
    private val memoryDao: MemoryDao,
) : SimpleTool<SaveMemoryTool.Args>(
    typeToken<Args>(),
    name = "save_memory",
    description = "Save an important user fact, preference, or episode into long-term memory.",
) {

    @Serializable
    data class Args(
        @param:LLMDescription("The memory content to save. Keep it concise and factual.")
        val content: String,
        @param:LLMDescription("Memory type. Use FACT, EPISODE, or PROCEDURAL.")
        val type: String = "FACT",
        @param:LLMDescription("Importance from 0.0 to 1.0.")
        val importance: Float = 0.5f,
    )

    override suspend fun execute(args: Args): String =
        withContext(Dispatchers.IO) {
            val normalizedType = MemoryType.entries.firstOrNull { it.name == args.type.uppercase() }
                ?: return@withContext invalidType(args.type)
            val safeImportance = args.importance.coerceIn(0f, 1f)
            val memoryId = UUID.randomUUID().toString()
            memoryDao.insert(
                MemoryEntity(
                    id = memoryId,
                    type = normalizedType,
                    content = args.content,
                    source = "tool:save_memory",
                    importance = safeImportance,
                    timestamp = System.currentTimeMillis(),
                )
            )
            """{"status":"saved","memoryId":"$memoryId"}"""
        }

    private fun invalidType(type: String): String =
        buildJsonObject {
            put("status", "error")
            put("reason", "invalid_memory_type")
            put("type", type)
            put("allowedTypes", MemoryType.entries.joinToString(",") { it.name })
        }.toString()
}
