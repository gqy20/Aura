package com.xiaoqi.companion.core.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import com.xiaoqi.companion.data.db.dao.AgentStateDao
import com.xiaoqi.companion.data.db.entity.AgentStateEntity
import java.util.UUID
import kotlinx.serialization.Serializable
import javax.inject.Inject

class UpdateRelationshipTool(
    private val agentStateDao: AgentStateDao,
    private val companionIdProvider: () -> String = { "default" },
) : SimpleTool<UpdateRelationshipTool.Args>(
    typeToken<Args>(),
    name = "update_relationship",
    description = "Update the relationship affinity level with the user. Persists to agent state for cross-session continuity.",
) {

    @Inject
    constructor(
        agentStateDao: AgentStateDao,
    ) : this(
        agentStateDao = agentStateDao,
        companionIdProvider = { "default" },
    )

    @Serializable
    data class Args(
        @param:LLMDescription("Change in relationship level (e.g. +0.1 for positive, -0.05 for negative).")
        val delta: Float,
        @param:LLMDescription("Reason for the change. Optional context.")
        val reason: String? = null,
    )

    override suspend fun execute(args: Args): String {
        val companionId = companionIdProvider()
        val existing = agentStateDao.getByCompanionId(companionId)
        val now = System.currentTimeMillis()
        val newLevel = ((existing?.relationshipLevel ?: 0f) + args.delta).coerceIn(0f, 1f)

        if (existing != null) {
            agentStateDao.updateRelationshipLevel(companionId, newLevel, now)
        } else {
            agentStateDao.insert(
                AgentStateEntity(
                    id = UUID.randomUUID().toString(),
                    companionId = companionId,
                    mood = "",
                    relationshipLevel = newLevel,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        }

        return """{"status":"updated","level":$newLevel,"delta":${args.delta}}"""
    }
}
