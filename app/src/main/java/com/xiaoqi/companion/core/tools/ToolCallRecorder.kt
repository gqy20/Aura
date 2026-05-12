package com.xiaoqi.companion.core.tools

import com.xiaoqi.companion.data.db.dao.ToolCallDao
import com.xiaoqi.companion.data.db.entity.ToolCallEntity
import java.util.UUID
import javax.inject.Inject

class ToolCallRecorder @Inject constructor(
    private val dao: ToolCallDao,
) {
    suspend fun record(
        sessionId: String,
        toolName: String,
        argumentsJson: String,
        block: suspend () -> String,
    ): String {
        val id = UUID.randomUUID().toString()
        val startedAt = System.currentTimeMillis()
        dao.insert(
            ToolCallEntity(
                id = id,
                sessionId = sessionId,
                toolName = toolName,
                argumentsJson = argumentsJson,
                status = "RUNNING",
                createdAt = startedAt,
            )
        )

        return try {
            val result = block()
            dao.updateResult(
                id = id,
                status = "SUCCESS",
                resultJson = result,
                errorMessage = null,
                completedAt = System.currentTimeMillis(),
            )
            result
        } catch (e: Throwable) {
            dao.updateResult(
                id = id,
                status = "FAILED",
                resultJson = "",
                errorMessage = e.message ?: e::class.java.simpleName,
                completedAt = System.currentTimeMillis(),
            )
            throw e
        }
    }
}
