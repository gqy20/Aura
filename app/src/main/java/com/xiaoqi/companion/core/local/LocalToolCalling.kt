package com.xiaoqi.companion.core.local

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.JSONObject
import ai.koog.serialization.kotlinx.KotlinxSerializer
import com.xiaoqi.companion.core.companion.KoogAgentEvent
import com.xiaoqi.companion.core.companion.model.AgentToolCall
import com.xiaoqi.companion.core.companion.model.ToolCallStatus
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.core.tools.LocalToolPromptResult
import com.xiaoqi.companion.core.tools.ToolCallRecorder
import com.xiaoqi.companion.core.tools.ToolEnvelopeFactory
import com.xiaoqi.companion.core.tools.ToolResultPromptComposer
import com.xiaoqi.companion.core.tools.encode
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
internal data class LocalToolCall(
    val name: String,
    val arguments: JsonObject = buildJsonObject { },
    val id: String? = null,
)

@Serializable
internal data class LocalToolCallBatch(
    @SerialName("tool_calls")
    val toolCalls: List<LocalToolCall> = emptyList(),
)

internal data class LocalToolExecutionResult(
    val events: List<KoogAgentEvent>,
    val transcripts: List<LocalToolPromptResult>,
)

internal object LocalToolProtocol {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parseToolCalls(raw: String): List<LocalToolCall> {
        val cleaned = raw
            .replace(Regex("```json\\s*"), "")
            .replace(Regex("```\\s*"), "")
            .trim()
        if (cleaned.isBlank()) return emptyList()

        val candidates = listOfNotNull(
            extractJsonBlock(cleaned),
            cleaned.takeIf { it.startsWith('{') },
        )
        candidates.forEach { candidate ->
            runCatching { json.decodeFromString(LocalToolCallBatch.serializer(), candidate) }
                .getOrNull()
                ?.toolCalls
                ?.takeIf { it.isNotEmpty() }
                ?.let { return it }
        }
        return emptyList()
    }

    fun buildToolInstructionBlock(registry: ToolRegistry): String {
        if (registry.tools.isEmpty()) return ""
        val toolSchema = buildJsonArray {
            registry.tools.forEach { tool ->
                add(
                    buildJsonObject {
                        put("name", tool.name)
                        put("description", tool.descriptor.description)
                    }
                )
            }
        }
        return """
            You may call tools when needed.
            If you want to use tools, output JSON only in this exact shape:
            {"tool_calls":[{"name":"tool_name","arguments":{"key":"value"}}]}
            Do not include any extra prose before or after the JSON.
            Available tools:
            $toolSchema
        """.trimIndent()
    }

    fun buildToolContextBlock(results: List<LocalToolPromptResult>): String =
        ToolResultPromptComposer.localToolContextBlock(results)

    fun roundLimitFallbackMessage(): String =
        "我已经拿到部分工具结果，但本地工具调用轮次已到上限。你可以换个问法，或减少一次请求里的任务数。"

    private fun extractJsonBlock(text: String): String? {
        val start = text.indexOfFirst { it == '{' }
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until text.length) {
            val c = text[i]
            if (escape) {
                escape = false
                continue
            }
            if (c == '\\') {
                escape = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (inString) continue
            when (c) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }
}

internal class LocalToolExecutor(
    private val registry: ToolRegistry,
    private val recorder: ToolCallRecorder?,
    private val sessionId: String,
) {
    private val serializer: JSONSerializer = KotlinxSerializer()

    suspend fun execute(toolCalls: List<LocalToolCall>): LocalToolExecutionResult {
        val events = mutableListOf<KoogAgentEvent>()
        val transcripts = mutableListOf<LocalToolPromptResult>()
        toolCalls.forEach { call ->
            val callId = call.id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
            val tool = registry.getToolOrNull(call.name)
            if (tool == null) {
                events += KoogAgentEvent.ToolCallUpdated(
                    AgentToolCall(
                        name = call.name,
                        status = ToolCallStatus.FAILED,
                        callId = callId,
                        argumentsJson = call.arguments.toString(),
                        errorMessage = "Tool not found",
                    )
                )
                transcripts += LocalToolPromptResult(
                    id = callId,
                    name = call.name,
                    result = encode(
                        ToolEnvelopeFactory.err(
                            reason = "tool_not_found",
                            hint = "这个工具当前不可用，请换一种问法，或者稍后再试。",
                        )
                    ),
                    isError = true,
                )
                return@forEach
            }

            recorder?.start(
                sessionId = sessionId,
                callId = callId,
                toolName = call.name,
                argumentsJson = call.arguments.toString(),
            )
            events += KoogAgentEvent.ToolCallUpdated(
                AgentToolCall(
                    name = call.name,
                    status = ToolCallStatus.STARTED,
                    callId = callId,
                    argumentsJson = call.arguments.toString(),
                )
            )

            runCatching {
                @OptIn(InternalAgentToolsApi::class)
                val decoded = tool.decodeArgs(call.arguments.toKoogJsonObject(), serializer)
                @OptIn(InternalAgentToolsApi::class)
                val result = tool.executeUnsafe(decoded)
                @OptIn(InternalAgentToolsApi::class)
                tool.encodeResultToStringUnsafe(result, serializer)
            }.onSuccess { result ->
                recorder?.succeed(callId, result)
                events += KoogAgentEvent.ToolCallUpdated(
                    AgentToolCall(
                        name = call.name,
                        status = ToolCallStatus.SUCCEEDED,
                        callId = callId,
                        argumentsJson = call.arguments.toString(),
                        resultJson = result,
                    )
                )
                transcripts += LocalToolPromptResult(
                    id = callId,
                    name = call.name,
                    result = result,
                )
            }.onFailure { error ->
                val message = error.message ?: error::class.simpleName.orEmpty()
                AppLogger.warn(
                    LogTags.LocalModel,
                    "local_tool_execution_failed",
                    "toolName" to call.name,
                    "callId" to callId,
                    "error" to message,
                )
                recorder?.fail(callId, message)
                events += KoogAgentEvent.ToolCallUpdated(
                    AgentToolCall(
                        name = call.name,
                        status = ToolCallStatus.FAILED,
                        callId = callId,
                        argumentsJson = call.arguments.toString(),
                        errorMessage = message,
                    )
                )
                transcripts += LocalToolPromptResult(
                    id = callId,
                    name = call.name,
                    result = encode(
                        ToolEnvelopeFactory.err(
                            reason = "tool_execution_failed",
                            hint = "这个工具刚才执行失败了。请先基于已有信息回答，必要时提示用户稍后重试。",
                            details = mapOf("message" to message),
                        )
                    ),
                    isError = true,
                )
            }
        }
        return LocalToolExecutionResult(
            events = events,
            transcripts = transcripts,
        )
    }

    private fun JsonObject.toKoogJsonObject(): JSONObject =
        serializer.decodeJSONElementFromString(toString()) as JSONObject
}
