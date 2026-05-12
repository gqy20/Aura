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
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.Closeable
import java.util.concurrent.TimeUnit

private const val DEFAULT_MAX_TOKENS = 1024
private const val ANTHROPIC_VERSION = "2023-06-01"

class AnthropicCompatLLMClient(
    private val apiKey: String,
    private val baseUrl: String,
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val clock: Clock = Clock.System,
) : LLMClient() {

    override val clientName: String = "anthropic-compatible"

    override fun llmProvider(): LLMProvider = LLMProvider.Anthropic

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): List<Message.Response> {
        val response = executeRequest(prompt, model, stream = false)
        val text = extractTextFromMessageResponse(response)
        val usage = response["usage"]?.jsonObject
        return listOf(
            Message.Assistant(
                content = text,
                metaInfo = responseMetaInfo(usage),
                finishReason = response["stop_reason"]?.jsonPrimitive?.contentOrNull,
            )
        )
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Flow<StreamFrame> = flow {
        val request = buildRequest(prompt, model, stream = true)
        var fullText = ""
        var finishReason: String? = null

        withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    throw RuntimeException("HTTP ${response.code}: $body")
                }

                val source = response.body?.source() ?: return@withContext
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: continue
                    if (!line.startsWith("data: ")) continue

                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") break

                    val parsed = parseSseData(data)
                    if (parsed.deltaText != null) {
                        fullText += parsed.deltaText
                        emit(StreamFrame.TextDelta(parsed.deltaText, null))
                    }
                    if (parsed.finishReason != null) {
                        finishReason = parsed.finishReason
                    }
                }
            }
        }

        if (fullText.isNotEmpty()) {
            emit(StreamFrame.TextComplete(fullText, null))
        }
        emit(StreamFrame.End(finishReason, ResponseMetaInfo(clock.now())))
    }.flowOn(Dispatchers.IO)

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
        ModerationResult(false, emptyMap())

    override suspend fun models(): List<LLModel> = emptyList()

    override fun close() {
        (httpClient as? Closeable)?.close()
    }

    private suspend fun executeRequest(prompt: Prompt, model: LLModel, stream: Boolean): JsonObject =
        withContext(Dispatchers.IO) {
            val request = buildRequest(prompt, model, stream)
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw RuntimeException("HTTP ${response.code}: $body")
                }
                json.parseToJsonElement(body).jsonObject
            }
        }

    private fun buildRequest(prompt: Prompt, model: LLModel, stream: Boolean): Request {
        val body = buildRequestBody(prompt, model, stream)
        return Request.Builder()
            .url("${baseUrl.trimEnd('/')}/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .header("content-type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun buildRequestBody(prompt: Prompt, model: LLModel, stream: Boolean): JsonObject =
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
        }

    private fun Message.toAnthropicMessage(): JsonObject =
        buildJsonObject {
            put("role", when (this@toAnthropicMessage) {
                is Message.Assistant -> "assistant"
                else -> "user"
            })
            put("content", parts.toAnthropicContent())
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

    private fun parseSseData(data: String): ParsedSse {
        val event = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull()
            ?: return ParsedSse()
        val type = event["type"]?.jsonPrimitive?.contentOrNull
        if (type == "message_delta") {
            return ParsedSse(
                finishReason = event["delta"]?.jsonObject
                    ?.get("stop_reason")?.jsonPrimitive?.contentOrNull
            )
        }
        if (type != "content_block_delta") return ParsedSse()

        val delta = event["delta"]?.jsonObject ?: return ParsedSse()
        return ParsedSse(deltaText = delta["text"]?.jsonPrimitive?.contentOrNull)
    }

    private fun responseMetaInfo(usage: JsonObject?): ResponseMetaInfo =
        ResponseMetaInfo(
            timestamp = clock.now(),
            inputTokensCount = usage?.get("input_tokens")?.jsonPrimitive?.intOrNull,
            outputTokensCount = usage?.get("output_tokens")?.jsonPrimitive?.intOrNull,
        )

    private data class ParsedSse(
        val deltaText: String? = null,
        val finishReason: String? = null,
    )

    private companion object {
        val json = Json { ignoreUnknownKeys = true }

        fun defaultHttpClient(): OkHttpClient =
            OkHttpClient.Builder()
                .callTimeout(60, TimeUnit.SECONDS)
                .build()
    }
}

class AnthropicCompatLLMClientFactory @Inject constructor() {
    fun create(apiKey: String, baseUrl: String): AnthropicCompatLLMClient =
        AnthropicCompatLLMClient(apiKey = apiKey, baseUrl = baseUrl)
}
