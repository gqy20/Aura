package com.xiaoqi.companion.data.db.converter

import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {

    // --- Enum round-trip ---

    @Test
    fun messageRole_roundTrip() {
        for (role in MessageRole.entries) {
            val str = Converters.messageRoleToString(role)
            assertEquals(role, Converters.stringToMessageRole(str))
        }
    }

    @Test
    fun memoryType_roundTrip() {
        for (type in MemoryType.entries) {
            val str = Converters.memoryTypeToString(type)
            assertEquals(type, Converters.stringToMemoryType(str))
        }
    }

    @Test
    fun themeMode_roundTrip() {
        for (mode in ThemeMode.entries) {
            val str = Converters.themeModeToString(mode)
            assertEquals(mode, Converters.stringToThemeMode(str))
        }
    }

    @Test
    fun llmProvider_roundTrip() {
        for (provider in LlmProvider.entries) {
            val str = Converters.llmProviderToString(provider)
            assertEquals(provider, Converters.stringToLlmProvider(str))
        }
    }

    // --- JSON round-trip ---

    @Test
    fun emotionVectorJson_roundTrip() {
        val map = mapOf("joy" to 0.8f, "sadness" to 0.2f, "anger" to 0.1f)
        val json = Converters.mapToJson(map)
        val restored = Converters.jsonToMap(json)
        assertEquals(3, restored.size)
        assertEquals(0.8f, restored["joy"]!!, 0.001f)
        assertEquals(0.2f, restored["sadness"]!!, 0.001f)
        assertEquals(0.1f, restored["anger"]!!, 0.001f)
    }

    @Test
    fun metadataJson_roundTrip() {
        val meta = MessageMetadata(model = "glm-5v-turbo", tokensUsed = 42)
        val json = Converters.metadataToJson(meta)
        val restored = Converters.jsonToMetadata(json)
        assertEquals(meta.model, restored.model)
        assertEquals(meta.tokensUsed, restored.tokensUsed)
    }
}
