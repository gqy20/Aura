package com.xiaoqi.companion.core.llm

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import java.io.IOException
import javax.inject.Inject
import kotlin.random.Random
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.Closeable
import java.util.concurrent.TimeUnit

private const val DEFAULT_MAX_TOKENS = 4096
private const val ANTHROPIC_VERSION = "2023-06-01"

data class LlmRetryPolicy(
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 300,
    val maxDelayMs: Long = 2_000,
) {
    init {
        require(maxAttempts >= 1)
        require(initialDelayMs >= 0)
        require(maxDelayMs >= initialDelayMs)
    }
}

private class LlmHttpException(
    val statusCode: Int,
    val retryAfterMs: Long?,
    val errorBodyPreview: String?,
) : IOException(buildString {
    append("HTTP $statusCode")
    if (!errorBodyPreview.isNullOrBlank()) append(": $errorBodyPreview")
})

private data class PendingToolCall(
    val id: String?,
    val name: String,
    val input: StringBuilder = StringBuilder(),
)

private data class NormalizedToolInput(
    val content: String,
    val repaired: Boolean,
    val valid: Boolean,
)

class AnthropicMessagesLLMClient(
    private val apiKey: String,
    private val baseUrl: String,
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val clock: Clock = Clock.System,
    private val retryPolicy: LlmRetryPolicy = LlmRetryPolicy(),
) : LLMClient() {

    override val clientName: String = "anthropic-messages"

    override fun llmProvider(): LLMProvider = LLMProvider.Anthropic

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): List<Message.Response> {
        val startedAt = System.currentTimeMillis()
        val response = executeRequest(prompt, model, stream = false, tools = tools)
        val usage = response["usage"]?.jsonObject
        val stopReason = response["stop_reason"]?.jsonPrimitive?.contentOrNull

        val responses = when (stopReason) {
            "tool_use" -> {
                val toolResponses = extractToolUseResponses(response, usage)
                AppLogger.info(
                    LogTags.Llm,
                    "response_tool_use",
                    "model" to model.id,
                    "durationMs" to (System.currentTimeMillis() - startedAt),
                    "toolCount" to toolResponses.size,
                    "toolNames" to toolResponses.mapNotNull { (it as? Message.Tool.Call)?.tool }.joinToString(","),
                    "inputTokens" to usage?.get("input_tokens")?.jsonPrimitive?.intOrNull,
                    "outputTokens" to usage?.get("output_tokens")?.jsonPrimitive?.intOrNull,
                )
                toolResponses
            }
            else -> {
                val text = extractTextFromMessageResponse(response)
                AppLogger.info(
                    LogTags.Llm,
                    "request_completed",
                    "model" to model.id,
                    "stream" to false,
                    "durationMs" to (System.currentTimeMillis() - startedAt),
                    "stopReason" to (stopReason ?: "null"),
                    "responseLength" to text.length,
                    "inputTokens" to usage?.get("input_tokens")?.jsonPrimitive?.intOrNull,
                    "outputTokens" to usage?.get("output_tokens")?.jsonPrimitive?.intOrNull,
                )
                listOf(
                    Message.Assistant(
                        content = text,
                        metaInfo = responseMetaInfo(usage),
                        finishReason = stopReason,
                    )
                )
            }
        }
        return responses
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Flow<StreamFrame> = callbackFlow {
        val startedAt = System.currentTimeMillis()
        var fullText = ""
        var finishReason: String? = null
        val pendingTools = mutableMapOf<Int, PendingToolCall>()
        var hasEmittedFrame = false

        fun completeTool(index: Int) {
            val tool = pendingTools.remove(index) ?: return
            val normalized = normalizeToolInput(tool.input.toString())
            hasEmittedFrame = true
            trySend(
                StreamFrame.ToolCallComplete(
                    id = tool.id,
                    name = tool.name,
                    content = normalized.content,
                    index = index,
                )
            )
            AppLogger.info(
                LogTags.Llm,
                "stream_tool_completed",
                "tool" to tool.name,
                "index" to index,
                "inputLength" to tool.input.length,
                "inputRepaired" to normalized.repaired,
                "inputValid" to normalized.valid,
            )
        }

        val job = launch(Dispatchers.IO) {
            try {
                AppLogger.info(
                    LogTags.Llm,
                    "stream_request_started",
                    "model" to model.id,
                    "toolCount" to tools.size,
                )
                withTransientRetry(
                    modelId = model.id,
                    operation = "stream",
                    canRetry = { !hasEmittedFrame },
                ) {
                    finishReason = null
                    pendingTools.clear()
                    val request = buildRequest(prompt, model, stream = true, tools = tools)
                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            val errorBody = response.body?.string().orEmpty()
                            AppLogger.warn(
                                LogTags.Llm,
                                "stream_http_error",
                                "statusCode" to response.code,
                                "model" to model.id,
                                "errorBodyPreview" to sanitizeErrorBody(errorBody),
                            )
                            throw response.toHttpException(errorBody)
                        }

                        val source = response.body?.source()
                            ?: throw IOException("Streaming response body is missing")
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: continue
                            if (!line.startsWith("data: ")) continue

                            val data = line.removePrefix("data: ").trim()
                            if (data == "[DONE]") break

                            val parsed = parseSseData(data)
                            if (parsed.isToolUseBlock && parsed.toolName != null) {
                                val index = parsed.index ?: nextSyntheticToolIndex(pendingTools)
                                pendingTools[index] = PendingToolCall(parsed.toolId, parsed.toolName)
                                AppLogger.debug(
                                    LogTags.Llm,
                                    "stream_tool_start",
                                    "tool" to parsed.toolName,
                                    "index" to index,
                                    "indexMissing" to (parsed.index == null),
                                )
                            }
                            if (parsed.toolInput != null) {
                                val pending = parsed.index?.let(pendingTools::get)
                                    ?: pendingTools.values.singleOrNull()
                                if (pending != null) {
                                    pending.input.append(parsed.toolInput)
                                } else {
                                    AppLogger.warn(
                                        LogTags.Llm,
                                        "stream_tool_delta_orphaned",
                                        "index" to parsed.index,
                                        "deltaLength" to parsed.toolInput.length,
                                        "pendingCount" to pendingTools.size,
                                    )
                                }
                            }
                            if (parsed.deltaText != null) {
                                fullText += parsed.deltaText
                                hasEmittedFrame = true
                                trySend(StreamFrame.TextDelta(parsed.deltaText, null))
                            }
                            if (parsed.isContentBlockStop) {
                                val stoppedIndex = parsed.index
                                    ?: pendingTools.keys.singleOrNull { it < 0 }
                                stoppedIndex?.let(::completeTool)
                            }
                            if (parsed.finishReason != null) {
                                finishReason = parsed.finishReason
                            }
                        }
                    }
                }

                if (fullText.isNotEmpty()) {
                    trySend(StreamFrame.TextComplete(fullText, null))
                }

                if (pendingTools.isNotEmpty()) {
                    val incompleteTools = pendingTools.values.joinToString(",") { it.name }
                    AppLogger.warn(
                        LogTags.Llm,
                        "stream_tool_incomplete",
                        "indexes" to pendingTools.keys.sorted().joinToString(","),
                        "tools" to incompleteTools,
                    )
                    pendingTools.clear()
                    throw IOException("Streaming response ended with incomplete tool calls: $incompleteTools")
                }
                AppLogger.info(
                    LogTags.Llm,
                    "stream_request_completed",
                    "model" to model.id,
                    "durationMs" to (System.currentTimeMillis() - startedAt),
                    "responseLength" to fullText.length,
                    "finishReason" to finishReason,
                )
                trySend(StreamFrame.End(finishReason, ResponseMetaInfo(clock.now())))
                close()
            } catch (e: Exception) {
                AppLogger.error(
                    LogTags.Llm,
                    e,
                    "stream_request_failed",
                    "model" to model.id,
                    "durationMs" to (System.currentTimeMillis() - startedAt),
                    "responseLength" to fullText.length,
                    "finishReason" to finishReason,
                )
                close(e)
            }
        }

        awaitClose { job.cancel() }
    }

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
        ModerationResult(false, emptyMap())

    override suspend fun models(): List<LLModel> = emptyList()

    override fun close() {
        (httpClient as? Closeable)?.close()
    }

    private suspend fun executeRequest(prompt: Prompt, model: LLModel, stream: Boolean, tools: List<ToolDescriptor>): JsonObject =
        withContext(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            withTransientRetry(
                modelId = model.id,
                operation = if (stream) "stream" else "request",
            ) {
                val request = buildRequest(prompt, model, stream, tools)
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        AppLogger.warn(
                            LogTags.Llm,
                            "request_http_error",
                            "statusCode" to response.code,
                            "model" to model.id,
                            "stream" to stream,
                            "durationMs" to (System.currentTimeMillis() - startedAt),
                            "errorBodyLength" to body.length,
                        )
                        throw response.toHttpException(body)
                    }
                    json.parseToJsonElement(body).jsonObject
                }
            }
        }

    private suspend fun <T> withTransientRetry(
        modelId: String,
        operation: String,
        canRetry: () -> Boolean = { true },
        block: suspend () -> T,
    ): T {
        var attempt = 1
        while (true) {
            try {
                return block()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                val shouldRetry = attempt < retryPolicy.maxAttempts &&
                    canRetry() &&
                    error.isTransientLlmFailure()
                if (!shouldRetry) throw error

                val delayMs = retryDelayMs(attempt, error)
                AppLogger.warn(
                    LogTags.Llm,
                    "llm_request_retry_scheduled",
                    "model" to modelId,
                    "operation" to operation,
                    "attempt" to attempt,
                    "nextAttempt" to (attempt + 1),
                    "delayMs" to delayMs,
                    "statusCode" to (error as? LlmHttpException)?.statusCode,
                    "cause" to error::class.simpleName,
                )
                delay(delayMs)
                attempt += 1
            }
        }
    }

    private fun retryDelayMs(attempt: Int, error: Exception): Long {
        val serverDelay = (error as? LlmHttpException)?.retryAfterMs
        if (serverDelay != null) return serverDelay.coerceAtMost(retryPolicy.maxDelayMs)
        var delayMs = retryPolicy.initialDelayMs
        repeat((attempt - 1).coerceAtLeast(0)) {
            delayMs = (delayMs * 2).coerceAtMost(retryPolicy.maxDelayMs)
        }
        if (delayMs == 0L) return 0L
        val jitter = (delayMs / 5).coerceAtLeast(1)
        return (delayMs + Random.nextLong(-jitter, jitter + 1))
            .coerceIn(0, retryPolicy.maxDelayMs)
    }

    private fun Exception.isTransientLlmFailure(): Boolean = when (this) {
        is LlmHttpException -> statusCode == 408 || statusCode == 429 || statusCode in 500..599
        is IOException -> true
        else -> false
    }

    private fun Response.toHttpException(errorBody: String): LlmHttpException =
        LlmHttpException(
            statusCode = code,
            retryAfterMs = header("Retry-After")
                ?.trim()
                ?.toLongOrNull()
                ?.times(1_000),
            errorBodyPreview = sanitizeErrorBody(errorBody),
        )

    private fun buildRequest(prompt: Prompt, model: LLModel, stream: Boolean, tools: List<ToolDescriptor>): Request {
        require(baseUrl.isNotBlank()) { "LLM_BASE_URL is not configured" }
        require(apiKey.isNotBlank()) { "LLM_API_KEY is not configured" }
        val body = buildRequestBody(prompt, model, stream, tools)
        AppLogger.debug(
            LogTags.Llm,
            "request_built",
            "model" to model.id,
            "stream" to stream,
            "systemMessageCount" to prompt.messages.filterIsInstance<Message.System>().size,
            "messageCount" to prompt.messages.count { it !is Message.System },
            "messageShape" to body.messageShape(),
            "toolCount" to tools.size,
            "hasApiKey" to apiKey.isNotBlank(),
        )
        return Request.Builder()
            .url("${baseUrl.trimEnd('/')}/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .header("content-type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun buildRequestBody(prompt: Prompt, model: LLModel, stream: Boolean, tools: List<ToolDescriptor>): JsonObject =
        buildJsonObject {
            put("model", model.id)
            put("max_tokens", prompt.params.maxTokens ?: model.maxOutputTokens?.toInt() ?: DEFAULT_MAX_TOKENS)
            prompt.params.temperature?.let { put("temperature", it) }
            put("stream", stream)

            val systemText = prompt.messages
                .filterIsInstance<Message.System>()
                .joinToString("\n") { it.content }
                .trim()
            if (systemText.isNotEmpty()) {
                put("system", systemText)
            }

            put(
                "messages",
                buildJsonArray {
                    prompt.messages
                        .filterNot { it is Message.System }
                        .forEach { message ->
                            add(message.toAnthropicMessage())
                        }
                }
            )

            if (tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    tools.forEach { tool ->
                        add(buildJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("input_schema", tool.toAnthropicInputSchema())
                        })
                    }
                })
            }
        }

    private fun ToolDescriptor.toAnthropicInputSchema(): JsonObject {
        val allParams = requiredParameters + optionalParameters
        val requiredNames = requiredParameters.map { it.name }.toSet()
        return buildJsonObject {
            put("type", JsonPrimitive("object"))
            put("properties", buildJsonObject {
                allParams.forEach { param ->
                    put(param.name, buildJsonObject {
                        put("type", JsonPrimitive(param.type.toString()))
                        put("description", JsonPrimitive(param.description))
                    })
                }
            })
            if (requiredNames.isNotEmpty()) {
                put("required", buildJsonArray { requiredNames.forEach { add(JsonPrimitive(it)) } })
            }
        }
    }

    private fun Message.toAnthropicMessage(): JsonObject = when (this) {
        is Message.Tool.Call -> buildJsonObject {
            put("role", "assistant")
            put("content", buildJsonArray {
                add(buildJsonObject {
                    put("type", "tool_use")
                    put("id", id)
                    put("name", tool)
                    put("input", content.toToolInput())
                })
            })
        }
        is Message.Tool.Result -> buildJsonObject {
            put("role", "user")
            put("content", buildJsonArray {
                add(buildJsonObject {
                    put("type", "tool_result")
                    put("tool_use_id", id)
                    put("content", content)
                    put("is_error", isError)
                })
            })
        }
        else -> buildJsonObject {
            put("role", if (this@toAnthropicMessage is Message.Response) "assistant" else "user")
            put("content", parts.toAnthropicContent())
        }
    }

    private fun String.toToolInput(): JsonObject =
        runCatching { json.parseToJsonElement(normalizeToolInput(this).content).jsonObject }
            .getOrElse {
                buildJsonObject { put("_raw", this@toToolInput) }
            }

    private fun List<ContentPart>.toAnthropicContent(): JsonArray =
        buildJsonArray {
            forEach { part ->
                when (part) {
                    is ContentPart.Text -> add(buildJsonObject {
                        put("type", "text")
                        put("text", part.text)
                    })
                    is ContentPart.Image -> add(part.toAnthropicImageBlock())
                    else -> {
                        val text = part.toString()
                        if (text.isNotBlank()) {
                            add(buildJsonObject {
                                put("type", "text")
                                put("text", text)
                            })
                        }
                    }
                }
            }
        }

    private fun ContentPart.Image.toAnthropicImageBlock(): JsonObject =
        buildJsonObject {
            put("type", "image")
            put(
                "source",
                buildJsonObject {
                    when (val attachment = content) {
                        is AttachmentContent.Binary.Base64 -> {
                            put("type", "base64")
                            put("media_type", mimeType)
                            put("data", attachment.base64)
                        }
                        is AttachmentContent.URL -> {
                            put("type", "url")
                            put("url", attachment.url)
                        }
                        else -> {
                            put("type", "base64")
                            put("media_type", mimeType)
                            put("data", attachment.toString())
                        }
                    }
                }
            )
        }

    private fun extractTextFromMessageResponse(response: JsonObject): String =
        response["content"]?.jsonArray
            ?.mapNotNull { block ->
                block.jsonObject["text"]?.jsonPrimitive?.contentOrNull
            }
            ?.joinToString("")
            .orEmpty()

    private fun extractToolUseResponses(response: JsonObject, usage: JsonObject?): List<Message.Response> =
        response["content"]?.jsonArray
            ?.mapNotNull { block ->
                val blockObj = block.jsonObject
                if (blockObj["type"]?.jsonPrimitive?.contentOrNull == "tool_use") {
                    val toolId = blockObj["id"]?.jsonPrimitive?.contentOrNull ?: ""
                    val toolName = blockObj["name"]?.jsonPrimitive?.contentOrNull ?: ""
                    val toolInput = blockObj["input"]?.toString() ?: "{}"
                    AppLogger.debug(
                        LogTags.Llm,
                        "extracted_tool_call",
                        "toolId" to toolId,
                        "toolName" to toolName,
                        "inputLength" to toolInput.length,
                        "inputPreview" to toolInput.take(200),
                    )
                    Message.Tool.Call(
                        id = toolId,
                        tool = toolName,
                        content = toolInput,
                        metaInfo = responseMetaInfo(usage),
                    )
                } else null
            }
            ?.also {
                AppLogger.debug(LogTags.Llm, "tool_calls_extracted", "count" to it.size)
            }
            ?: emptyList()

    private fun parseSseData(data: String): ParsedSse {
        val event = runCatching { json.parseToJsonElement(data).jsonObject }
            .onFailure { parseError ->
                AppLogger.debug(
                    LogTags.Llm,
                    "sse_event_parse_failed",
                    "dataLength" to data.length,
                    "error" to (parseError.message ?: parseError::class.simpleName.orEmpty()),
                )
            }
            .getOrNull()
            ?: return ParsedSse()
        val type = event["type"]?.jsonPrimitive?.contentOrNull
        val index = event["index"]?.jsonPrimitive?.intOrNull
        if (type == "message_delta") {
            return ParsedSse(
                finishReason = event["delta"]?.jsonObject
                    ?.get("stop_reason")?.jsonPrimitive?.contentOrNull
            )
        }
        if (type == "content_block_start") {
            val block = event["content_block"]?.jsonObject ?: return ParsedSse()
            if (block["type"]?.jsonPrimitive?.contentOrNull == "tool_use") {
                return ParsedSse(
                    index = index,
                    isToolUseBlock = true,
                    toolId = block["id"]?.jsonPrimitive?.contentOrNull,
                    toolName = block["name"]?.jsonPrimitive?.contentOrNull,
                )
            }
            return ParsedSse()
        }
        if (type == "content_block_stop") {
            return ParsedSse(index = index, isContentBlockStop = true)
        }
        if (type != "content_block_delta") return ParsedSse()

        val delta = event["delta"]?.jsonObject ?: return ParsedSse()
        val deltaType = delta["type"]?.jsonPrimitive?.contentOrNull
        return when (deltaType) {
            "text_delta" -> ParsedSse(index = index, deltaText = delta["text"]?.jsonPrimitive?.contentOrNull)
            "input_json_delta" -> ParsedSse(index = index, toolInput = delta["partial_json"]?.jsonPrimitive?.contentOrNull)
            else -> ParsedSse()
        }
    }

    private fun responseMetaInfo(usage: JsonObject?): ResponseMetaInfo =
        ResponseMetaInfo(
            timestamp = clock.now(),
            inputTokensCount = usage?.get("input_tokens")?.jsonPrimitive?.intOrNull,
            outputTokensCount = usage?.get("output_tokens")?.jsonPrimitive?.intOrNull,
        )

    private data class ParsedSse(
        val index: Int? = null,
        val deltaText: String? = null,
        val finishReason: String? = null,
        val toolId: String? = null,
        val toolName: String? = null,
        val toolInput: String? = null,
        val isToolUseBlock: Boolean = false,
        val isContentBlockStop: Boolean = false,
    )

    private companion object {
        val json = Json { ignoreUnknownKeys = true }

        fun defaultHttpClient(): OkHttpClient =
            OkHttpClient.Builder()
                .callTimeout(60, TimeUnit.SECONDS)
                .build()
    }
}

private fun nextSyntheticToolIndex(pending: Map<Int, PendingToolCall>): Int =
    generateSequence(-1) { it - 1 }.first { it !in pending }

private fun normalizeToolInput(raw: String): NormalizedToolInput {
    if (runCatching { Json.parseToJsonElement(raw).jsonObject }.isSuccess) {
        return NormalizedToolInput(raw, repaired = false, valid = true)
    }
    val repaired = repairFlatJsonValues(raw)
    val valid = repaired != null && runCatching { Json.parseToJsonElement(repaired).jsonObject }.isSuccess
    return if (valid) {
        NormalizedToolInput(repaired.orEmpty(), repaired = true, valid = true)
    } else {
        NormalizedToolInput(raw, repaired = false, valid = false)
    }
}

private fun repairFlatJsonValues(raw: String): String? {
    val trimmed = raw.trim()
    if (!trimmed.startsWith('{') || !trimmed.endsWith('}')) return null
    var inString = false
    var escaped = false
    for (index in 1 until trimmed.lastIndex) {
        val char = trimmed[index]
        if (escaped) {
            escaped = false
        } else if (char == '\\' && inString) {
            escaped = true
        } else if (char == '"') {
            inString = !inString
        } else if (!inString && (char == '{' || char == '[')) {
            return null
        }
    }

    val valuePattern = Regex("""(:\s*)([^,}\s][^,}]*)\s*(?=,|})""")
    var changed = false
    val candidate = valuePattern.replace(trimmed) { match ->
        val prefix = match.groupValues[1]
        val token = match.groupValues[2].trim()
        val isAlreadyJson = token.startsWith('"') ||
            token == "true" || token == "false" || token == "null" || token.toDoubleOrNull() != null
        if (isAlreadyJson) {
            match.value
        } else {
            changed = true
            prefix + JsonPrimitive(token).toString()
        }
    }
    return candidate.takeIf { changed }
}

private fun sanitizeErrorBody(body: String): String? = body
    .replace(Regex("""(?i)(bearer\s+|api[_-]?key[\"']?\s*[:=]\s*[\"']?)[^\s\"',}]+"""), "$1[REDACTED]")
    .replace(Regex("\\s+"), " ")
    .trim()
    .take(400)
    .ifBlank { null }

private fun JsonObject.messageShape(): String = this["messages"]
    ?.jsonArray
    ?.joinToString(">") { message ->
        val obj = message.jsonObject
        val role = obj["role"]?.jsonPrimitive?.contentOrNull ?: "?"
        val types = obj["content"]?.jsonArray
            ?.mapNotNull { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull }
            ?.distinct()
            ?.joinToString("+")
            .orEmpty()
        "$role:${types.ifEmpty { "unknown" }}"
    }
    .orEmpty()

class AnthropicMessagesLLMClientFactory @Inject constructor() {
    fun create(apiKey: String, baseUrl: String): AnthropicMessagesLLMClient =
        AnthropicMessagesLLMClient(apiKey = apiKey, baseUrl = baseUrl)
}
