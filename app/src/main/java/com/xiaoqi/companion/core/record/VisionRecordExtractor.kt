package com.xiaoqi.companion.core.record

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart
import com.xiaoqi.companion.core.llm.AnthropicMessagesLLMClientFactory
import com.xiaoqi.companion.core.llm.LlmForcedToolMissingException
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.data.db.converter.MealType
import com.xiaoqi.companion.data.db.converter.RecordSource
import com.xiaoqi.companion.data.db.converter.LlmProvider
import com.xiaoqi.companion.data.repository.ConfigRepository
import com.xiaoqi.companion.data.repository.MealRecordRepository
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

sealed interface RecordExtractionResult {
    data class Meal(val draft: MealRecordDraft) : RecordExtractionResult
    data class Purchase(val draft: PurchaseRecordDraft) : RecordExtractionResult
    data class Failed(val reason: String) : RecordExtractionResult
}

// 数值钳制:照片估热量误差 ±30-50%,越界值视为模型幻觉处理而非静默入库(design.md 估算诚实原则)
internal const val CALORIE_MAX_PER_ITEM = 3000.0
internal const val PROTEIN_MAX_PER_ITEM = 150.0
internal const val CARB_MAX_PER_ITEM = 300.0
internal const val FAT_MAX_PER_ITEM = 150.0
internal const val PURCHASE_QUANTITY_MAX = 10_000.0
internal const val PURCHASE_PRICE_MAX = 100_000.0
internal const val GRAMS_MAX_PER_ITEM = 5_000.0

/**
 * 生活记录图片 → 结构化提取器。一次强制工具调用,按 tool_choice=any 让模型在
 * submit_meal_record / submit_purchase_record 间自选完成域分类(design.md §6:
 * 提取器分类,确认页域切换兜底 —— [domainHint] 供手动切换后按指定域重提)。
 *
 * 餐食营养走两段式(MR 精度策略):模型只负责菜名 + 可食部克重,密度命中时营养
 * 由《成分表》密度 × 克重本地计算,消掉"同一道菜每次估的热量都不同"的方差;
 * 密度未命中再降级模型估算。用户修正过的条目(isEstimate=false)优先级最高。
 *
 * 云端 GLM 专用 —— 本地 Qwen 不做结构化 vision 提取(质量不可靠,roadmap MR 决策)。
 */
class VisionRecordExtractor(
    private val configRepository: ConfigRepository,
    private val clientFactory: AnthropicMessagesLLMClientFactory,
    private val foodDensitySource: FoodDensitySource,
    private val mealRepository: MealRecordRepository,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) {

    // Dagger 不认 Function0 默认参数,注入走这个不含 nowProvider 的构造
    @Inject
    constructor(
        configRepository: ConfigRepository,
        clientFactory: AnthropicMessagesLLMClientFactory,
        foodDensitySource: FoodDensitySource,
        mealRepository: MealRecordRepository,
    ) : this(configRepository, clientFactory, foodDensitySource, mealRepository, { System.currentTimeMillis() })

    suspend fun extract(
        imageBase64: String,
        mediaType: String,
        note: String? = null,
        domainHint: RecordDomain? = null,
        photoTakenAtMillis: Long? = null,
    ): RecordExtractionResult {
        val config = configRepository.getCurrentLlmConfig().first()
        if (config.provider == LlmProvider.LOCAL_QWEN) {
            return RecordExtractionResult.Failed("本地模型不支持图片结构化提取,请切换云端 Provider")
        }
        if (config.apiKey.isBlank() || config.baseUrl.isBlank()) {
            return RecordExtractionResult.Failed("缺少 API Key 或 Base URL,请先在设置页配置")
        }
        val client = clientFactory.create(config.apiKey, config.baseUrl)
        val model = LLModel(
            provider = LLMProvider.Anthropic,
            id = config.modelName,
            capabilities = listOf(LLMCapability.Completion, LLMCapability.Vision.Image),
        )
        val domainText = note?.takeIf { it.isNotBlank() } ?: "提取这张照片里的结构化记录。"
        val requestPrompt = prompt("record-extraction") {
            system(EXTRACTION_SYSTEM_PROMPT)
            user {
                text(domainText)
                image(
                    ContentPart.Image(
                        content = AttachmentContent.Binary.Base64(imageBase64),
                        format = "base64",
                        mimeType = mediaType,
                    ),
                )
            }
        }
        val (tools, forceName) = when (domainHint) {
            RecordDomain.MEAL -> listOf(MEAL_TOOL_SCHEMA) to MEAL_TOOL_NAME
            RecordDomain.PURCHASE -> listOf(PURCHASE_TOOL_SCHEMA) to PURCHASE_TOOL_NAME
            null -> listOf(MEAL_TOOL_SCHEMA, PURCHASE_TOOL_SCHEMA) to null
        }
        val call = try {
            client.executeForcedTool(requestPrompt, model, tools, forceName)
        } catch (error: LlmForcedToolMissingException) {
            // 模型以文本拒绝(如"照片里没有食物"):原话透传给用户,比内部协议错误诚实得多
            AppLogger.warn(
                LogTags.Record,
                "record_extraction_model_declined",
                "model" to config.modelName,
                "domainHint" to domainHint?.name,
                "replyLength" to error.modelReply.length,
            )
            val reason = error.modelReply.lineSequence().firstOrNull { it.isNotBlank() }
                ?.take(200)
                ?.ifBlank { null }
                ?: "未识别出结构化记录,可手动填写"
            return RecordExtractionResult.Failed(reason)
        } catch (error: Exception) {
            AppLogger.warn(
                LogTags.Record,
                "record_extraction_request_failed",
                "model" to config.modelName,
                "error" to (error.message ?: error::class.simpleName.orEmpty()),
            )
            return RecordExtractionResult.Failed("提取请求失败:${error.message ?: "网络错误"}")
        }
        val hour = photoTakenAtMillis?.let { takenAt ->
            Instant.ofEpochMilli(takenAt).atZone(ZoneId.systemDefault()).hour
        } ?: Instant.ofEpochMilli(nowProvider()).atZone(ZoneId.systemDefault()).hour
        return when (call.name.ifBlank { forceName ?: "" }) {
            MEAL_TOOL_NAME -> {
                val parsed = parseMealToolInput(call.inputJson, note, hour)
                if (parsed is RecordExtractionResult.Meal) {
                    RecordExtractionResult.Meal(resolveMealNutrition(parsed.draft))
                } else {
                    parsed
                }
            }
            PURCHASE_TOOL_NAME -> parsePurchaseToolInput(call.inputJson, note)
            else -> {
                AppLogger.warn(LogTags.Record, "record_extraction_unknown_tool", "tool" to call.name)
                RecordExtractionResult.Failed("提取返回了未知记录类型,可重试或手动填写")
            }
        }
    }

    /**
     * 营养数值三级解析,优先级从高到低:
     * 1. 个人校准:用户修正过同名条目 → 直接套用其克重换算(用户比任何表都懂自己的碗)
     * 2. 密度表:菜名命中《成分表》→ 密度 × 模型克重
     * 3. 模型估算:保留原值(唯一误差源=模型,方差回到纯 prompt 水平)
     */
    private suspend fun resolveMealNutrition(draft: MealRecordDraft): MealRecordDraft {
        if (draft.items.isEmpty()) return draft
        val densityTable = foodDensitySource.load()
        val calibrated = runCatching {
            mealRepository.getRecentCalibratedItems(
                sinceAt = nowProvider() - CALIBRATION_WINDOW_DAYS * MILLIS_PER_DAY,
                limit = 30,
            )
        }.getOrDefault(emptyList())
            .associateBy { it.name.trim().lowercase() }
        if (densityTable == null && calibrated.isEmpty()) return draft

        var densityHitCount = 0
        var calibrationHitCount = 0
        val resolved = draft.items.map { item ->
            val nameKey = item.name.trim().lowercase()
            calibrated[nameKey]?.let { calib ->
                calibrationHitCount += 1
                val grams = item.estimatedGrams
                return@map if (grams > 0) scaleCalibrated(calib, grams) else item
            }
            val entry = densityTable?.lookup(item.name) ?: return@map item
            val grams = if (item.estimatedGrams > 0) item.estimatedGrams else gramsFromCalories(item.calories, entry)
            if (grams <= 0.0) return@map item
            densityHitCount += 1
            item.copy(
                calories = round1(entry.kcalPer100g * grams / 100.0),
                proteinG = round1(entry.proteinPer100g * grams / 100.0),
                carbsG = round1(entry.carbsPer100g * grams / 100.0),
                fatG = round1(entry.fatPer100g * grams / 100.0),
                estimatedGrams = grams,
            )
        }
        AppLogger.info(
            LogTags.Record,
            "meal_nutrition_resolved",
            "itemCount" to draft.items.size,
            "densityHitCount" to densityHitCount,
            "calibrationHitCount" to calibrationHitCount,
            "tableLoaded" to (densityTable != null),
        )
        return draft.copy(items = resolved)
    }

    private fun scaleCalibrated(calib: com.xiaoqi.companion.data.db.dao.CalibratedMealItemRow, grams: Double): MealItemDraft {
        // 修正记录的克重从 portionText 里的数字提取(如"1碗(350g)"),拿不到就按比例近似不缩放
        val baseGrams = GRAMS_IN_PORTION.find(calib.portionText)?.groupValues?.get(1)?.toDoubleOrNull() ?: return run {
            MealItemDraft(
                name = calib.name,
                portionText = calib.portionText,
                calories = calib.estCalories,
                proteinG = calib.estProteinG,
                carbsG = calib.estCarbsG,
                fatG = calib.estFatG,
                isEstimate = false,
                estimatedGrams = grams,
            )
        }
        val scale = if (baseGrams > 0) grams / baseGrams else 1.0
        return MealItemDraft(
            name = calib.name,
            portionText = calib.portionText,
            calories = round1(calib.estCalories * scale),
            proteinG = round1(calib.estProteinG * scale),
            carbsG = round1(calib.estCarbsG * scale),
            fatG = round1(calib.estFatG * scale),
            isEstimate = false,
            estimatedGrams = grams,
        )
    }

    private fun gramsFromCalories(calories: Double, entry: FoodDensityEntry): Double =
        if (entry.kcalPer100g > 0) calories / entry.kcalPer100g * 100.0 else 0.0

    private fun round1(v: Double): Double = Math.round(v * 10.0) / 10.0

    companion object {
        private const val MEAL_TOOL_NAME = "submit_meal_record"
        private const val PURCHASE_TOOL_NAME = "submit_purchase_record"
        private const val CALIBRATION_WINDOW_DAYS = 60
        private const val MILLIS_PER_DAY = 86_400_000L
        private val GRAMS_IN_PORTION = Regex("""(\d+(?:\.\d+)?)\s*g""", RegexOption.IGNORE_CASE)

        private const val EXTRACTION_SYSTEM_PROMPT =
            "You are a life logging extractor. Analyze the photo (plus the optional user note) and submit ONE " +
                "structured record by choosing the matching tool. Photo of prepared food, a dish, or a meal being " +
                "eaten → submit_meal_record. Photo of groceries, a supermarket receipt, or packaged products bought " +
                "→ submit_purchase_record (extract brand, quantity, unit and unit price from labels or receipt). " +
                "If the photo shows neither, reply in plain text explaining why. " +
                "Use the language of the photo/note for item names.\n\n" +
                "## Estimating grams (meal items)\n" +
                "estimated_grams is the weight of the EDIBLE portion on the plate/in the bowl, not the raw weight. " +
                "Use reference objects in the photo to judge volume:\n" +
                "- standard rice bowl ≈ 300ml (cooked rice ≈ 200g), large ramen bowl ≈ 600ml (noodles ≈ 300g)\n" +
                "- chinese soup spoon ≈ 10ml; pair of disposable chopsticks ≈ 25cm long (plate diameter reference)\n" +
                "- palm-sized piece of meat ≈ 100g; one egg ≈ 50g; one bottle cap of oil ≈ 5g\n" +
                "- packaged foods: read the label and use its net weight\n" +
                "Prefer the user's note when it states amounts. When unsure, choose the midpoint of plausible range.\n" +
                "Nutrition fields are fallbacks only — the app recomputes them from a food composition table when the " +
                "dish name matches, so put your effort into naming the dish precisely (generic core name, e.g. " +
                "牛肉面 not 张记招牌牛肉拉面) and estimating grams."
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
    val estimated_grams: Double = 0.0,
)

@Serializable
private data class MealRecordDto(
    val confidence: Double? = null,
    val items: List<MealItemDto> = emptyList(),
)

@Serializable
private data class PurchaseItemDto(
    val name: String = "",
    val brand: String? = null,
    val quantity: Double = 0.0,
    val unit: String = "",
    val unit_price: Double? = null,
)

@Serializable
private data class PurchaseRecordDto(
    val store_name: String? = null,
    val confidence: Double? = null,
    val items: List<PurchaseItemDto> = emptyList(),
)

internal val MEAL_TOOL_SCHEMA: JsonObject = buildJsonObject {
    put("name", "submit_meal_record")
    put(
        "description",
        "Submit one structured MEAL record for a photo of prepared food / a dish / a meal being eaten. Estimate nutrition per visible portion.",
    )
    put("input_schema", buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
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
                        put("estimated_grams", buildJsonObject {
                            put("type", "number")
                            put("description", "Estimated weight of the edible portion in grams")
                        })
                    })
                    put("required", buildJsonArray {
                        add(JsonPrimitive("name"))
                        add(JsonPrimitive("portion_text"))
                        add(JsonPrimitive("calories"))
                        add(JsonPrimitive("estimated_grams"))
                    })
                })
            })
        })
        put("required", buildJsonArray {
            add(JsonPrimitive("items"))
        })
    })
}

internal val PURCHASE_TOOL_SCHEMA: JsonObject = buildJsonObject {
    put("name", "submit_purchase_record")
    put(
        "description",
        "Submit one structured PURCHASE record for a photo of groceries / a supermarket receipt / packaged products bought. Extract brand, quantity, unit and unit price.",
    )
    put("input_schema", buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("store_name", buildJsonObject {
                put("type", "string")
                put("description", "Store name if visible on the receipt or packaging")
            })
            put("confidence", buildJsonObject {
                put("type", "number")
                put("description", "Overall extraction confidence, 0.0 to 1.0")
            })
            put("items", buildJsonObject {
                put("type", "array")
                put("description", "Distinct purchased products")
                put("items", buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("name", buildJsonObject {
                            put("type", "string")
                            put("description", "Product name; follow the language of the photo or receipt")
                        })
                        put("brand", buildJsonObject { put("type", "string"); put("description", "Brand if visible") })
                        put("quantity", buildJsonObject {
                            put("type", "number")
                            put("description", "Purchased amount in the given unit, e.g. 15 for a tray of 15 eggs")
                        })
                        put("unit", buildJsonObject {
                            put("type", "string")
                            put("description", "Counting unit, e.g. 个 / 板 / 盒 / 斤 / 袋")
                        })
                        put("unit_price", buildJsonObject { put("type", "number"); put("description", "Price per unit if visible") })
                    })
                    put("required", buildJsonArray {
                        add(JsonPrimitive("name"))
                        add(JsonPrimitive("quantity"))
                    })
                })
            })
        })
        put("required", buildJsonArray {
            add(JsonPrimitive("items"))
        })
    })
}

internal fun parseMealToolInput(
    inputJson: String,
    note: String?,
    hour: Int,
): RecordExtractionResult {
    val dto = try {
        extractionJson.decodeFromString<MealRecordDto>(inputJson)
    } catch (error: Exception) {
        AppLogger.warn(LogTags.Record, "meal_extraction_parse_failed", "error" to (error.message ?: ""))
        return RecordExtractionResult.Failed("提取结果解析失败,可手动填写")
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
                estimatedGrams = item.estimated_grams.coerceIn(0.0, GRAMS_MAX_PER_ITEM),
            )
        }
    if (items.isEmpty()) {
        return RecordExtractionResult.Failed("未识别出食物条目,可手动填写")
    }
    return RecordExtractionResult.Meal(
        MealRecordDraft(
            // 餐次只由时间决定(拍摄时间优先,否则当前时间),与食物内容无关
            mealType = mealTypeForHour(hour),
            source = RecordSource.PHOTO,
            items = items,
            confidence = (dto.confidence ?: 0.5).coerceIn(0.0, 1.0),
            rawNote = note?.takeIf { it.isNotBlank() },
        ),
    )
}

internal fun parsePurchaseToolInput(inputJson: String, note: String?): RecordExtractionResult {
    val dto = try {
        extractionJson.decodeFromString<PurchaseRecordDto>(inputJson)
    } catch (error: Exception) {
        AppLogger.warn(LogTags.Record, "purchase_extraction_parse_failed", "error" to (error.message ?: ""))
        return RecordExtractionResult.Failed("提取结果解析失败,可手动填写")
    }
    val items = dto.items
        .filter { it.name.isNotBlank() && it.quantity > 0 }
        .map { item ->
            PurchaseItemDraft(
                name = item.name.trim(),
                brand = item.brand?.trim()?.takeIf { it.isNotBlank() },
                quantity = item.quantity.coerceIn(0.01, PURCHASE_QUANTITY_MAX),
                unit = item.unit.trim().ifBlank { "个" },
                unitPrice = item.unit_price?.coerceIn(0.0, PURCHASE_PRICE_MAX),
            )
        }
    if (items.isEmpty()) {
        return RecordExtractionResult.Failed("未识别出采购条目,可手动填写")
    }
    return RecordExtractionResult.Purchase(
        PurchaseRecordDraft(
            items = items,
            source = RecordSource.PHOTO,
            storeName = dto.store_name?.trim()?.takeIf { it.isNotBlank() },
            totalCost = null,
            imageLocalPath = null,
        ),
    )
}

private fun mealTypeForHour(hour: Int): MealType = when (hour) {
    in 5..10 -> MealType.BREAKFAST
    in 11..16 -> MealType.LUNCH
    in 17..21 -> MealType.DINNER
    else -> MealType.SNACK
}

private val extractionJson = Json { ignoreUnknownKeys = true }
