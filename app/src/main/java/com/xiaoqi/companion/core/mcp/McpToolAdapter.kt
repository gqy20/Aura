package com.xiaoqi.companion.core.mcp

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.serialization.typeToken
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class McpRemoteTool(
    serverUrl: String,
    serverName: String,
    spec: McpToolSpec,
    private val client: RemoteMcpClient,
    private val headers: Map<String, String> = emptyMap(),
) : Tool<JsonObject, String>(
    typeToken<JsonObject>(),
    typeToken<String>(),
    descriptor = spec.toToolDescriptor(toolName = spec.toKoogToolName(serverName, serverUrl)),
) {
    private val remoteName = spec.name
    private val remoteServerUrl = serverUrl

    override suspend fun execute(args: JsonObject): String {
        val normalizedArgs = args.quoteInvalidBareLiterals().jsonObject
        return withMcpToolRetry(
            serverUrl = remoteServerUrl,
            toolName = remoteName,
        ) {
            client.callTool(
                serverUrl = remoteServerUrl,
                toolName = remoteName,
                arguments = normalizedArgs,
                headers = headers,
            )
        }
    }
}

private fun JsonElement.quoteInvalidBareLiterals(): JsonElement = when (this) {
    is JsonObject -> JsonObject(mapValues { (_, value) -> value.quoteInvalidBareLiterals() })
    is JsonArray -> JsonArray(map(JsonElement::quoteInvalidBareLiterals))
    is JsonPrimitive -> {
        val isValidLiteral = isString || content == "true" || content == "false" ||
            content == "null" || content.toDoubleOrNull() != null
        if (isValidLiteral) this else JsonPrimitive(content)
    }
}

fun McpToolSpec.toKoogToolName(serverName: String, serverUrl: String): String {
    val serverSlug = serverName.ifBlank { serverUrl.hostSlug() }.sanitizeToolName()
    return "mcp__${serverSlug}__${name.sanitizeToolName()}".take(MAX_TOOL_NAME_LENGTH)
}

private fun McpToolSpec.toToolDescriptor(toolName: String): ToolDescriptor {
    val properties = inputSchema["properties"]?.jsonObject.orEmpty()
    val requiredNames = inputSchema["required"]
        ?.jsonArray
        ?.mapNotNull { it.jsonPrimitive.contentOrNull }
        ?.toSet()
        .orEmpty()
    val descriptors = properties.map { (name, schema) ->
        ToolParameterDescriptor(
            name = name,
            description = schema.jsonObject["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            type = schema.jsonObject.toParameterType(),
        )
    }
    return ToolDescriptor(
        name = toolName,
        description = description.ifBlank { "Remote MCP tool: $name" },
        requiredParameters = descriptors.filter { it.name in requiredNames },
        optionalParameters = descriptors.filterNot { it.name in requiredNames },
    )
}

private fun JsonObject.toParameterType(): ToolParameterType {
    val enumEntries = this["enum"] as? JsonArray
    if (enumEntries != null) {
        return ToolParameterType.Enum(
            enumEntries.mapNotNull { it.jsonPrimitive.contentOrNull }.toTypedArray()
        )
    }

    return when (this["type"]?.jsonPrimitive?.contentOrNull) {
        "integer" -> ToolParameterType.Integer
        "number" -> ToolParameterType.Float
        "boolean" -> ToolParameterType.Boolean
        "array" -> ToolParameterType.List(
            this["items"]?.jsonObject?.toParameterType() ?: ToolParameterType.String
        )
        "object" -> toObjectParameterType()
        else -> ToolParameterType.String
    }
}

private fun JsonObject.toObjectParameterType(): ToolParameterType.Object {
    val propertiesObject = this["properties"]?.jsonObject.orEmpty()
    val requiredNames = this["required"]
        ?.jsonArray
        ?.mapNotNull { it.jsonPrimitive.contentOrNull }
        .orEmpty()
    return ToolParameterType.Object(
        properties = propertiesObject.map { (name, schema) ->
            ToolParameterDescriptor(
                name = name,
                description = schema.jsonObject["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                type = schema.jsonObject.toParameterType(),
            )
        },
        requiredProperties = requiredNames,
        additionalProperties = (this["additionalProperties"] as? JsonObject)?.let { true } ?: true,
        additionalPropertiesType = (this["additionalProperties"] as? JsonObject)?.toParameterType()
            ?: ToolParameterType.String,
    )
}

private fun String.hostSlug(): String =
    substringAfter("://", this)
        .substringBefore("/")
        .substringBefore(":")
        .ifBlank { "remote" }
        .sanitizeToolName()

private fun String.sanitizeToolName(): String {
    val sanitized = lowercase()
        .map { char ->
            when {
                char in 'a'..'z' || char in '0'..'9' || char == '_' || char == '-' -> char
                else -> '_'
            }
        }
        .joinToString("")
        .trim('_', '-')
    return sanitized.ifBlank { "tool" }
}

private const val MAX_TOOL_NAME_LENGTH = 96
