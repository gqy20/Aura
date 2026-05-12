package com.xiaoqi.companion.core.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import com.xiaoqi.companion.data.db.converter.MemoryType
import com.xiaoqi.companion.data.db.dao.MemoryDao
import com.xiaoqi.companion.data.db.entity.MemoryEntity
import java.util.UUID
import javax.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SaveMemoryTool(
    private val memoryDao: MemoryDao,
    private val recorder: ToolCallRecorder,
    private val sessionIdProvider: () -> String = { "default" },
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

    @Inject
    constructor(
        memoryDao: MemoryDao,
        recorder: ToolCallRecorder,
    ) : this(
        memoryDao = memoryDao,
        recorder = recorder,
        sessionIdProvider = { "default" },
    )

    override suspend fun execute(args: Args): String {
        val normalizedType = runCatching { MemoryType.valueOf(args.type.uppercase()) }
            .getOrDefault(MemoryType.FACT)
        val safeImportance = args.importance.coerceIn(0f, 1f)
        val argumentsJson = json.encodeToString(args)

        return recorder.record(
            sessionId = sessionIdProvider(),
            toolName = name,
            argumentsJson = argumentsJson,
        ) {
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
    }

    private companion object {
        val json = Json { encodeDefaults = true }
    }
}
