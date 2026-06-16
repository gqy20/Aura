package com.xiaoqi.companion.core.tools.parser

import com.xiaoqi.companion.core.tools.ToolEnvelope
import com.xiaoqi.companion.core.tools.isError
import com.xiaoqi.companion.core.tools.parseErrorHint
import com.xiaoqi.companion.core.tools.parseErrorReason
import com.xiaoqi.companion.core.tools.parseOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * 把 tool 的 raw result JSON + tool 名 → [ToolResultSummary]。
 *
 * **三段式分发**:
 * 1. **envelope 错误**(`isError(resultJson)` true):走 [ToolResultSummary.Failed]
 * 2. **envelope 成功**(`parseOrNull(resultJson)` is `Ok`):从 `data` 字段里读 `count/results/...`,
 *    再按 tool 名分流(search_* → ListHits / save_memory → SavedOne / 等等)
 * 3. **legacy/raw 字符串**(无 status 字段):整段当 [ToolResultSummary.Unknown] 兜底
 *
 * **纯函数**:`toolName` 决定渲染文案,`resultJson` 决定数据。所有副作用(数据库/网络)都不进 parser。
 */
@Singleton
class ToolCallResultParser @Inject constructor() {

    fun parse(toolName: String, resultJson: String?): ToolResultSummary {
        if (resultJson.isNullOrBlank()) return ToolResultSummary.Unknown(raw = "")

        // 1. envelope error
        if (isError(resultJson)) {
            return ToolResultSummary.Failed(
                title = toolTitle(toolName),
                reason = parseErrorReason(resultJson) ?: "tool_error",
                hint = parseErrorHint(resultJson),
            )
        }

        // 2. envelope ok:读 data 字段
        val env = parseOrNull(resultJson)
        if (env is ToolEnvelope.Ok) {
            return parseEnvelopeOk(toolName, env.data)
        }

        // 3. legacy raw JSON:可能是 search_records 的 {count, results} 等旧结构
        //    或 create_local_reminder 的 {status:scheduled, title, ...} 裸格式
        //    (CreateLocalReminderTool.kt:122 不走 envelope,直接 buildJsonObject)
        return parseLegacy(toolName, resultJson)
            ?: ToolResultSummary.Unknown(raw = resultJson)
    }

    private fun parseEnvelopeOk(toolName: String, data: JsonObject): ToolResultSummary {
        return when (toolName) {
            "search_memory" -> parseSearchMemory(data)
            "search_records" -> parseSearchRecords(data)
            "search_summaries" -> parseSearchSummaries(data)
            "save_memory" -> parseSaveMemory(data)
            "create_local_reminder" -> parseCreateReminder(data)
            "get_current_time" -> parseGetCurrentTime(data)
            "get_recent_interaction_context" -> parseRecentContext(data)
            "get_device_status" -> parseDeviceStatus(data)
            "get_weather" -> parseWeather(data)
            "get_user_context_settings" -> parseUserContextSettings(data)
            "update_mood" -> parseMood(data)
            "update_relationship" -> parseRelationship(data)
            "query_health_data" -> parseHealthData(data)
            else -> ToolResultSummary.Unknown(raw = data.toString())
        }
    }

    private fun parseSearchMemory(data: JsonObject): ToolResultSummary {
        val count = data.int("count") ?: 0
        if (count == 0) return ToolResultSummary.Empty(title = "记忆搜索")
        val items = data.arrayAt("results")
            .mapNotNull { it.asObject().str("content")?.takeIf { c -> c.isNotBlank() } }
        return ToolResultSummary.ListHits(title = "记忆搜索", count = count, items = items)
    }

    private fun parseSearchRecords(data: JsonObject): ToolResultSummary {
        val count = data.int("count") ?: 0
        if (count == 0) return ToolResultSummary.Empty(title = "对话记录搜索")
        val items = data.arrayAt("results")
            .mapNotNull { hit ->
                val obj = hit.asObject()
                val role = obj.str("role") ?: return@mapNotNull null
                val content = obj.str("content") ?: return@mapNotNull null
                "$role: $content"
            }
        return ToolResultSummary.ListHits(title = "对话记录搜索", count = count, items = items)
    }

    private fun parseSearchSummaries(data: JsonObject): ToolResultSummary {
        val count = data.int("count") ?: 0
        if (count == 0) return ToolResultSummary.Empty(title = "总结搜索")
        val items = data.arrayAt("results")
            .mapNotNull { it.asObject().str("title") }
        return ToolResultSummary.ListHits(title = "总结搜索", count = count, items = items)
    }

    private fun parseSaveMemory(data: JsonObject): ToolResultSummary {
        val subject = data.str("memoryId")?.let { "记忆 $it" }
            ?: data.str("type")
            ?: "记忆"
        return ToolResultSummary.SavedOne(title = "保存记忆", subject = subject)
    }

    private fun parseCreateReminder(data: JsonObject): ToolResultSummary {
        val subject = data.str("title") ?: "提醒"
        val triggerAt = data.long("triggerAtMillis")
        val exact = data.bool("exact") ?: false
        return ToolResultSummary.Scheduled(
            title = "创建提醒",
            subject = subject,
            triggerAtMillis = triggerAt,
            exact = exact,
        )
    }

    private fun parseGetCurrentTime(data: JsonObject): ToolResultSummary {
        val pairs = listOfNotNull(
            data.str("nowIso")?.let { "ISO 时间" to it },
            data.str("timezone")?.let { "时区" to it },
            data.str("epochMillis")?.let { "时间戳" to it },
        )
        return ToolResultSummary.KeyValueReport(title = "当前时间", pairs = pairs)
    }

    private fun parseRecentContext(data: JsonObject): ToolResultSummary {
        val count = data.int("count") ?: 0
        if (count == 0) return ToolResultSummary.Empty(title = "最近互动")
        val items = data.arrayAt("messages")
            .mapNotNull { it.asObject().str("content") }
        return ToolResultSummary.ListHits(title = "最近互动", count = count, items = items)
    }

    private fun parseDeviceStatus(data: JsonObject): ToolResultSummary {
        val pairs = listOfNotNull(
            data.int("batteryPercent")?.let { "电量" to "$it%" },
            data.bool("isCharging")?.let { "充电中" to if (it) "是" else "否" },
            data.str("networkType")?.let { "网络" to it },
            data.bool("isOnline")?.let { "在线" to if (it) "是" else "否" },
        )
        return ToolResultSummary.KeyValueReport(title = "设备状态", pairs = pairs)
    }

    private fun parseWeather(data: JsonObject): ToolResultSummary {
        val pairs = listOfNotNull(
            data.str("locationName")?.let { "地点" to it },
            data.str("temperatureCelsius")?.let { "气温" to "${it}°C" },
            data.str("weatherLabel")?.let { "天气" to it },
            data.str("humidityPercent")?.let { "湿度" to "$it%" },
        )
        return ToolResultSummary.KeyValueReport(title = "天气", pairs = pairs)
    }

    private fun parseUserContextSettings(data: JsonObject): ToolResultSummary {
        val pairs = listOfNotNull(
            data.bool("weatherEnabled")?.let { "天气上下文" to if (it) "开" else "关" },
            data.bool("locationEnabled")?.let { "位置上下文" to if (it) "开" else "关" },
            data.bool("reminderEnabled")?.let { "提醒" to if (it) "开" else "关" },
            data.bool("notificationEnabled")?.let { "通知" to if (it) "开" else "关" },
        )
        return ToolResultSummary.KeyValueReport(title = "上下文设置", pairs = pairs)
    }

    private fun parseMood(data: JsonObject): ToolResultSummary {
        val mood = data.str("mood") ?: "情绪"
        return ToolResultSummary.SavedOne(title = "更新情绪", subject = mood)
    }

    private fun parseRelationship(data: JsonObject): ToolResultSummary {
        val subject = data.str("relationshipLabel") ?: "关系"
        return ToolResultSummary.SavedOne(title = "更新关系", subject = subject)
    }

    private fun parseHealthData(data: JsonObject): ToolResultSummary {
        val count = data.int("count") ?: 0
        if (count == 0) return ToolResultSummary.Empty(title = "健康数据")
        val items = data.arrayAt("samples")
            .mapNotNull { it.asObject().str("label") }
        return ToolResultSummary.ListHits(title = "健康数据", count = count, items = items)
    }

    /** legacy 兜底:tool 返回纯 JSON 但不是 envelope。 */
    private fun parseLegacy(toolName: String, raw: String): ToolResultSummary? {
        return runCatching {
            val element = kotlinx.serialization.json.Json.parseToJsonElement(raw)
            val obj = element as? JsonObject ?: return@runCatching null

            // create_local_reminder 真实路径不返回 envelope,直接 buildJsonObject:
            //   {status:scheduled, reminderId, title, triggerAtEpochMillis, ...}
            // 走 envelope 路径会被 parseOrNull 当成 legacy JSON 而漏掉,这里显式分发。
            if (toolName == "create_local_reminder" && obj.str("status") == "scheduled") {
                return@runCatching parseCreateReminder(obj)
            }

            // 通用 search_* 工具的 {count, results} 旧结构
            val count = obj.int("count")
            if (count != null) {
                val items = obj.arrayAt("results")
                    .mapNotNull { it.asObject().str("content") ?: it.asObject().str("title") }
                ToolResultSummary.ListHits(title = toolTitle(toolName), count = count, items = items)
            } else {
                null
            }
        }.getOrNull()
    }

    private fun toolTitle(toolName: String): String = when (toolName) {
        "search_memory" -> "记忆搜索"
        "search_records" -> "对话记录搜索"
        "search_summaries" -> "总结搜索"
        "save_memory" -> "保存记忆"
        "create_local_reminder" -> "创建提醒"
        "get_current_time" -> "当前时间"
        "get_recent_interaction_context" -> "最近互动"
        "get_device_status" -> "设备状态"
        "get_weather" -> "天气"
        "get_user_context_settings" -> "上下文设置"
        "update_mood" -> "更新情绪"
        "update_relationship" -> "更新关系"
        "query_health_data" -> "健康数据"
        else -> toolName
    }

    // ---- JsonObject 工具 ----

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

    private fun JsonObject.bool(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.let { it.contentOrNull?.toBooleanStrictOrNull() }

    private fun JsonObject.arrayAt(key: String): List<JsonElement> {
        return (this[key] as? JsonArray)?.toList().orEmpty()
    }

    private fun JsonElement.asObject(): JsonObject =
        (this as? JsonObject) ?: JsonObject(emptyMap())
}
