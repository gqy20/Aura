package com.xiaoqi.companion.core.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogFieldSanitizerTest {

    @Test
    fun `redacts known sensitive keys`() {
        val sensitive = mapOf(
            "apiKey" to "sk-1234567890",
            "api_key" to "sk-9876",
            "Authorization" to "Bearer abc",
            "token" to "tok-xyz",
            "secret" to "shh",
            "prompt" to "用户在说什么",
            "content" to "hello world",
            "message" to "hi",
            "text" to "raw",
            "url" to "https://api.example.com",
            "base64" to "iVBOR...",
            "body" to "{...}",
            "image" to "data:image/png;base64,...",
            "input" to "用户输入",
            "response" to "模型输出",
        )
        val sanitized = LogFieldSanitizer.sanitize(sensitive)

        sanitized.values.forEach { value ->
            val s = value.toString()
            assertTrue("expected redaction marker, got $value", s.startsWith("redacted:"))
            assertTrue("must not leak raw value: $s", !s.contains("sk-") && !s.contains("Bearer"))
        }
    }

    @Test
    fun `safe metric keys preserve raw value even when key contains sensitive substring`() {
        // contentLength 含 "content" 但属于 safe metric → 必须保留原值
        val safe = mapOf(
            "contentLength" to 128,
            "messageCount" to 5,
            "responseLength" to 256,
            "inputTokens" to 100,
            "outputTokens" to 200,
            "statusCode" to 200,
            "durationMs" to 42,
            "duration" to 100L,
            "requestHash" to "abc123",
            "hasImage" to true,
            "content_hash" to "deadbeef",
        )
        val sanitized = LogFieldSanitizer.sanitize(safe)

        assertEquals(128, sanitized["contentLength"])
        assertEquals(5, sanitized["messageCount"])
        assertEquals(256, sanitized["responseLength"])
        assertEquals(100, sanitized["inputTokens"])
        assertEquals(200, sanitized["outputTokens"])
        assertEquals(200, sanitized["statusCode"])
        assertEquals(42, sanitized["durationMs"])
        assertEquals(100L, sanitized["duration"])
        assertEquals("abc123", sanitized["requestHash"])
        assertEquals(true, sanitized["hasImage"])
        assertEquals("deadbeef", sanitized["content_hash"])
    }

    @Test
    fun `sensitive string reports length not content`() {
        val sanitized = LogFieldSanitizer.sanitize(mapOf("prompt" to "abcdefgh"))
        assertEquals("redacted:length=8", sanitized["prompt"])
    }

    @Test
    fun `sensitive collection reports size not contents`() {
        val sanitized = LogFieldSanitizer.sanitize(mapOf("messages" to listOf("a", "b", "c")))
        assertEquals("redacted:size=3", sanitized["messages"])
    }

    @Test
    fun `sensitive map reports size not contents`() {
        val sanitized = LogFieldSanitizer.sanitize(
            mapOf("body" to mapOf("k1" to "v1", "k2" to "v2"))
        )
        assertEquals("redacted:size=2", sanitized["body"])
    }

    @Test
    fun `null sensitive value is explicit null marker`() {
        val sanitized = LogFieldSanitizer.sanitize(mapOf("prompt" to null))
        assertEquals("redacted:null", sanitized["prompt"])
    }

    @Test
    fun `null safe value stays null`() {
        val sanitized = LogFieldSanitizer.sanitize(mapOf("contentLength" to null))
        assertNull(sanitized["contentLength"])
    }

    @Test
    fun `string value truncates beyond max length`() {
        val long = "x".repeat(500)
        val sanitized = LogFieldSanitizer.sanitize(mapOf("sessionId" to long))
        assertEquals(120, (sanitized["sessionId"] as String).length)
    }

    @Test
    fun `hash is stable and truncated to 12 chars`() {
        val h1 = LogFieldSanitizer.hash("session-abc")
        val h2 = LogFieldSanitizer.hash("session-abc")
        assertEquals(h1, h2)
        assertEquals(12, h1.length)
    }

    @Test
    fun `hash differs for different inputs`() {
        val a = LogFieldSanitizer.hash("user-1")
        val b = LogFieldSanitizer.hash("user-2")
        assertTrue("different inputs must hash differently", a != b)
    }

    @Test
    fun `hash of blank or null is sentinel`() {
        assertEquals("none", LogFieldSanitizer.hash(null))
        assertEquals("none", LogFieldSanitizer.hash(""))
        assertEquals("none", LogFieldSanitizer.hash("   "))
    }

    @Test
    fun `nested map values are recursively sanitized by their own keys`() {
        val nested = mapOf(
            "metadata" to mapOf(
                "token" to "leak-me",
                "contentLength" to 99,
            )
        )
        val sanitized = LogFieldSanitizer.sanitize(nested)
        val inner = sanitized["metadata"] as Map<*, *>

        assertEquals("redacted:length=7", inner["token"])
        // safe metric 在嵌套层级也必须生效
        assertEquals(99, inner["contentLength"])
    }

    @Test
    fun `iterable values sanitize each element`() {
        val sanitized = LogFieldSanitizer.sanitize(
            mapOf("durations" to listOf(1, 2, 3))
        )
        assertEquals(listOf(1, 2, 3), sanitized["durations"])
    }

    @Test
    fun `iterable beyond max collection size is truncated`() {
        val big = (1..50).toList()
        val sanitized = LogFieldSanitizer.sanitize(mapOf("items" to big))
        assertEquals(12, (sanitized["items"] as List<*>).size)
    }

    @Test
    fun `throwable value reduces to its simple class name`() {
        val sanitized = LogFieldSanitizer.sanitize(
            mapOf("cause" to IllegalStateException("boom"))
        )
        assertEquals("IllegalStateException", sanitized["cause"])
    }

    @Test
    fun `enum value uses its name`() {
        val sanitized = LogFieldSanitizer.sanitize(
            mapOf("level" to LogEventType.Audit)
        )
        assertEquals("Audit", sanitized["level"])
    }

    @Test
    fun `case insensitive key matching`() {
        val sanitized = LogFieldSanitizer.sanitize(
            mapOf("PROMPT" to "x", "ApiKey" to "y", "TOKEN" to "z")
        )
        assertEquals("redacted:length=1", sanitized["PROMPT"])
        assertEquals("redacted:length=1", sanitized["ApiKey"])
        assertEquals("redacted:length=1", sanitized["TOKEN"])
    }
}
