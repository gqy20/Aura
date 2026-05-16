package com.xiaoqi.companion.data.datastore

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppPreferencesTest {

    @Test
    fun apiKey_keyIsCorrect() {
        val key = stringPreferencesKey("api_key")
        assertEquals("api_key", key.name)
    }

    @Test
    fun currentCompanionId_keyIsCorrect() {
        val key = stringPreferencesKey("current_companion_id")
        assertEquals("current_companion_id", key.name)
    }

    @Test
    fun themeMode_keyIsCorrect() {
        val key = stringPreferencesKey("theme_mode")
        assertEquals("theme_mode", key.name)
    }

    @Test
    fun voiceEnabled_keyIsCorrect() {
        val key = stringPreferencesKey("voice_enabled")
        assertEquals("voice_enabled", key.name)
    }

    @Test
    fun notificationEnabled_keyIsCorrect() {
        val key = stringPreferencesKey("notification_enabled")
        assertEquals("notification_enabled", key.name)
    }

    @Test
    fun contextCapabilityKeys_areCorrect() {
        assertEquals("device_status_context_enabled", AppPreferences.Keys.deviceStatusContextEnabled.name)
        assertEquals("location_context_enabled", AppPreferences.Keys.locationContextEnabled.name)
        assertEquals("weather_context_enabled", AppPreferences.Keys.weatherContextEnabled.name)
        assertEquals("reminder_tool_enabled", AppPreferences.Keys.reminderToolEnabled.name)
    }

    @Test
    fun llmProvider_keyIsCorrect() {
        val key = stringPreferencesKey("llm_provider")
        assertEquals("llm_provider", key.name)
    }

    @Test
    fun modelName_keyIsCorrect() {
        val key = stringPreferencesKey("model_name")
        assertEquals("model_name", key.name)
    }

    // --- Enum parsing ---

    @Test
    fun themeMode_defaultIsSystem() {
        assertEquals(com.xiaoqi.companion.data.db.converter.ThemeMode.SYSTEM, AppPreferences.defaultThemeMode)
    }

    @Test
    fun llmProvider_defaultIsGLM() {
        assertEquals(com.xiaoqi.companion.data.db.converter.LlmProvider.GLM, AppPreferences.defaultLlmProvider)
    }
}
