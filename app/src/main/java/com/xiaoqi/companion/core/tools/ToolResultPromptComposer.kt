package com.xiaoqi.companion.core.tools

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object ToolResultPromptComposer {

    fun followupInstruction(hasErrors: Boolean): String =
        if (hasErrors) {
            "One or more tool calls failed. Do not invent missing results. Explain the limitation naturally, " +
                "use any successful tool results that are available, and suggest a practical next step when helpful."
        } else {
            "Use the tool results above to answer the user naturally. If you still need tools, call them. " +
                "Do not repeat raw tool JSON in the final reply."
        }

    fun localToolResultsJson(results: List<LocalToolPromptResult>): String =
        buildJsonObject {
            put(
                "tool_results",
                buildJsonArray {
                    results.forEach { result ->
                        add(
                            buildJsonObject {
                                put("id", result.id)
                                put("name", result.name)
                                put("result", result.result)
                                put("is_error", result.isError)
                            }
                        )
                    }
                }
            )
            put("instruction", followupInstruction(results.any { it.isError }))
        }.toString()

    fun localToolContextBlock(results: List<LocalToolPromptResult>): String = buildString {
        appendLine("Tool results from previous calls:")
        appendLine(localToolResultsJson(results))
        appendLine()
        append(
            "If you still need tools, output JSON in the same tool_calls shape only. " +
                "Otherwise answer the user naturally."
        )
    }
}

data class LocalToolPromptResult(
    val id: String,
    val name: String,
    val result: String,
    val isError: Boolean = false,
)
