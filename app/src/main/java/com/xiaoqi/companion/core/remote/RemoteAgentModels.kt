package com.xiaoqi.companion.core.remote

import com.xiaoqi.companion.core.companion.model.AgentError
import com.xiaoqi.companion.core.companion.model.AgentEvent
import com.xiaoqi.companion.core.companion.model.AgentToolCall
import com.xiaoqi.companion.core.companion.model.ToolCallStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class RemoteAgentTurnRequest(
    val sessionId: String,
    val inputText: String,
    val allowTools: Boolean = true,
    val readOnlyToolsOnly: Boolean = true,
)

@Serializable
data class RemoteReadOnlyToolRequest(
    val sessionId: String,
    val toolName: String,
    val arguments: JsonObject,
)

@Serializable
data class RemoteReadOnlyToolResult(
    val toolName: String,
    val resultJson: String,
)

@Serializable
data class RemoteAgentEventDto(
    val type: String,
    val text: String? = null,
    val runId: String? = null,
    val status: String? = null,
    val stage: String? = null,
    val message: String? = null,
    val toolName: String? = null,
    val toolStatus: String? = null,
    val callId: String? = null,
    val argumentsJson: String? = null,
    val resultJson: String? = null,
    val errorMessage: String? = null,
)

fun RemoteAgentEventDto.toAgentEvent(): AgentEvent =
    when (type) {
        "delta" -> AgentEvent.Streaming(text.orEmpty())
        "progress" -> AgentEvent.Progress(stage = stage.orEmpty(), message = message.orEmpty())
        "remote_status" -> AgentEvent.RemoteStatus(runId = runId.orEmpty(), status = status.orEmpty())
        "tool" -> AgentEvent.ToolCallUpdated(
            AgentToolCall(
                name = toolName.orEmpty(),
                status = toolStatus.toToolCallStatus(),
                callId = callId,
                argumentsJson = argumentsJson,
                resultJson = resultJson,
                errorMessage = errorMessage,
            )
        )
        "complete" -> AgentEvent.Complete(text.orEmpty())
        "error" -> AgentEvent.Error(AgentError.ApiError(message ?: errorMessage ?: "Remote agent error"))
        else -> AgentEvent.Progress(stage = "remote_unknown", message = type)
    }

private fun String?.toToolCallStatus(): ToolCallStatus =
    when (this?.lowercase()) {
        "succeeded", "success", "completed" -> ToolCallStatus.SUCCEEDED
        "failed", "error" -> ToolCallStatus.FAILED
        else -> ToolCallStatus.STARTED
    }
