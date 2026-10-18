package com.xiaoqi.companion.core.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.core.record.PurchaseItemDraft
import com.xiaoqi.companion.core.record.PurchaseRecordDraft
import com.xiaoqi.companion.data.db.converter.RecordSource
import com.xiaoqi.companion.data.repository.PurchaseRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 聊天内口述记采购(P3 工具化入口)。"在超市买了一板鸡蛋 15 个,24 块" → 入 purchase_records + 库存累加。
 * 与快记流共用 PurchaseRepository;数量/单价以用户口述为准,不猜。
 */
class LogPurchaseTool(
    private val purchaseRepository: PurchaseRepository,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) : SimpleTool<LogPurchaseTool.Args>(
    typeToken<Args>(),
    name = "log_purchase",
    description = "Log a purchase record when the user describes what they bought in chat " +
        "(e.g. '买了一板鸡蛋,15个,24块'). Only call this when the user explicitly states purchased items. " +
        "Quantity and unit must come from the user's words, never guessed.",
) {

    // Dagger 不认 Function0 默认参数,注入走这个不含 nowProvider 的构造
    @Inject
    constructor(
        purchaseRepository: PurchaseRepository,
    ) : this(purchaseRepository, { System.currentTimeMillis() })

    @Serializable
    data class PurchaseItemArg(
        @param:LLMDescription("Product name in the user's language, e.g. 鸡蛋")
        val name: String,
        @param:LLMDescription("Brand if the user mentioned it, otherwise omit")
        val brand: String? = null,
        @param:LLMDescription("Quantity purchased as the user stated, e.g. 15")
        val quantity: Double,
        @param:LLMDescription("Counting unit, e.g. 个 / 板 / 盒 / 斤 / 袋")
        val unit: String = "个",
        @param:LLMDescription("Price per unit in yuan if the user stated it")
        val unit_price: Double? = null,
    )

    @Serializable
    data class Args(
        @param:LLMDescription("Purchased items the user described")
        val items: List<PurchaseItemArg>,
        @param:LLMDescription("Store name if the user mentioned it")
        val store_name: String? = null,
        @param:LLMDescription("If true (default), add purchased quantities into home inventory. Set false only if the user explicitly says not to track stock.")
        val adjust_inventory: Boolean = true,
    )

    override suspend fun execute(args: Args): String = withContext(Dispatchers.IO) {
        AppLogger.info(
            LogTags.Record,
            "log_purchase_tool_requested",
            "itemCount" to args.items.size,
            "adjustInventory" to args.adjust_inventory,
        )
        val validItems = args.items.filter { it.name.isNotBlank() && it.quantity > 0 }
        if (validItems.isEmpty()) {
            return@withContext encode(
                ToolEnvelopeFactory.err(
                    reason = "purchase_items_empty",
                    hint = "没有可记录的商品条目。请让用户说清楚买了什么、买了多少。",
                )
            )
        }

        val draft = PurchaseRecordDraft(
            items = validItems.map {
                PurchaseItemDraft(
                    name = it.name.trim(),
                    brand = it.brand?.trim()?.takeIf { brand -> brand.isNotBlank() },
                    quantity = it.quantity.coerceIn(0.01, MAX_QUANTITY),
                    unit = it.unit.trim().ifBlank { "个" },
                    unitPrice = it.unit_price?.coerceIn(0.0, MAX_PRICE),
                )
            },
            source = RecordSource.TEXT,
            storeName = args.store_name?.trim()?.takeIf { it.isNotBlank() },
            purchasedAt = nowProvider(),
        )
        val recordId = purchaseRepository.savePurchase(draft, adjustInventory = args.adjust_inventory)

        val totalCost = draft.items.sumOf { (it.unitPrice ?: 0.0) * it.quantity }
        encode(
            ToolEnvelopeFactory.ok(
                buildJsonObject {
                    put("recordId", recordId)
                    put("itemCount", draft.items.size)
                    put("totalCost", totalCost)
                    put("inventoryAdjusted", args.adjust_inventory)
                }
            )
        )
    }

    private companion object {
        const val MAX_QUANTITY = 10_000.0
        const val MAX_PRICE = 100_000.0
    }
}
