package com.xiaoqi.companion.data.repository

import com.xiaoqi.companion.core.record.PurchaseItemDraft
import com.xiaoqi.companion.core.record.PurchaseRecordDraft
import com.xiaoqi.companion.data.db.converter.RecordSource
import com.xiaoqi.companion.data.db.dao.PurchaseDao
import com.xiaoqi.companion.data.db.entity.PurchaseItemEntity
import com.xiaoqi.companion.data.db.entity.PurchaseRecordEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PurchaseRepositoryTest {

    @Test
    fun savePurchase_sumsUnitPricesWhenTotalCostAbsent() = runTest {
        val dao = mockk<PurchaseDao>()
        val recordSlot = slot<PurchaseRecordEntity>()
        coEvery { dao.insertPurchaseWithItems(capture(recordSlot), any()) } just runs
        val repository = PurchaseRepositoryImpl(dao, mockk(relaxed = true))

        repository.savePurchase(
            PurchaseRecordDraft(
                items = listOf(
                    PurchaseItemDraft("鸡蛋", "德清源", 15.0, "个", 1.6),
                    PurchaseItemDraft("牛奶", null, 2.0, "盒", 12.5),
                ),
                source = RecordSource.PHOTO,
            ),
            adjustInventory = false,
        )

        assertEquals(24.0 + 25.0, recordSlot.captured.totalCost!!, 0.001)
    }

    @Test
    fun savePurchase_explicitTotalCostWins() = runTest {
        val dao = mockk<PurchaseDao>()
        val recordSlot = slot<PurchaseRecordEntity>()
        coEvery { dao.insertPurchaseWithItems(capture(recordSlot), any()) } just runs
        val repository = PurchaseRepositoryImpl(dao, mockk(relaxed = true))

        repository.savePurchase(
            PurchaseRecordDraft(
                items = listOf(PurchaseItemDraft("鸡蛋", null, 15.0, "个", 1.6)),
                source = RecordSource.PHOTO,
                totalCost = 30.0,
            ),
            adjustInventory = false,
        )

        assertEquals(30.0, recordSlot.captured.totalCost!!, 0.001)
    }

    @Test
    fun savePurchase_adjustInventoryPropagatesEveryItem() = runTest {
        val dao = mockk<PurchaseDao>()
        val inventoryRepository = mockk<InventoryRepository>(relaxed = true)
        coEvery { dao.insertPurchaseWithItems(any(), any()) } just runs
        val repository = PurchaseRepositoryImpl(dao, inventoryRepository)

        repository.savePurchase(
            PurchaseRecordDraft(
                items = listOf(
                    PurchaseItemDraft("鸡蛋", "德清源", 15.0, "个", 1.6),
                    PurchaseItemDraft("牛奶", null, 2.0, "盒", 12.5),
                ),
                source = RecordSource.PHOTO,
            ),
            adjustInventory = true,
        )

        coVerify(exactly = 1) { inventoryRepository.adjustStock("鸡蛋", "德清源", "个", 15.0) }
        coVerify(exactly = 1) { inventoryRepository.adjustStock("牛奶", null, "盒", 2.0) }
    }

    @Test
    fun savePurchase_withoutInventoryAdjustmentSkipsStock() = runTest {
        val dao = mockk<PurchaseDao>()
        val inventoryRepository = mockk<InventoryRepository>(relaxed = true)
        coEvery { dao.insertPurchaseWithItems(any(), any()) } just runs
        val repository = PurchaseRepositoryImpl(dao, inventoryRepository)

        repository.savePurchase(
            PurchaseRecordDraft(
                items = listOf(PurchaseItemDraft("鸡蛋", null, 15.0, "个")),
                source = RecordSource.PHOTO,
            ),
            adjustInventory = false,
        )

        coVerify(exactly = 0) { inventoryRepository.adjustStock(any(), any(), any(), any()) }
    }

    @Test
    fun deletePurchase_cascadesViaDao() = runTest {
        val dao = mockk<PurchaseDao>()
        coEvery { dao.deletePurchaseWithItems("p1") } just runs
        val repository = PurchaseRepositoryImpl(dao, mockk(relaxed = true))

        repository.deletePurchase("p1")

        coVerify(exactly = 1) { dao.deletePurchaseWithItems("p1") }
    }

    @Test
    fun savePurchase_trimsStoreNameAndBrand() = runTest {
        val dao = mockk<PurchaseDao>()
        val recordSlot = slot<PurchaseRecordEntity>()
        val itemsSlot = slot<List<PurchaseItemEntity>>()
        coEvery { dao.insertPurchaseWithItems(capture(recordSlot), capture(itemsSlot)) } just runs
        val repository = PurchaseRepositoryImpl(dao, mockk(relaxed = true))

        repository.savePurchase(
            PurchaseRecordDraft(
                items = listOf(PurchaseItemDraft("鸡蛋", " 德清源 ", 15.0, "个")),
                source = RecordSource.PHOTO,
                storeName = "  永辉超市 ",
            ),
            adjustInventory = false,
        )

        assertEquals("永辉超市", recordSlot.captured.storeName)
        assertEquals("德清源", itemsSlot.captured.single().brand)
    }
}
