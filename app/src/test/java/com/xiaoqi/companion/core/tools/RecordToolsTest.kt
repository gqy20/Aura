package com.xiaoqi.companion.core.tools

import com.xiaoqi.companion.core.record.MealRecordDraft
import com.xiaoqi.companion.core.record.PurchaseItemDraft
import com.xiaoqi.companion.core.record.PurchaseRecordDraft
import com.xiaoqi.companion.data.db.converter.MealType
import com.xiaoqi.companion.data.db.entity.InventoryItemEntity
import com.xiaoqi.companion.data.repository.InventoryRepository
import com.xiaoqi.companion.data.repository.MealRecordRepository
import com.xiaoqi.companion.data.datastore.AppPreferences
import com.xiaoqi.companion.data.repository.PurchaseRepository
import com.xiaoqi.companion.data.repository.ReminderRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordToolsTest {

    private val reminderRepo = mockk<ReminderRepository>(relaxed = true)
    private val appPrefs = mockk<AppPreferences> {
        io.mockk.every { notificationEnabled } returns kotlinx.coroutines.flow.flowOf(true)
    }

    // --- log_meal ---

    @Test
    fun logMeal_savesDraftAndConsumesInventory() = runTest {
        val mealRepo = mockk<MealRecordRepository>()
        val inventoryRepo = mockk<InventoryRepository>(relaxed = true)
        val draftSlot = slot<MealRecordDraft>()
        coEvery { mealRepo.saveMeal(capture(draftSlot)) } returns "meal-1"
        coEvery { inventoryRepo.adjustStock("鸡蛋", null, "个", -2.0) } returns makeItem(quantity = 13.0)
        val tool = LogMealTool(mealRepo, inventoryRepo, reminderRepo, appPrefs, nowProvider = { 1_000L })

        val result = tool.execute(
            LogMealTool.Args(
                meal_type = MealType.BREAKFAST,
                items = listOf(
                    LogMealTool.MealItemArg(
                        name = "鸡蛋",
                        portion_text = "2 个",
                        calories = 160.0,
                        protein_g = 12.0,
                    ),
                ),
                consumed_items = listOf(
                    LogMealTool.ConsumedItemArg(name = "鸡蛋", quantity = 2.0, unit = "个"),
                ),
                note = "早上吃了两个鸡蛋",
            )
        )

        assertTrue(result.contains(""""status":"ok""""))
        assertTrue(result.contains(""""recordId":"meal-1""""))
        val draft = draftSlot.captured
        assertEquals(MealType.BREAKFAST, draft.mealType)
        assertEquals("鸡蛋", draft.items.single().name)
        assertEquals(160.0, draft.items.single().calories, 0.001)
        assertTrue(draft.items.single().isEstimate)
        assertEquals("早上吃了两个鸡蛋", draft.rawNote)
        assertEquals(1_000L, draft.createdAt)
        coVerify(exactly = 1) { inventoryRepo.adjustStock("鸡蛋", null, "个", -2.0) }
    }

    @Test
    fun logMeal_clampsOutOfRangeNutrition() = runTest {
        val mealRepo = mockk<MealRecordRepository>()
        val draftSlot = slot<MealRecordDraft>()
        coEvery { mealRepo.saveMeal(capture(draftSlot)) } returns "meal-1"
        val tool = LogMealTool(mealRepo, mockk(relaxed = true), reminderRepo, appPrefs, nowProvider = { 1L })

        tool.execute(
            LogMealTool.Args(
                meal_type = MealType.DINNER,
                items = listOf(
                    LogMealTool.MealItemArg(name = "宴席", calories = 99_999.0, protein_g = -5.0),
                ),
            )
        )

        val item = draftSlot.captured.items.single()
        assertEquals(3000.0, item.calories, 0.001)
        assertEquals(0.0, item.proteinG, 0.001)
    }

    @Test
    fun logMeal_emptyItemsReturnsEnvelopeError() = runTest {
        val tool = LogMealTool(mockk(), mockk(relaxed = true), reminderRepo, appPrefs, nowProvider = { 1L })

        val result = tool.execute(
            LogMealTool.Args(meal_type = MealType.LUNCH, items = listOf(LogMealTool.MealItemArg(name = "  ")))
        )

        assertTrue(result.contains(""""status":"error""""))
        assertTrue(result.contains("meal_items_empty"))
    }

    @Test
    fun logMeal_lowStockAfterConsumption_schedulesReminder() = runTest {
        val mealRepo = mockk<MealRecordRepository>()
        coEvery { mealRepo.saveMeal(any()) } returns "meal-1"
        val inventoryRepo = mockk<InventoryRepository>()
        coEvery { inventoryRepo.adjustStock("鸡蛋", null, "个", -6.0) } returns makeItem(id = "eggs", quantity = 1.0)
        val reminderRepo = mockk<ReminderRepository>()
        val scheduled = io.mockk.slot<com.xiaoqi.companion.core.reminder.ScheduledReminder>()
        coEvery { reminderRepo.createReminder(any(), any(), any(), any(), any()) } returns
            com.xiaoqi.companion.core.reminder.ScheduledReminder(
                id = "r1", title = "库存不多:鸡蛋", triggerAtMillis = 61_000L, delayMillis = 60_000L, exact = false,
            )
        val tool = LogMealTool(mealRepo, inventoryRepo, reminderRepo, appPrefs, nowProvider = { 1_000L })

        tool.execute(
            LogMealTool.Args(
                meal_type = MealType.BREAKFAST,
                items = listOf(LogMealTool.MealItemArg(name = "鸡蛋", calories = 200.0)),
                consumed_items = listOf(LogMealTool.ConsumedItemArg(name = "鸡蛋", quantity = 6.0, unit = "个")),
            )
        )

        coVerify(exactly = 1) { reminderRepo.createReminder(any(), any(), any(), any(), any()) }
    }

    @Test
    fun logMeal_sufficientStock_noReminder() = runTest {
        val mealRepo = mockk<MealRecordRepository>()
        coEvery { mealRepo.saveMeal(any()) } returns "meal-1"
        val inventoryRepo = mockk<InventoryRepository>()
        // 库存充足:15-2=13 > 阈值 2
        coEvery { inventoryRepo.adjustStock("鸡蛋", null, "个", -2.0) } returns makeItem(id = "eggs", quantity = 13.0)
        val reminderRepo = mockk<ReminderRepository>()
        val tool = LogMealTool(mealRepo, inventoryRepo, reminderRepo, appPrefs, nowProvider = { 1_000L })

        tool.execute(
            LogMealTool.Args(
                meal_type = MealType.BREAKFAST,
                items = listOf(LogMealTool.MealItemArg(name = "鸡蛋", calories = 200.0)),
                consumed_items = listOf(LogMealTool.ConsumedItemArg(name = "鸡蛋", quantity = 2.0, unit = "个")),
            )
        )

        coVerify(exactly = 0) { reminderRepo.createReminder(any(), any(), any(), any(), any()) }
    }

    @Test
    fun logMeal_skipsNonPositiveConsumedEntries() = runTest {
        val inventoryRepo = mockk<InventoryRepository>(relaxed = true)
        val mealRepo = mockk<MealRecordRepository>()
        coEvery { mealRepo.saveMeal(any()) } returns "meal-1"
        val tool = LogMealTool(mealRepo, inventoryRepo, reminderRepo, appPrefs, nowProvider = { 1L })

        tool.execute(
            LogMealTool.Args(
                meal_type = MealType.LUNCH,
                items = listOf(LogMealTool.MealItemArg(name = "牛肉面", calories = 500.0)),
                consumed_items = listOf(
                    LogMealTool.ConsumedItemArg(name = "  ", quantity = 2.0),
                    LogMealTool.ConsumedItemArg(name = "鸡蛋", quantity = 0.0),
                ),
            )
        )

        coVerify(exactly = 0) { inventoryRepo.adjustStock(any(), any(), any(), any()) }
    }

    // --- log_purchase ---

    @Test
    fun logPurchase_savesDraftAndAdjustsInventory() = runTest {
        val purchaseRepo = mockk<PurchaseRepository>()
        val draftSlot = slot<PurchaseRecordDraft>()
        coEvery { purchaseRepo.savePurchase(capture(draftSlot), any()) } returns "purchase-1"
        val tool = LogPurchaseTool(purchaseRepo, nowProvider = { 2_000L })

        val result = tool.execute(
            LogPurchaseTool.Args(
                items = listOf(
                    LogPurchaseTool.PurchaseItemArg(
                        name = "鸡蛋",
                        brand = "德清源",
                        quantity = 15.0,
                        unit = "个",
                        unit_price = 1.6,
                    ),
                ),
                store_name = " 永辉超市 ",
            )
        )

        assertTrue(result.contains(""""status":"ok""""))
        assertTrue(result.contains(""""inventoryAdjusted":true"""))
        val draft = draftSlot.captured
        assertEquals("永辉超市", draft.storeName)
        assertEquals("德清源", draft.items.single().brand)
        assertEquals(24.0, draft.items.sumOf { (it.unitPrice ?: 0.0) * it.quantity }, 0.001)
        assertEquals(2_000L, draft.purchasedAt)
        coVerify(exactly = 1) { purchaseRepo.savePurchase(any(), adjustInventory = true) }
    }

    @Test
    fun logPurchase_inventoryAdjustDisabledPropagates() = runTest {
        val purchaseRepo = mockk<PurchaseRepository>()
        coEvery { purchaseRepo.savePurchase(any(), any()) } returns "purchase-2"
        val tool = LogPurchaseTool(purchaseRepo, nowProvider = { 1L })

        tool.execute(
            LogPurchaseTool.Args(
                items = listOf(LogPurchaseTool.PurchaseItemArg(name = "牛奶", quantity = 2.0, unit = "盒")),
                adjust_inventory = false,
            )
        )

        coVerify(exactly = 1) { purchaseRepo.savePurchase(any(), adjustInventory = false) }
    }

    @Test
    fun logPurchase_invalidItemsReturnsEnvelopeError() = runTest {
        val tool = LogPurchaseTool(mockk(), nowProvider = { 1L })

        val result = tool.execute(
            LogPurchaseTool.Args(
                items = listOf(
                    LogPurchaseTool.PurchaseItemArg(name = "", quantity = 5.0),
                    LogPurchaseTool.PurchaseItemArg(name = "零数量", quantity = 0.0),
                )
            )
        )

        assertTrue(result.contains("purchase_items_empty"))
    }

    // --- query_meal_log ---

    @Test
    fun queryMealLog_returnsSerializedRecords() = runTest {
        val mealRepo = mockk<MealRecordRepository>()
        coEvery { mealRepo.getRecordsBetween(any(), any()) } returns listOf(
            makeMealWithItems(id = "r1", calories = 830.0, protein = 34.0),
        )
        val tool = QueryMealLogTool(mealRepo)

        val result = tool.execute(QueryMealLogTool.Args(days = 7))

        assertTrue(result.contains(""""count":1"""))
        assertTrue(result.contains(""""totalCalories":830.0"""))
        assertTrue(result.contains("牛肉粉"))
    }

    @Test
    fun queryMealLog_daysClampedToMax() = runTest {
        val mealRepo = mockk<MealRecordRepository>()
        coEvery { mealRepo.getRecordsBetween(any(), any()) } returns emptyList()
        val tool = QueryMealLogTool(mealRepo)

        val result = tool.execute(QueryMealLogTool.Args(days = 999))

        assertTrue(result.contains(""""days":30"""))
        assertTrue(result.contains(""""count":0"""))
    }

    // --- query_inventory ---

    @Test
    fun queryInventory_filtersByNameAndFlagsLowStock() = runTest {
        val inventoryRepo = mockk<InventoryRepository>()
        coEvery { inventoryRepo.observeInventory() } returns flowOf(
            listOf(
                makeItem(id = "eggs", name = "鸡蛋", quantity = 2.0, threshold = 3.0),
                makeItem(id = "milk", name = "牛奶", quantity = 6.0, threshold = 2.0),
            )
        )
        val tool = QueryInventoryTool(inventoryRepo)

        val result = tool.execute(QueryInventoryTool.Args(name_filter = "鸡蛋"))

        assertTrue(result.contains(""""count":1"""))
        assertTrue(result.contains(""""lowCount":1"""))
        assertTrue(result.contains(""""isLow":true"""))
        assertTrue(result.contains("鸡蛋"))
    }

    @Test
    fun queryInventory_noFilterReturnsAll() = runTest {
        val inventoryRepo = mockk<InventoryRepository>()
        coEvery { inventoryRepo.observeInventory() } returns flowOf(
            listOf(
                makeItem(id = "eggs", name = "鸡蛋", quantity = 15.0, threshold = null),
                makeItem(id = "milk", name = "牛奶", quantity = 2.0, threshold = 1.0),
            )
        )
        val tool = QueryInventoryTool(inventoryRepo)

        val result = tool.execute(QueryInventoryTool.Args(name_filter = null))

        assertTrue(result.contains(""""count":2"""))
    }

    private fun makeItem(
        id: String = "item-1",
        name: String = "鸡蛋",
        quantity: Double,
        threshold: Double? = null,
    ) = InventoryItemEntity(
        id = id,
        name = name,
        brand = "德清源",
        unit = "个",
        currentQuantity = quantity,
        lowStockThreshold = threshold,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun makeMealWithItems(id: String, calories: Double, protein: Double) =
        com.xiaoqi.companion.data.db.dao.MealRecordWithItems(
            record = com.xiaoqi.companion.data.db.entity.MealRecordEntity(
                id = id,
                createdAt = 1L,
                updatedAt = 1L,
                mealType = MealType.LUNCH,
                source = com.xiaoqi.companion.data.db.converter.RecordSource.PHOTO,
                totalCalories = calories,
                totalProteinG = protein,
                totalCarbsG = 95.0,
                totalFatG = 36.0,
                confidence = 0.85,
            ),
            items = listOf(
                com.xiaoqi.companion.data.db.entity.MealItemEntity(
                    recordId = id,
                    name = "牛肉粉",
                    portionText = "1碗",
                    estCalories = calories,
                    estProteinG = protein,
                    estCarbsG = 95.0,
                    estFatG = 36.0,
                )
            ),
        )
}
