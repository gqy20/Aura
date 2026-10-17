package com.xiaoqi.companion.data.db.dao

import app.cash.turbine.test
import com.xiaoqi.companion.data.db.BaseDaoTest
import com.xiaoqi.companion.data.db.converter.MealType
import com.xiaoqi.companion.data.db.converter.RecordSource
import com.xiaoqi.companion.data.db.entity.MealItemEntity
import com.xiaoqi.companion.data.db.entity.MealRecordEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MealRecordDaoTest : BaseDaoTest() {

    private lateinit var dao: MealRecordDao

    override fun initDaos() {
        dao = db.mealRecordDao()
    }

    @Test
    fun insertRecordWithItems_observeRecentReturnsRelationInDescOrder() = runTest {
        dao.insertRecordWithItems(makeRecord(id = "r1", createdAt = 1_000), itemsFor("r1"))
        dao.insertRecordWithItems(makeRecord(id = "r2", createdAt = 2_000), itemsFor("r2"))

        dao.observeRecent(10).test {
            val records = awaitItem()
            assertEquals(listOf("r2", "r1"), records.map { it.record.id })
            val first = records.first { it.record.id == "r1" }
            assertEquals(2, first.items.size)
            assertEquals("Beef Noodle Soup", first.items[0].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeTotalsBetween_sumsOnlyRecordsInsideWindow() = runTest {
        dao.insertRecordWithItems(makeRecord(id = "in1", createdAt = 1_000, totalCalories = 450.0), itemsFor("in1"))
        dao.insertRecordWithItems(makeRecord(id = "out", createdAt = 9_000, totalCalories = 999.0), itemsFor("out"))

        dao.observeTotalsBetween(0, 5_000).test {
            val totals = awaitItem()
            assertEquals(450.0, totals.calories, 0.001)
            assertEquals(25.0, totals.proteinG, 0.001)
            assertEquals(1, totals.recordCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeTotalsBetween_emptyWindowReturnsZeros() = runTest {
        dao.observeTotalsBetween(0, 5_000).test {
            val totals = awaitItem()
            assertEquals(0.0, totals.calories, 0.001)
            assertEquals(0, totals.recordCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getRecordsBetween_returnsAscendingWithItems() = runTest {
        dao.insertRecordWithItems(makeRecord(id = "r2", createdAt = 2_000), itemsFor("r2"))
        dao.insertRecordWithItems(makeRecord(id = "r1", createdAt = 1_000), itemsFor("r1"))

        val records = dao.getRecordsBetween(0, 10_000)
        assertEquals(listOf("r1", "r2"), records.map { it.record.id })
        assertTrue(records.all { it.items.isNotEmpty() })
    }

    @Test
    fun deleteRecordWithItems_cascadesItemRows() = runTest {
        dao.insertRecordWithItems(makeRecord(id = "r1"), itemsFor("r1"))

        dao.deleteRecordWithItems("r1")

        assertNull(dao.getRecord("r1"))
        assertTrue(dao.getRecordsBetween(0, 100_000).isEmpty())
    }

    private fun makeRecord(id: String, createdAt: Long = 1_000, totalCalories: Double = 530.0) =
        MealRecordEntity(
            id = id,
            createdAt = createdAt,
            updatedAt = createdAt,
            mealType = MealType.LUNCH,
            source = RecordSource.PHOTO,
            totalCalories = totalCalories,
            totalProteinG = 25.0,
            totalCarbsG = 50.0,
            totalFatG = 10.0,
            confidence = 0.9,
        )

    private fun itemsFor(recordId: String) = listOf(
        MealItemEntity(
            recordId = recordId,
            name = "Beef Noodle Soup",
            portionText = "1 bowl",
            estCalories = 450.0,
            estProteinG = 25.0,
            estCarbsG = 50.0,
            estFatG = 10.0,
        ),
        MealItemEntity(
            recordId = recordId,
            name = "Braised Egg",
            portionText = "1 egg",
            estCalories = 80.0,
            estProteinG = 6.0,
            estCarbsG = 1.0,
            estFatG = 5.0,
            isEstimate = false,
        ),
    )
}
