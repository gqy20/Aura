package com.xiaoqi.companion.data.repository

import com.xiaoqi.companion.data.db.dao.InventoryDao
import com.xiaoqi.companion.data.db.entity.InventoryItemEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryRepositoryTest {

    @Test
    fun adjustStock_newPositiveDeltaCreatesItemWithNormalizedIdentity() = runTest {
        val dao = mockk<InventoryDao>()
        val entitySlot = slot<InventoryItemEntity>()
        coEvery { dao.getById("鸡蛋|德清源") } returns null
        coEvery { dao.upsert(capture(entitySlot)) } just runs
        val repository = InventoryRepositoryImpl(dao)

        val result = repository.adjustStock("  鸡蛋 ", " 德清源 ", "个", 15.0)

        assertNotNull(result)
        assertEquals("鸡蛋|德清源", entitySlot.captured.id)
        assertEquals("鸡蛋", entitySlot.captured.name)
        assertEquals("德清源", entitySlot.captured.brand)
        assertEquals(15.0, entitySlot.captured.currentQuantity, 0.001)
    }

    @Test
    fun adjustStock_newNonPositiveDeltaReturnsNullWithoutInsert() = runTest {
        val dao = mockk<InventoryDao>()
        coEvery { dao.getById(any()) } returns null
        val repository = InventoryRepositoryImpl(dao)

        assertNull(repository.adjustStock("鸡蛋", null, "个", -2.0))

        coVerify(exactly = 0) { dao.upsert(any()) }
    }

    @Test
    fun adjustStock_existingItemAccumulatesViaDao() = runTest {
        val dao = mockk<InventoryDao>()
        coEvery { dao.getById("鸡蛋|") } returnsMany listOf(
            makeItem("鸡蛋|", 15.0),
            makeItem("鸡蛋|", 40.0),
        )
        coEvery { dao.adjustQuantity("鸡蛋|", 25.0, any()) } just runs
        val repository = InventoryRepositoryImpl(dao)

        val result = repository.adjustStock("鸡蛋", null, "板", 25.0)

        assertEquals(40.0, result!!.currentQuantity, 0.001)
    }

    @Test
    fun adjustStock_normalizesNameAndBrandCase() = runTest {
        val dao = mockk<InventoryDao>()
        coEvery { dao.getById("milk|") } returns null
        coEvery { dao.upsert(any()) } just runs
        val repository = InventoryRepositoryImpl(dao)

        repository.adjustStock("Milk", null, "盒", 2.0)

        coVerify { dao.getById("milk|") }
    }

    @Test
    fun setLowStockThreshold_existingItemUpdates() = runTest {
        val dao = mockk<InventoryDao>()
        coEvery { dao.getById("鸡蛋|") } returns makeItem("鸡蛋|", 15.0)
        coEvery { dao.setLowStockThreshold("鸡蛋|", 3.0, any()) } just runs
        val repository = InventoryRepositoryImpl(dao)

        assertTrue(repository.setLowStockThreshold("鸡蛋", null, 3.0))
    }

    @Test
    fun setLowStockThreshold_missingItemReturnsFalse() = runTest {
        val dao = mockk<InventoryDao>()
        coEvery { dao.getById(any()) } returns null
        val repository = InventoryRepositoryImpl(dao)

        assertFalse(repository.setLowStockThreshold("鸡蛋", null, 3.0))
    }

    private fun makeItem(id: String, quantity: Double) = InventoryItemEntity(
        id = id,
        name = "鸡蛋",
        brand = null,
        unit = "个",
        currentQuantity = quantity,
        createdAt = 1L,
        updatedAt = 1L,
    )
}
