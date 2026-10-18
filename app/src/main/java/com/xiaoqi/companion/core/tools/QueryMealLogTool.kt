package com.xiaoqi.companion.core.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.data.db.converter.MealType
import com.xiaoqi.companion.data.repository.MealRecordRepository
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 让 LLM 查最近的餐食记录(P3 工具化入口)。
 * "我今天吃了多少" / "这周蛋白质够吗" 这类问题的数据来源;
 * 与 query_health_data 同族:只读本地缓存,不触发任何同步。
 */
class QueryMealLogTool @Inject constructor(
    private val mealRepository: MealRecordRepository,
) : SimpleTool<QueryMealLogTool.Args>(
    typeToken<Args>(),
    name = "query_meal_log",
    description = "Query recent structured meal records (what the user logged, with calorie and nutrition totals). " +
        "Use this when the user asks about their recent diet, calories, or nutrition intake.",
) {

    @Serializable
    data class Args(
        @param:LLMDescription("Number of days to look back from today. Default 7, max 30.")
        val days: Int = 7,
        @param:LLMDescription("Optional meal type filter: BREAKFAST, LUNCH, DINNER or SNACK. Omit for all meals.")
        val meal_type: MealType? = null,
    )

    @Serializable
    private data class MealRecordItem(
        val id: String,
        val mealType: String,
        val createdAt: Long,
        val totalCalories: Double,
        val totalProteinG: Double,
        val totalCarbsG: Double,
        val totalFatG: Double,
        val source: String,
        val rawNote: String? = null,
        val items: List<String>,
    )

    @Serializable
    private data class MealLogResult(
        val days: Int,
        val count: Int,
        val totalCalories: Double,
        val totalProteinG: Double,
        val records: List<MealRecordItem>,
    )

    override suspend fun execute(args: Args): String = withContext(Dispatchers.IO) {
        val days = args.days.coerceIn(1, MAX_LOOKBACK_DAYS)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val startMillis = today.minusDays((days - 1).toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val records = mealRepository.getRecordsBetween(startMillis, endMillis)
            .filter { args.meal_type == null || it.record.mealType == args.meal_type }
        val result = MealLogResult(
            days = days,
            count = records.size,
            totalCalories = records.sumOf { it.record.totalCalories },
            totalProteinG = records.sumOf { it.record.totalProteinG },
            records = records.take(MAX_RECORDS).map { withItems ->
                MealRecordItem(
                    id = withItems.record.id,
                    mealType = withItems.record.mealType.name,
                    createdAt = withItems.record.createdAt,
                    totalCalories = withItems.record.totalCalories,
                    totalProteinG = withItems.record.totalProteinG,
                    totalCarbsG = withItems.record.totalCarbsG,
                    totalFatG = withItems.record.totalFatG,
                    source = withItems.record.source.name,
                    rawNote = withItems.record.rawNote,
                    items = withItems.items.map { item -> "${item.name}(${item.portionText})" },
                )
            },
        )
        AppLogger.info(
            LogTags.Record,
            "query_meal_log_completed",
            "days" to days,
            "count" to result.count,
            "totalCalories" to result.totalCalories,
        )
        json.encodeToString(result)
    }

    private companion object {
        const val MAX_LOOKBACK_DAYS = 30
        // LLM 上下文预算:超出的记录只报数量,不展开
        const val MAX_RECORDS = 20
        val json = Json { encodeDefaults = true; explicitNulls = false }
    }
}
