package com.xiaoqi.companion.feature.record

import com.xiaoqi.companion.data.db.converter.MealType
import com.xiaoqi.companion.data.db.converter.RecordSource
import com.xiaoqi.companion.data.db.dao.MealRecordWithItems
import com.xiaoqi.companion.data.db.dao.MealTotalsRow
import com.xiaoqi.companion.data.db.dao.PurchaseRecordWithItems
import com.xiaoqi.companion.data.db.entity.MealItemEntity
import com.xiaoqi.companion.data.db.entity.MealRecordEntity
import com.xiaoqi.companion.data.db.entity.PurchaseItemEntity
import com.xiaoqi.companion.data.db.entity.PurchaseRecordEntity
import com.xiaoqi.companion.data.repository.MealRecordRepository
import com.xiaoqi.companion.data.repository.PurchaseRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordHistoryViewModelTest {

    private lateinit var mealRepository: MealRecordRepository
    private lateinit var purchaseRepository: PurchaseRepository
    private val mealsFlow = MutableStateFlow<List<MealRecordWithItems>>(emptyList())
    private val purchasesFlow = MutableStateFlow<List<PurchaseRecordWithItems>>(emptyList())
    private lateinit var viewModel: RecordHistoryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mealRepository = mockk {
            every { observeRecentRecords(any()) } returns mealsFlow
        }
        purchaseRepository = mockk {
            every { observeRecentPurchases(any()) } returns purchasesFlow
        }
        viewModel = RecordHistoryViewModel(mealRepository, purchaseRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun groupsEntriesByDayMostRecentFirst() = runTest {
        // 正午锚点:'几小时前'永不跨天(午夜附近跑测试时 now-Nh 落到昨天)
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now(zone).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val yesterday = today - 86_400_000L
        mealsFlow.value = listOf(
            mealRecord(id = "m1", time = today - 3_600_000L),
            mealRecord(id = "m2", time = yesterday),
        )
        purchasesFlow.value = listOf(
            purchaseRecord(id = "p1", time = today - 7_200_000L),
        )

        val state = viewModel.state.value
        assertEquals(2, state.days.size)
        // 今天的两条在第一组,且按时间倒序(p1 先于 m1)
        assertEquals(2, state.days[0].entries.size)
        // m1(1小时前)比 p1(2小时前)更近,倒序在前
        assertEquals("m1", state.days[0].entries[0].id)
        assertEquals("p1", state.days[0].entries[1].id)
        assertEquals(1, state.days[1].entries.size)
        assertEquals("m2", state.days[1].entries[0].id)
    }

    @Test
    fun emptyDaysYieldsEmptyState() = runTest {
        assertEquals(0, viewModel.state.value.days.size)
        assertNull(viewModel.state.value.expandedEntryId)
    }

    @Test
    fun toggleExpand_switchesAndCancels() = runTest {
        mealsFlow.value = listOf(mealRecord(id = "m1"))
        viewModel.toggleExpand("m1")
        assertEquals("m1", viewModel.state.value.expandedEntryId)
        viewModel.toggleExpand("m1")
        assertNull(viewModel.state.value.expandedEntryId)
    }

    private fun mealRecord(id: String, time: Long = System.currentTimeMillis()) = MealRecordWithItems(
        record = MealRecordEntity(
            id = id,
            createdAt = time,
            updatedAt = time,
            mealType = MealType.LUNCH,
            source = RecordSource.PHOTO,
            totalCalories = 700.0,
            totalProteinG = 30.0,
            totalCarbsG = 75.0,
            totalFatG = 28.0,
            confidence = 0.85,
        ),
        items = listOf(
            MealItemEntity(
                recordId = id,
                name = "牛肉面",
                portionText = "1碗",
                estCalories = 550.0,
                estProteinG = 25.0,
                estCarbsG = 60.0,
                estFatG = 12.0,
            ),
        ),
    )

    private fun purchaseRecord(id: String, time: Long = System.currentTimeMillis()) = PurchaseRecordWithItems(
        record = PurchaseRecordEntity(
            id = id,
            purchasedAt = time,
            updatedAt = time,
            source = RecordSource.PHOTO,
            storeName = "永辉超市",
        ),
        items = listOf(
            PurchaseItemEntity(
                recordId = id,
                name = "鸡蛋",
                brand = "德清源",
                quantity = 15.0,
                unit = "个",
            ),
        ),
    )
}
