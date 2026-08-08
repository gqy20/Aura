package com.xiaoqi.companion.core.tools

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolResultJsonNormalizerTest {
    @Test
    fun unwrapsJsonStoredAsStringLiteral() {
        val raw = "\"{\\\"results\\\":[{\\\"location\\\":\\\"120.1,30.2\\\"}]}\""

        assertEquals(
            """{"results":[{"location":"120.1,30.2"}]}""",
            normalizeToolResultJson(raw),
        )
    }

    @Test
    fun preservesRawJsonAndPlainStrings() {
        val json = """{"status":"ok","data":{}}"""

        assertEquals(json, normalizeToolResultJson(json))
        assertEquals("plain text", normalizeToolResultJson("plain text"))
    }

    @Test
    fun unwrapsKoogLooseStringRepresentation() {
        val raw = "\"{\"epochMillis\":123,\"timezone\":\"Asia/Shanghai\"}\""

        assertEquals(
            """{"epochMillis":123,"timezone":"Asia/Shanghai"}""",
            normalizeToolResultJson(raw),
        )
    }
}
