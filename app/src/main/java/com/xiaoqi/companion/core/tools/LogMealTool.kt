package com.xiaoqi.companion.core.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.core.record.MealItemDraft
import com.xiaoqi.companion.core.record.MealRecordDraft
import com.xiaoqi.companion.data.db.converter.MealType
import com.xiaoqi.companion.data.db.converter.RecordSource
import com.xiaoqi.companion.data.repository.InventoryRepository
import com.xiaoqi.companion.data.repository.MealRecordRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 聊天内口述记餐(P3 工具化入口)。"中午吃了一碗牛肉面" → 入 meal_records。
 *
 * 与快记流(RecordCaptureScreen)共用 MealRecordRepository,数据同源;
 * 区别是这里没有图片与确认页 —— 数值全部来自模型对用户口述的估算,
 * 所以要求模型把不确定的营养值按保守值填,且只有用户明确说了食物才调用。
 *
 * 库存消耗联动:consumed_items 由模型显式给出 —— 只在用户口述里明确提到
 * "吃了 N 个家里的 X"(如"早上吃了两个鸡蛋")时才填,宁缺毋滥。
 */
class LogMealTool(
    private val mealRepository: MealRecordRepository,
    private val inventoryRepository: InventoryRepository,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) : SimpleTool<LogMealTool.Args>(
    typeToken<Args>(),
    name = "log_meal",
    description = "Log a structured meal record when the user describes what they ate in chat " +
        "(e.g. '中午吃了一碗牛肉面'). Only call this when the user explicitly states food items they ate. " +
        "Estimate nutrition from the description; keep estimates conservative when uncertain.",
) {

    // Dagger 不认 Function0 默认参数,注入走这个不含 nowProvider 的构造
    @Inject
    constructor(
        mealRepository: MealRecordRepository,
        inventoryRepository: InventoryRepository,
    ) : this(mealRepository, inventoryRepository, { System.currentTimeMillis() })

    @Serializable
    data class MealItemArg(
        @param:LLMDescription("Food name, in the user's language, e.g. 牛肉面")
        val name: String,
        @param:LLMDescription("Portion description as the user stated it, e.g. 一碗 / 2 个 / 约200g")
        val portion_text: String = "1 份",
        @param:LLMDescription("Estimated calories (kcal) for this portion; conservative if uncertain")
        val calories: Double = 0.0,
        @param:LLMDescription("Estimated protein in grams")
        val protein_g: Double = 0.0,
        @param:LLMDescription("Estimated carbs in grams")
        val carbs_g: Double = 0.0,
        @param:LLMDescription("Estimated fat in grams")
        val fat_g: Double = 0.0,
    )

    @Serializable
    data class ConsumedItemArg(
        @param:LLMDescription("Pantry item name as the user said, e.g. 鸡蛋")
        val name: String,
        @param:LLMDescription("Brand if the user mentioned it, otherwise omit")
        val brand: String? = null,
        @param:LLMDescription("How many were consumed, e.g. 2")
        val quantity: Double,
        @param:LLMDescription("Counting unit, e.g. 个 / 盒 / 袋")
        val unit: String = "个",
    )

    @Serializable
    data class Args(
        @param:LLMDescription("Which meal this was")
        val meal_type: MealType,
        @param:LLMDescription("Food items the user said they ate")
        val items: List<MealItemArg>,
        @param:LLMDescription("Pantry items consumed from home stock. ONLY fill when the user explicitly stated eating N units of a stockpiled item (like '吃了两个鸡蛋'). Leave empty otherwise.")
        val consumed_items: List<ConsumedItemArg> = emptyList(),
        @param:LLMDescription("Optional note quoting or summarizing what the user said")
        val note: String? = null,
    )

    override suspend fun execute(args: Args): String = withContext(Dispatchers.IO) {
        AppLogger.info(
            LogTags.Record,
            "log_meal_tool_requested",
            "mealType" to args.meal_type.name,
            "itemCount" to args.items.size,
            "consumedCount" to args.consumed_items.size,
        )
        val validItems = args.items.filter { it.name.isNotBlank() }
        if (validItems.isEmpty()) {
            return@withContext encode(
                ToolEnvelopeFactory.err(
                    reason = "meal_items_empty",
                    hint = "没有可记录的食物条目。请让用户说清楚吃了什么,或至少一个食物名称。",
                )
            )
        }

        val draft = MealRecordDraft(
            mealType = args.meal_type,
            source = RecordSource.TEXT,
            items = validItems.map {
                MealItemDraft(
                    name = it.name.trim(),
                    portionText = it.portion_text.trim().ifBlank { "1 份" },
                    calories = it.calories.coerceIn(0.0, MAX_CALORIES),
                    proteinG = it.protein_g.coerceIn(0.0, MAX_PROTEIN),
                    carbsG = it.carbs_g.coerceIn(0.0, MAX_CARBS),
                    fatG = it.fat_g.coerceIn(0.0, MAX_FAT),
                )
            },
            confidence = 0.0,
            rawNote = args.note?.takeIf { it.isNotBlank() },
            createdAt = nowProvider(),
        )
        val recordId = mealRepository.saveMeal(draft)

        // 库存消耗:adjustStock 对不存在的条目 delta<=0 直接忽略,不需要预查
        val consumedResults = args.consumed_items
            .filter { it.name.isNotBlank() && it.quantity > 0 }
            .map { consumed ->
                val adjusted = inventoryRepository.adjustStock(
                    name = consumed.name,
                    brand = consumed.brand,
                    unit = consumed.unit,
                    quantityDelta = -consumed.quantity,
                )
                consumed.name to (adjusted?.currentQuantity)
            }
        if (consumedResults.isNotEmpty()) {
            AppLogger.info(
                LogTags.Record,
                "log_meal_inventory_consumed",
                "consumedCount" to consumedResults.size,
                "quantities" to consumedResults.associate { (name, qty) -> name to (qty ?: -1.0) },
            )
        }

        val totalCalories = draft.items.sumOf { it.calories }
        encode(
            ToolEnvelopeFactory.ok(
                buildJsonObject {
                    put("recordId", recordId)
                    put("mealType", args.meal_type.name)
                    put("itemCount", draft.items.size)
                    put("totalCalories", totalCalories)
                    put("consumedInventoryCount", consumedResults.size)
                }
            )
        )
    }

    private companion object {
        const val MAX_CALORIES = 3000.0
        const val MAX_PROTEIN = 150.0
        const val MAX_CARBS = 300.0
        const val MAX_FAT = 150.0
    }
}
