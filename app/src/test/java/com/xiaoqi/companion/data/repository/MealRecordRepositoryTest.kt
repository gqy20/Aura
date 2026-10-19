package com.xiaoqi.companion.data.repository

import com.xiaoqi.companion.core.record.MealItemDraft
import com.xiaoqi.companion.core.record.MealRecordDraft
import com.xiaoqi.companion.data.db.converter.MealType
import com.xiaoqi.companion.data.db.converter.RecordSource
import com.xiaoqi.companion.data.db.dao.MealRecordDao
import com.xiaoqi.companion.data.db.entity.MealRecordEntity
import com.xiaoqi.companion.data.record.RecordImageStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MealRecordRepositoryTest {

    @Test
    fun saveMeal_aggregatesTotalsAndMapsItems() = runTest {
        val dao = mockk<MealRecordDao>()
        val recordSlot = slot<MealRecordEntity>()
        coEvery { dao.insertRecordWithItems(capture(recordSlot), any()) } just runs
        val repository = MealRecordRepositoryImpl(dao, mockk(relaxed = true))

        repository.saveMeal(
            MealRecordDraft(
                mealType = MealType.LUNCH,
                source = RecordSource.PHOTO,
                items = listOf(
                    MealItemDraft("Beef Noodle Soup", "1 bowl", 450.0, 25.0, 50.0, 10.0),
                    MealItemDraft("Braised Egg", "1 egg", 80.0, 6.0, 1.0, 5.0, isEstimate = false),
                ),
                confidence = 0.9,
            )
        )

        val record = recordSlot.captured
        assertEquals(530.0, record.totalCalories, 0.001)
        assertEquals(31.0, record.totalProteinG, 0.001)
        assertEquals(51.0, record.totalCarbsG, 0.001)
        assertEquals(15.0, record.totalFatG, 0.001)
        assertEquals(MealType.LUNCH, record.mealType)
    }

    @Test
    fun deleteMeal_cascadesRecordAndImageFile() = runTest {
        val dao = mockk<MealRecordDao>()
        val imageStore = mockk<RecordImageStore>()
        coEvery { dao.getRecord("r1") } returns MealRecordEntity(
            id = "r1", createdAt = 1L, updatedAt = 1L,
            mealType = MealType.LUNCH, source = RecordSource.PHOTO,
            totalCalories = 0.0, totalProteinG = 0.0, totalCarbsG = 0.0, totalFatG = 0.0,
            confidence = 0.0, imageLocalPath = "/data/record_images/a.jpg",
        )
        coEvery { dao.deleteRecordWithItems("r1") } just runs
        coEvery { imageStore.delete("/data/record_images/a.jpg") } returns true
        val repository = MealRecordRepositoryImpl(dao, imageStore)

        repository.deleteMeal("r1")

        coVerify(exactly = 1) { dao.deleteRecordWithItems("r1") }
        coVerify(exactly = 1) { imageStore.delete("/data/record_images/a.jpg") }
    }

    @Test
    fun deleteMeal_deletesEveryImageInMultiPhotoPath() = runTest {
        val dao = mockk<MealRecordDao>()
        val imageStore = mockk<RecordImageStore>()
        coEvery { dao.getRecord("r1") } returns MealRecordEntity(
            id = "r1", createdAt = 1L, updatedAt = 1L,
            mealType = MealType.LUNCH, source = RecordSource.PHOTO,
            totalCalories = 0.0, totalProteinG = 0.0, totalCarbsG = 0.0, totalFatG = 0.0,
            confidence = 0.0, imageLocalPath = "/data/record_images/a.jpg,/data/record_images/b.jpg",
        )
        coEvery { dao.deleteRecordWithItems("r1") } just runs
        coEvery { imageStore.delete(any()) } returns true
        val repository = MealRecordRepositoryImpl(dao, imageStore)

        repository.deleteMeal("r1")

        coVerify(exactly = 1) { imageStore.delete("/data/record_images/a.jpg") }
        coVerify(exactly = 1) { imageStore.delete("/data/record_images/b.jpg") }
    }

    @Test
    fun deleteMeal_withoutImageSkipsImageDeletion() = runTest {
        val dao = mockk<MealRecordDao>()
        val imageStore = mockk<RecordImageStore>()
        coEvery { dao.getRecord("r1") } returns MealRecordEntity(
            id = "r1", createdAt = 1L, updatedAt = 1L,
            mealType = MealType.LUNCH, source = RecordSource.MANUAL,
            totalCalories = 0.0, totalProteinG = 0.0, totalCarbsG = 0.0, totalFatG = 0.0,
            confidence = 0.0,
        )
        coEvery { dao.deleteRecordWithItems("r1") } just runs
        val repository = MealRecordRepositoryImpl(dao, imageStore)

        repository.deleteMeal("r1")

        coVerify(exactly = 0) { imageStore.delete(any()) }
    }
}
