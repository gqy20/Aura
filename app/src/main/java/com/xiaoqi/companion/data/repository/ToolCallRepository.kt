package com.xiaoqi.companion.data.repository

import com.xiaoqi.companion.core.companion.model.ToolCallStatus
import com.xiaoqi.companion.data.db.dao.ToolCallDao
import com.xiaoqi.companion.data.db.entity.ToolCallEntity
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ToolCallRepository {
    fun observeBySession(sessionId: String): Flow<List<ToolCallSnapshot>>
}

class ToolCallRepositoryImpl @Inject constructor(
    private val dao: ToolCallDao,
) : ToolCallRepository {

    override fun observeBySession(sessionId: String): Flow<List<ToolCallSnapshot>> =
        dao.observeBySession(sessionId).map { calls ->
            calls.map { it.toSnapshot() }
        }

    private fun ToolCallEntity.toSnapshot(): ToolCallSnapshot =
        ToolCallSnapshot(
            id = id,
            sessionId = sessionId,
            toolName = toolName,
            status = when (status) {
                "SUCCESS" -> ToolCallStatus.SUCCEEDED
                "FAILED" -> ToolCallStatus.FAILED
                else -> ToolCallStatus.STARTED
            },
            argumentsJson = argumentsJson,
            resultJson = resultJson.takeIf { it.isNotBlank() },
            errorMessage = errorMessage,
            startedAt = createdAt,
            completedAt = completedAt,
        )
}

data class ToolCallSnapshot(
    val id: String,
    val sessionId: String,
    val toolName: String,
    val status: ToolCallStatus,
    val argumentsJson: String,
    val resultJson: String?,
    val errorMessage: String?,
    val startedAt: Long,
    val completedAt: Long?,
) {
    val durationMs: Long?
        get() = completedAt?.minus(startedAt)?.coerceAtLeast(0)
}
