package com.xiaoqi.companion.data.db.dao

import app.cash.turbine.test
import com.xiaoqi.companion.data.db.BaseDaoTest
import com.xiaoqi.companion.data.db.entity.InventoryItemEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class InventoryDaoTest : BaseDaoTest() {

    private lateinit var dao: InventoryDao

    override fun initDaos() {
        dao = db.inventoryDao()
    }

    @Test
    fun upsert_thenObserveAllOrderedByUpdatedAtDesc() = runTest {
        dao.upsert(makeItem(id = "eggs", updatedAt = 1_000, quantity = 15.0))
        dao.upsert(makeItem(id = "milk", updatedAt = 2_000, quantity = 2.0))

        dao.observeAll().test {
            val items = awaitItem()
            assertEquals(listOf("milk", "eggs"), items.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun adjustQuantity_accumulatesDelta() = runTest {
        dao.upsert(makeItem(id = "eggs", quantity = 15.0))

        dao.adjustQuantity("eggs", 10.0, 2_000)

        assertEquals(25.0, dao.getById("eggs")!!.currentQuantity, 0.001)
    }

    @Test
    fun adjustQuantity_clampsAtZero() = runTest {
        dao.upsert(makeItem(id = "eggs", quantity = 15.0))

        dao.adjustQuantity("eggs", -20.0, 2_000)

        assertEquals(0.0, dao.getById("eggs")!!.currentQuantity, 0.001)
    }

    @Test
    fun adjustQuantity_unknownIdIsNoOp() = runTest {
        dao.adjustQuantity("missing", 5.0, 2_000)

        assertNull(dao.getById("missing"))
    }

    @Test
    fun setLowStockThreshold_updatesExistingItemOnly() = runTest {
        dao.upsert(makeItem(id = "eggs", quantity = 15.0))
        dao.setLowStockThreshold("missing", 3.0, 2_000)

        assertNull(dao.getById("missing"))

        dao.setLowStockThreshold("eggs", 3.0, 3_000)

        val item = dao.getById("eggs")!!
        assertNotNull(item.lowStockThreshold)
        assertEquals(3.0, item.lowStockThreshold!!, 0.001)
    }

    @Test
    fun deleteById_removesItem() = runTest {
        dao.upsert(makeItem(id = "eggs", quantity = 15.0))

        dao.deleteById("eggs")

        assertNull(dao.getById("eggs"))
    }

    private fun makeItem(id: String, quantity: Double, updatedAt: Long = 1_000) = InventoryItemEntity(
        id = id,
        name = "鸡蛋",
        brand = "德清源",
        unit = "个",
        currentQuantity = quantity,
        createdAt = 1_000,
        updatedAt = updatedAt,
    )
}
