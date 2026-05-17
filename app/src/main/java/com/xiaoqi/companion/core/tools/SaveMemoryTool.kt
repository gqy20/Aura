package com.xiaoqi.companion.core.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import com.xiaoqi.companion.data.db.converter.MemoryType
import com.xiaoqi.companion.data.repository.MemoryRepository
import com.xiaoqi.companion.data.repository.SaveMemoryRequest
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SaveMemoryTool @Inject constructor(
    private val memoryRepository: MemoryRepository,
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
        @param:LLMDescription("Confidence from 0.0 to 1.0. Use lower values for inferred or uncertain memories.")
        val confidence: Float = 0.7f,
        @param:LLMDescription("Sensitivity: normal, private, or sensitive.")
        val sensitivity: String = "normal",
    )

    override suspend fun execute(args: Args): String =
        withContext(Dispatchers.IO) {
            val normalizedType = MemoryType.entries.firstOrNull { it.name == args.type.uppercase() }
                ?: return@withContext invalidType(args.type)
            val result = memoryRepository.saveMemory(
                SaveMemoryRequest(
                    content = args.content,
                    type = normalizedType,
                    importance = args.importance,
                    confidence = args.confidence,
                    sensitivity = args.sensitivity,
                )
            )
            """{"status":"saved","memoryId":"${result.memory.id}","merged":${result.merged}}"""
        }

    private fun invalidType(type: String): String =
        buildJsonObject {
            put("status", "error")
            put("reason", "invalid_memory_type")
            put("type", type)
            put("allowedTypes", MemoryType.entries.joinToString(",") { it.name })
        }.toString()
}
