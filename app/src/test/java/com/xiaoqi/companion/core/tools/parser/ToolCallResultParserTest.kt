package com.xiaoqi.companion.core.tools.parser

import com.xiaoqi.companion.core.tools.ToolEnvelopeFactory
import com.xiaoqi.companion.core.tools.encode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCallResultParserTest {

    private val parser = ToolCallResultParser()

    @Test
    fun parse_searchMemoryEnvelopeOk_extractsListHitsWithCountAndContents() {
        val env = encode(ToolEnvelopeFactory.ok(buildJsonObject {
            put("count", JsonPrimitive(3))
            put("query", JsonPrimitive("jasmine"))
            put("results", kotlinx.serialization.json.JsonArray(listOf(
                buildJsonObject { put("content", JsonPrimitive("likes jasmine tea")) },
                buildJsonObject { put("content", JsonPrimitive("prefers quiet cafes")) },
                buildJsonObject { put("content", JsonPrimitive("allergic to lilies")) },
            )))
        }))

        val summary = parser.parse("search_memory", env)

        assertTrue(summary is ToolResultSummary.ListHits)
        summary as ToolResultSummary.ListHits
        assertEquals("记忆搜索", summary.title)
        assertEquals(3, summary.count)
        assertEquals(listOf("likes jasmine tea", "prefers quiet cafes", "allergic to lilies"), summary.items)
    }

    @Test
    fun parse_searchMemoryEnvelopeOkZeroCount_returnsEmpty() {
        val env = encode(ToolEnvelopeFactory.ok(buildJsonObject {
            put("count", JsonPrimitive(0))
            put("results", kotlinx.serialization.json.JsonArray(emptyList()))
        }))

        val summary = parser.parse("search_memory", env)

        assertTrue(summary is ToolResultSummary.Empty)
        assertEquals("记忆搜索", (summary as ToolResultSummary.Empty).title)
    }

    @Test
    fun parse_envelopeError_returnsFailedWithReasonAndHint() {
        val env = encode(ToolEnvelopeFactory.disabled(
            reason = "reminder_tool_disabled",
            hint = "用户关闭了提醒功能,可在设置里开启",
        ))

        val summary = parser.parse("create_local_reminder", env)

        assertTrue(summary is ToolResultSummary.Failed)
        summary as ToolResultSummary.Failed
        assertEquals("创建提醒", summary.title)
        assertEquals("reminder_tool_disabled", summary.reason)
        assertEquals("用户关闭了提醒功能,可在设置里开启", summary.hint)
    }

    @Test
    fun parse_saveMemoryEnvelopeOk_returnsSavedOneWithSubject() {
        val env = encode(ToolEnvelopeFactory.ok(buildJsonObject {
            put("memoryId", JsonPrimitive("mem-42"))
            put("type", JsonPrimitive("FACT"))
        }))

        val summary = parser.parse("save_memory", env)

        assertTrue(summary is ToolResultSummary.SavedOne)
        summary as ToolResultSummary.SavedOne
        assertEquals("保存记忆", summary.title)
        assertEquals("记忆 mem-42", summary.subject)
    }

    @Test
    fun parse_createReminderEnvelopeOk_returnsScheduledWithExact() {
        val env = encode(ToolEnvelopeFactory.ok(buildJsonObject {
            put("title", JsonPrimitive("Stand up"))
            put("triggerAtMillis", JsonPrimitive(601_000L))
            put("exact", JsonPrimitive(true))
        }))

        val summary = parser.parse("create_local_reminder", env)

        assertTrue(summary is ToolResultSummary.Scheduled)
        summary as ToolResultSummary.Scheduled
        assertEquals("Stand up", summary.subject)
        assertEquals(601_000L, summary.triggerAtMillis)
        assertEquals(true, summary.exact)
    }

    @Test
    fun parse_createReminderLegacyScheduled_returnsScheduled() {
        // P0 修复:CreateLocalReminderTool 真实路径不返回 envelope,
        // 直接 buildJsonObject 输出 {status:scheduled, title, triggerAtEpochMillis, ...}
        // parseLegacy 路径必须识别这种格式,否则 chip 文案会丢失 subject。
        val raw = """{"status":"scheduled","reminderId":"r-1","title":"吃药","triggerAtEpochMillis":1234,"delayMillis":600000,"exact":true}"""

        val summary = parser.parse("create_local_reminder", raw)

        assertTrue(summary is ToolResultSummary.Scheduled)
        summary as ToolResultSummary.Scheduled
        assertEquals("吃药", summary.subject)
        assertEquals(true, summary.exact)
    }

    @Test
    fun parse_deviceStatusEnvelopeOk_returnsKeyValueReport() {
        val env = encode(ToolEnvelopeFactory.ok(buildJsonObject {
            put("batteryPercent", JsonPrimitive(42))
            put("isCharging", JsonPrimitive(true))
            put("networkType", JsonPrimitive("wifi"))
            put("isOnline", JsonPrimitive(true))
        }))

        val summary = parser.parse("get_device_status", env)

        assertTrue(summary is ToolResultSummary.KeyValueReport)
        val pairs = (summary as ToolResultSummary.KeyValueReport).pairs
        assertEquals(4, pairs.size)
        assertEquals("电量" to "42%", pairs[0])
        assertEquals("充电中" to "是", pairs[1])
        assertEquals("网络" to "wifi", pairs[2])
        assertEquals("在线" to "是", pairs[3])
    }

    @Test
    fun parse_weatherEnvelopeOk_returnsKeyValueReport() {
        val env = encode(ToolEnvelopeFactory.ok(buildJsonObject {
            put("locationName", JsonPrimitive("Shanghai"))
            put("temperatureCelsius", JsonPrimitive(25.5))
            put("weatherLabel", JsonPrimitive("晴"))
            put("humidityPercent", JsonPrimitive(68))
        }))

        val summary = parser.parse("get_weather", env)

        assertTrue(summary is ToolResultSummary.KeyValueReport)
        val pairs = (summary as ToolResultSummary.KeyValueReport).pairs
        assertEquals("地点" to "Shanghai", pairs[0])
        assertEquals("气温" to "25.5°C", pairs[1])
        assertEquals("天气" to "晴", pairs[2])
        assertEquals("湿度" to "68%", pairs[3])
    }

    @Test
    fun parse_legacySearchRecordsRaw_returnsListHitsFromLegacyShape() {
        // legacy: tool 直接返 {count, results: [{content, ...}]},无 envelope,无 role 字段
        val raw = """{"count":2,"query":"x","sessionId":"default","results":[{"id":"a","role":"USER","content":"hello","timestamp":1,"hasImage":false,"score":1.0,"contextBefore":[],"contextAfter":[],"source":"recent"},{"id":"b","role":"ASSISTANT","content":"hi","timestamp":2,"hasImage":false,"score":1.0,"contextBefore":[],"contextAfter":[],"source":"recent"}]}"""

        val summary = parser.parse("any_tool", raw)

        // parseLegacy 是兜底:不解析 role,只取 content/title
        assertTrue(summary is ToolResultSummary.ListHits)
        summary as ToolResultSummary.ListHits
        assertEquals(2, summary.count)
        assertEquals(listOf("hello", "hi"), summary.items)
    }

    @Test
    fun parse_unknownToolName_returnsUnknownWithRawString() {
        val env = encode(ToolEnvelopeFactory.ok(buildJsonObject {
            put("foo", JsonPrimitive("bar"))
        }))

        val summary = parser.parse("mystery_tool", env)

        assertTrue(summary is ToolResultSummary.Unknown)
        assertTrue((summary as ToolResultSummary.Unknown).raw.contains("bar"))
    }

    @Test
    fun parse_blankOrNullJson_returnsUnknown() {
        assertTrue(parser.parse("any", null) is ToolResultSummary.Unknown)
        assertTrue(parser.parse("any", "") is ToolResultSummary.Unknown)
        assertTrue(parser.parse("any", "   ") is ToolResultSummary.Unknown)
    }

    @Test
    fun parse_malformedJson_fallsThroughToUnknown() {
        // 既不是 envelope 也不是 legacy JSON —— 兜底 unknown
        val summary = parser.parse("any", "not even json {{{{")

        assertTrue(summary is ToolResultSummary.Unknown)
    }

    @Test
    fun parse_searchSummariesEnvelopeOk_extractsTitles() {
        val env = encode(ToolEnvelopeFactory.ok(buildJsonObject {
            put("count", JsonPrimitive(2))
            put("query", JsonPrimitive(""))
            put("results", kotlinx.serialization.json.JsonArray(listOf(
                buildJsonObject { put("title", JsonPrimitive("Last week themes")) },
                buildJsonObject { put("title", JsonPrimitive("Quiet month")) },
            )))
        }))

        val summary = parser.parse("search_summaries", env)

        assertTrue(summary is ToolResultSummary.ListHits)
        summary as ToolResultSummary.ListHits
        assertEquals(2, summary.count)
        assertEquals(listOf("Last week themes", "Quiet month"), summary.items)
    }
}
