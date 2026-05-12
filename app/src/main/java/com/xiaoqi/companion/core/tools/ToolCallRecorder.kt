package com.xiaoqi.companion.core.tools

import com.xiaoqi.companion.data.db.dao.ToolCallDao
import com.xiaoqi.companion.data.db.entity.ToolCallEntity
import java.util.UUID
import javax.inject.Inject

class ToolCallRecorder @Inject constructor(
    private val dao: ToolCallDao,
) {
    suspend fun start(
        sessionId: String,
        callId: String,
        toolName: String,
        argumentsJson: String,
    ) {
        dao.insert(
            ToolCallEntity(
                id = callId,
                sessionId = sessionId,
                toolName = toolName,
                argumentsJson = argumentsJson,
                status = "RUNNING",
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun succeed(
        callId: String,
        resultJson: String,
    ) {
        dao.updateResult(
            id = callId,
            status = "SUCCESS",
            resultJson = resultJson,
            errorMessage = null,
            completedAt = System.currentTimeMillis(),
        )
    }

    suspend fun fail(
        callId: String,
        errorMessage: String?,
    ) {
        dao.updateResult(
            id = callId,
            status = "FAILED",
            resultJson = "",
            errorMessage = errorMessage,
            completedAt = System.currentTimeMillis(),
        )
    }

    suspend fun record(
        sessionId: String,
        toolName: String,
        argumentsJson: String,
        block: suspend () -> String,
    ): String {
        val id = UUID.randomUUID().toString()
        start(
            sessionId = sessionId,
            callId = id,
            toolName = toolName,
            argumentsJson = argumentsJson,
        )

        return try {
            val result = block()
            succeed(callId = id, resultJson = result)
            result
        } catch (e: Throwable) {
            fail(callId = id, errorMessage = e.message ?: e::class.java.simpleName)
            throw e
        }
    }
}
