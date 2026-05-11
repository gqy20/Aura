package com.xiaoqi.companion.data.db.converter

import androidx.room.TypeConverter
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// --- Enums ---

enum class MessageRole { USER, ASSISTANT, SYSTEM }
enum class MemoryType { FACT, EPISODE, PROCEDURAL }
enum class ThemeMode { LIGHT, DARK, SYSTEM }
enum class LlmProvider { GLM, KIMI }

// --- Metadata model ---

@Serializable
data class MessageMetadata(
    val model: String = "",
    val tokensUsed: Int = 0,
)

// --- Converters ---

object Converters {

    private val json = Json { ignoreUnknownKeys = true }

    // --- MessageRole ---
    @TypeConverter
    fun messageRoleToString(role: MessageRole): String = role.name
    @TypeConverter
    fun stringToMessageRole(value: String): MessageRole = enumValueOf(value)

    // --- MemoryType ---
    @TypeConverter
    fun memoryTypeToString(type: MemoryType): String = type.name
    @TypeConverter
    fun stringToMemoryType(value: String): MemoryType = enumValueOf(value)

    // --- ThemeMode ---
    @TypeConverter
    fun themeModeToString(mode: ThemeMode): String = mode.name
    @TypeConverter
    fun stringToThemeMode(value: String): ThemeMode = enumValueOf(value)

    // --- LlmProvider ---
    @TypeConverter
    fun llmProviderToString(provider: LlmProvider): String = provider.name
    @TypeConverter
    fun stringToLlmProvider(value: String): LlmProvider = enumValueOf(value)

    // --- EmotionVector (Map<String, Float>) ---
    @TypeConverter
    fun mapToJson(map: Map<String, Float>): String = json.encodeToString(map)
    @TypeConverter
    fun jsonToMap(value: String): Map<String, Float> =
        json.decodeFromString(value)

    // --- MessageMetadata ---
    @TypeConverter
    fun metadataToJson(meta: MessageMetadata): String = json.encodeToString(meta)
    @TypeConverter
    fun jsonToMetadata(value: String): MessageMetadata =
        json.decodeFromString(value)
}
