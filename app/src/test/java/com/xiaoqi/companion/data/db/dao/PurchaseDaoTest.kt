package com.xiaoqi.companion.data.db.dao

import app.cash.turbine.test
import com.xiaoqi.companion.data.db.BaseDaoTest
import com.xiaoqi.companion.data.db.converter.RecordSource
import com.xiaoqi.companion.data.db.entity.PurchaseItemEntity
import com.xiaoqi.companion.data.db.entity.PurchaseRecordEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseDaoTest : BaseDaoTest() {

    private lateinit var dao: PurchaseDao

    override fun initDaos() {
        dao = db.purchaseDao()
    }

    @Test
    fun insertPurchaseWithItems_observeRecentReturnsRelation() = runTest {
        dao.insertPurchaseWithItems(makeRecord(id = "p1", purchasedAt = 1_000), itemsFor("p1"))
        dao.insertPurchaseWithItems(makeRecord(id = "p2", purchasedAt = 2_000), itemsFor("p2"))

        dao.observeRecent(10).test {
            val records = awaitItem()
            assertEquals(listOf("p2", "p1"), records.map { it.record.id })
            val first = records.first { it.record.id == "p1" }
            assertEquals(1, first.items.size)
            assertEquals("鸡蛋", first.items[0].name)
            assertEquals("德清源", first.items[0].brand)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deletePurchaseWithItems_cascadesItemRows() = runTest {
        dao.insertPurchaseWithItems(makeRecord(id = "p1"), itemsFor("p1"))

        dao.deletePurchaseWithItems("p1")

        dao.observeRecent(10).test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun makeRecord(id: String, purchasedAt: Long = 1_000) = PurchaseRecordEntity(
        id = id,
        purchasedAt = purchasedAt,
        updatedAt = purchasedAt,
        source = RecordSource.PHOTO,
        storeName = "永辉超市",
        totalCost = 24.0,
    )

    private fun itemsFor(recordId: String) = listOf(
        PurchaseItemEntity(
            recordId = recordId,
            name = "鸡蛋",
            brand = "德清源",
            quantity = 15.0,
            unit = "个",
            unitPrice = 1.6,
        ),
    )
}
