package com.xiaoqi.companion.core.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolEnvelopeTest {

    // --- encode ---

    @Test
    fun encode_ok_envelope_emitsStatusAndData() {
        val env = ToolEnvelope.Ok(data = buildJsonObject {
            put("count", JsonPrimitive(3))
            put("query", JsonPrimitive("jasmine"))
        })

        val raw = encode(env)

        assertTrue("envelope should start with status:ok but was: $raw", raw.contains("\"status\":\"ok\""))
        assertTrue(raw.contains("\"count\":3"))
        assertTrue(raw.contains("\"query\":\"jasmine\""))
    }

    @Test
    fun encode_error_envelope_emitsStatusReasonHintAndDetails() {
        val env = ToolEnvelopeFactory.invalidMemoryType("XYZX")

        val raw = encode(env)

        assertTrue(raw.contains("\"status\":\"error\""))
        assertTrue(raw.contains("\"reason\":\"invalid_memory_type\""))
        assertTrue(raw.contains("\"hint\""))
        assertTrue(raw.contains("\"provided\":\"XYZX\""))
    }

    // --- parseOrNull round-trip ---

    @Test
    fun parseOrNull_ok_envelopeRoundTrips() {
        val env = ToolEnvelope.Ok(data = buildJsonObject { put("k", JsonPrimitive("v")) })
        val parsed = parseOrNull(encode(env))

        assertNotNull(parsed)
        assertTrue(parsed is ToolEnvelope.Ok)
        assertEquals("v", (parsed as ToolEnvelope.Ok).data["k"]?.jsonPrimitive?.content)
    }

    @Test
    fun parseOrNull_error_envelopeRoundTrips() {
        val env = ToolEnvelopeFactory.permissionMissing("POST_NOTIFICATIONS", "需要通知权限")
        val parsed = parseOrNull(encode(env))

        assertNotNull(parsed)
        assertTrue(parsed is ToolEnvelope.Error)
        parsed as ToolEnvelope.Error
        assertEquals("permission_missing", parsed.reason)
        assertEquals("POST_NOTIFICATIONS", parsed.details["permission"])
    }

    @Test
    fun parseOrNull_malformedJson_returnsNullWithoutThrowing() {
        assertNull(parseOrNull("{not json"))
        assertNull(parseOrNull(""))
        assertNull(parseOrNull(null))
        // status 字段值是未知类型 → 解析失败 → null
        assertNull(parseOrNull("""{"status":"weird","reason":"x"}"""))
    }

    @Test
    fun parseOrNull_acceptsDoubleEncodedEnvelopeAndIgnoresLegacyJson() {
        val encoded = encode(ToolEnvelope.Ok(data = buildJsonObject { put("k", JsonPrimitive("v")) }))
        val doubleEncoded = JsonPrimitive(encoded).toString()

        assertTrue(parseOrNull(doubleEncoded) is ToolEnvelope.Ok)
        assertNull(parseOrNull("""{"epochMillis":123,"timezone":"Asia/Shanghai"}"""))
    }

    // --- isError quick check ---

    @Test
    fun isError_detectsEnvelopeErrorAndIgnoresOk() {
        assertTrue(isError(encode(ToolEnvelopeFactory.disabled("x_tool_disabled", "x 不可用"))))
        assertFalse(isError(encode(ToolEnvelope.Ok(data = buildJsonObject { put("k", JsonPrimitive("v")) }))))
        assertFalse(isError(null))
        assertFalse(isError(""))
        assertFalse(isError("plain text"))
    }

    // --- extractors ---

    @Test
    fun parseErrorReason_andHint_extractFromError() {
        val raw = encode(ToolEnvelopeFactory.ftsFailure("no such column: rowid"))

        assertEquals("fts_index_failure", parseErrorReason(raw))
        assertTrue("hint should mention '全文检索' but was: ${parseErrorHint(raw)}",
            parseErrorHint(raw)?.contains("全文检索") == true)
    }

    @Test
    fun parseErrorReason_returnsNullForOk() {
        val raw = encode(ToolEnvelope.Ok(data = buildJsonObject { put("k", JsonPrimitive("v")) }))
        assertNull(parseErrorReason(raw))
        assertNull(parseErrorHint(raw))
    }

    // --- factory convenience ---

    @Test
    fun factory_invalidEnum_includesAllowedListInHint() {
        val env = ToolEnvelopeFactory.invalidSummaryType("WEEKLY")
        assertTrue("hint should list DAILY/SESSION/TOPIC/PROJECT/RELATIONSHIP but was: ${env.hint}",
            env.hint.contains("DAILY") && env.hint.contains("RELATIONSHIP"))
        assertEquals("invalid_summary_type", env.reason)
    }

    @Test
    fun factory_disabled_setsReasonAndEmptyDetails() {
        val env = ToolEnvelopeFactory.disabled(
            reason = "reminder_tool_disabled",
            hint = "用户在设置中关闭了提醒功能",
        )
        assertEquals("reminder_tool_disabled", env.reason)
        assertTrue(env.details.isEmpty())
    }

    @Test
    fun factory_permissionMissing_usesStandardReason() {
        val env = ToolEnvelopeFactory.permissionMissing(
            permission = "EXACT_ALARM",
            hint = "需要精准闹钟权限才能准时提醒",
        )
        assertEquals("permission_missing", env.reason)
        assertEquals("EXACT_ALARM", env.details["permission"])
    }

    // --- jsonObjectOf helper ---

    @Test
    fun jsonObjectOf_buildsFlatObject() {
        val obj: JsonObject = jsonObjectOf(
            "count" to JsonPrimitive(3),
            "query" to JsonPrimitive("jasmine"),
            "ok" to JsonPrimitive(true),
        )
        assertEquals(JsonPrimitive(3), obj["count"])
        assertEquals(JsonPrimitive("jasmine"), obj["query"])
        assertEquals(JsonPrimitive(true), obj["ok"])
    }
}
