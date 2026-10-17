package com.xiaoqi.companion.core.record

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart
import com.xiaoqi.companion.core.llm.AnthropicMessagesLLMClientFactory
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.data.db.converter.MealType
import com.xiaoqi.companion.data.db.converter.RecordSource
import com.xiaoqi.companion.data.db.converter.LlmProvider
import com.xiaoqi.companion.data.repository.ConfigRepository
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

sealed interface MealExtractionResult {
    data class Extracted(val draft: MealRecordDraft) : MealExtractionResult
    data class Failed(val reason: String) : MealExtractionResult
}

// 数值钳制:照片估热量误差 ±30-50%,越界值视为模型幻觉处理而非静默入库(design.md 估算诚实原则)
internal const val CALORIE_MAX_PER_ITEM = 3000.0
internal const val PROTEIN_MAX_PER_ITEM = 150.0
internal const val CARB_MAX_PER_ITEM = 300.0
internal const val FAT_MAX_PER_ITEM = 150.0

/**
 * 餐食图片 → 结构化记录提取器。走 tool_choice 强制调用(嵌套 items schema 由
 * [com.xiaoqi.companion.core.llm.AnthropicMessagesLLMClient.executeForcedTool] 承载),
 * 云端 GLM 专用 —— 本地 Qwen 不做结构化 vision 提取(质量不可靠,roadmap MR 决策)。
 */
class VisionMealExtractor @Inject constructor(
    private val configRepository: ConfigRepository,
    private val clientFactory: AnthropicMessagesLLMClientFactory,
) {

    suspend fun extractMeal(imageBase64: String, mediaType: String, note: String? = null): MealExtractionResult {
        val config = configRepository.getCurrentLlmConfig().first()
        if (config.provider == LlmProvider.LOCAL_QWEN) {
            return MealExtractionResult.Failed("本地模型不支持图片结构化提取,请切换云端 Provider")
        }
        if (config.apiKey.isBlank() || config.baseUrl.isBlank()) {
            return MealExtractionResult.Failed("缺少 API Key 或 Base URL,请先在设置页配置")
        }
        val client = clientFactory.create(config.apiKey, config.baseUrl)
        val model = LLModel(
            provider = LLMProvider.Anthropic,
            id = config.modelName,
            capabilities = listOf(LLMCapability.Completion, LLMCapability.Vision.Image),
        )
        val requestPrompt = prompt("meal-extraction") {
            system(EXTRACTION_SYSTEM_PROMPT)
            user {
                text(note?.takeIf { it.isNotBlank() } ?: "提取这张餐食照片的结构化记录。")
                image(
                    ContentPart.Image(
                        content = AttachmentContent.Binary.Base64(imageBase64),
                        format = "base64",
                        mimeType = mediaType,
                    ),
                )
            }
        }
        val call = try {
            client.executeForcedTool(requestPrompt, model, MEAL_TOOL_SCHEMA)
        } catch (error: Exception) {
            AppLogger.warn(
                LogTags.Record,
                "meal_extraction_request_failed",
                "model" to config.modelName,
                "error" to (error.message ?: error::class.simpleName.orEmpty()),
            )
            return MealExtractionResult.Failed("提取请求失败:${error.message ?: "网络错误"}")
        }
        return parseMealToolInput(call.inputJson, note, LocalTime.now().hour)
    }

    companion object {
        private const val EXTRACTION_SYSTEM_PROMPT =
            "You are a meal logging extractor. Analyze the meal photo (plus the optional user note) and submit " +
                "one structured meal record. Nutrition values are estimates based on the visible food and typical " +
                "portions — record the portion basis in portion_text. Use the language of the photo/note for item " +
                "names. Never leave items empty if any food is visible."
    }
}

@Serializable
private data class MealItemDto(
    val name: String = "",
    val portion_text: String = "",
    val calories: Double = 0.0,
    val protein_g: Double = 0.0,
    val carbs_g: Double = 0.0,
    val fat_g: Double = 0.0,
)

@Serializable
private data class MealRecordDto(
    val meal_type: String = "",
    val confidence: Double? = null,
    val items: List<MealItemDto> = emptyList(),
)

internal val MEAL_TOOL_SCHEMA: JsonObject = buildJsonObject {
    put("name", "submit_meal_record")
    put("description", "Submit one structured meal record extracted from the meal photo. Call exactly once with the complete record.")
    put("input_schema", buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("meal_type", buildJsonObject {
                put("type", "string")
                put("enum", buildJsonArray {
                    listOf("BREAKFAST", "LUNCH", "DINNER", "SNACK").forEach { add(JsonPrimitive(it)) }
                })
            })
            put("confidence", buildJsonObject {
                put("type", "number")
                put("description", "Overall extraction confidence, 0.0 to 1.0")
            })
            put("items", buildJsonObject {
                put("type", "array")
                put("description", "Distinct food items visible in the photo")
                put("items", buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("name", buildJsonObject {
                            put("type", "string")
                            put("description", "Food name; follow the language of the photo or note")
                        })
                        put("portion_text", buildJsonObject {
                            put("type", "string")
                            put("description", "Portion basis of the estimates, e.g. 1 bowl / about 200g")
                        })
                        put("calories", buildJsonObject { put("type", "number"); put("description", "Estimated kcal for this portion") })
                        put("protein_g", buildJsonObject { put("type", "number") })
                        put("carbs_g", buildJsonObject { put("type", "number") })
                        put("fat_g", buildJsonObject { put("type", "number") })
                    })
                    put("required", buildJsonArray {
                        add(JsonPrimitive("name"))
                        add(JsonPrimitive("portion_text"))
                        add(JsonPrimitive("calories"))
                    })
                })
            })
        })
        put("required", buildJsonArray {
            add(JsonPrimitive("meal_type"))
            add(JsonPrimitive("items"))
        })
    })
}

internal fun parseMealToolInput(inputJson: String, note: String?, hour: Int): MealExtractionResult {
    val dto = try {
        extractionJson.decodeFromString<MealRecordDto>(inputJson)
    } catch (error: Exception) {
        AppLogger.warn(LogTags.Record, "meal_extraction_parse_failed", "error" to (error.message ?: ""))
        return MealExtractionResult.Failed("提取结果解析失败,可手动填写")
    }
    val items = dto.items
        .filter { it.name.isNotBlank() }
        .map { item ->
            MealItemDraft(
                name = item.name.trim(),
                portionText = item.portion_text.trim().ifBlank { "1 份" },
                calories = item.calories.coerceIn(0.0, CALORIE_MAX_PER_ITEM),
                proteinG = item.protein_g.coerceIn(0.0, PROTEIN_MAX_PER_ITEM),
                carbsG = item.carbs_g.coerceIn(0.0, CARB_MAX_PER_ITEM),
                fatG = item.fat_g.coerceIn(0.0, FAT_MAX_PER_ITEM),
            )
        }
    if (items.isEmpty()) {
        return MealExtractionResult.Failed("未识别出食物条目,可手动填写")
    }
    return MealExtractionResult.Extracted(
        MealRecordDraft(
            mealType = dto.meal_type.toMealTypeOrDefault(hour),
            source = RecordSource.PHOTO,
            items = items,
            confidence = (dto.confidence ?: 0.5).coerceIn(0.0, 1.0),
            rawNote = note?.takeIf { it.isNotBlank() },
        ),
    )
}

private fun String.toMealTypeOrDefault(hour: Int): MealType = when (uppercase().trim()) {
    "BREAKFAST" -> MealType.BREAKFAST
    "LUNCH" -> MealType.LUNCH
    "DINNER" -> MealType.DINNER
    "SNACK" -> MealType.SNACK
    else -> mealTypeForHour(hour)
}

private fun mealTypeForHour(hour: Int): MealType = when (hour) {
    in 5..10 -> MealType.BREAKFAST
    in 11..16 -> MealType.LUNCH
    in 17..21 -> MealType.DINNER
    else -> MealType.SNACK
}

private val extractionJson = Json { ignoreUnknownKeys = true }
