package com.xiaoqi.companion.core.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.data.repository.InventoryRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 让 LLM 查家庭库存(P3 工具化入口)。
 * "家里还有鸡蛋吗" 的数据来源;低库存条目带 isLow 标记,LLM 可据此建议补货。
 */
class QueryInventoryTool @Inject constructor(
    private val inventoryRepository: InventoryRepository,
) : SimpleTool<QueryInventoryTool.Args>(
    typeToken<Args>(),
    name = "query_inventory",
    description = "Query current home inventory (stockpiled items with remaining quantities). " +
        "Use this when the user asks what's left at home or whether they need to buy something. " +
        "Items at or below their low-stock threshold are flagged.",
) {

    @Serializable
    data class Args(
        @param:LLMDescription("Optional name substring filter, e.g. 鸡蛋. Omit to list all items.")
        val name_filter: String? = null,
    )

    @Serializable
    private data class InventoryItem(
        val name: String,
        val brand: String? = null,
        val unit: String,
        val quantity: Double,
        val isLow: Boolean,
    )

    @Serializable
    private data class InventoryResult(
        val count: Int,
        val lowCount: Int,
        val items: List<InventoryItem>,
    )

    override suspend fun execute(args: Args): String = withContext(Dispatchers.IO) {
        val filter = args.name_filter?.trim()?.takeIf { it.isNotBlank() }
        val all = inventoryRepository.observeInventory().first()
        val matched = all.filter { item ->
            filter == null || item.name.contains(filter, ignoreCase = true) ||
                item.brand?.contains(filter, ignoreCase = true) == true
        }
        val items = matched.map { item ->
            InventoryItem(
                name = item.name,
                brand = item.brand,
                unit = item.unit,
                quantity = item.currentQuantity,
                isLow = (item.lowStockThreshold ?: 0.0) >= item.currentQuantity && (item.lowStockThreshold ?: 0.0) > 0.0,
            )
        }
        val result = InventoryResult(
            count = items.size,
            lowCount = items.count { it.isLow },
            items = items.take(MAX_ITEMS),
        )
        AppLogger.info(
            LogTags.Record,
            "query_inventory_completed",
            "filter" to (filter ?: ""),
            "count" to result.count,
            "lowCount" to result.lowCount,
        )
        json.encodeToString(result)
    }

    private companion object {
        // LLM 上下文预算:超出的条目只报数量
        const val MAX_ITEMS = 30
        val json = Json { encodeDefaults = true; explicitNulls = false }
    }
}
