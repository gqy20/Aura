package com.xiaoqi.companion.core.mcp

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.serialization.typeToken
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class McpRemoteTool(
    serverUrl: String,
    spec: McpToolSpec,
    private val client: RemoteMcpClient,
) : Tool<JsonObject, String>(
    typeToken<JsonObject>(),
    typeToken<String>(),
    descriptor = spec.toToolDescriptor(toolName = spec.toKoogToolName(serverUrl)),
) {
    private val remoteName = spec.name
    private val remoteServerUrl = serverUrl

    override suspend fun execute(args: JsonObject): String =
        client.callTool(
            serverUrl = remoteServerUrl,
            toolName = remoteName,
            arguments = args,
        )
}

fun McpToolSpec.toKoogToolName(serverUrl: String): String =
    "mcp__${serverUrl.hostSlug()}__${name.sanitizeToolName()}".take(MAX_TOOL_NAME_LENGTH)

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
            (this["items"] as? JsonObject)?.toParameterType() ?: ToolParameterType.String
        )
        "object" -> ToolParameterType.Object(
            properties = emptyList(),
            requiredProperties = emptyList(),
            additionalProperties = true,
            additionalPropertiesType = ToolParameterType.String,
        )
        else -> ToolParameterType.String
    }
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
