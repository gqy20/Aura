package com.xiaoqi.companion.data.repository

import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.core.record.MealRecordDraft
import com.xiaoqi.companion.data.db.dao.CalibratedMealItemRow
import com.xiaoqi.companion.data.db.dao.MealRecordDao
import com.xiaoqi.companion.data.db.dao.MealRecordWithItems
import com.xiaoqi.companion.data.db.dao.MealTotalsRow
import com.xiaoqi.companion.data.db.entity.MealItemEntity
import com.xiaoqi.companion.data.db.entity.MealRecordEntity
import com.xiaoqi.companion.data.record.RecordImageStore
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

interface MealRecordRepository {
    fun observeRecentRecords(limit: Int = 50): Flow<List<MealRecordWithItems>>
    fun observeTotalsBetween(from: Long, to: Long): Flow<MealTotalsRow>
    suspend fun saveMeal(draft: MealRecordDraft): String
    suspend fun deleteMeal(recordId: String)
    suspend fun getRecordsBetween(from: Long, to: Long): List<MealRecordWithItems>

    /** 用户修正过数值的条目(sinceAt 以来),提取器用作个人化校准锚点。 */
    suspend fun getRecentCalibratedItems(sinceAt: Long, limit: Int): List<CalibratedMealItemRow>
}

class MealRecordRepositoryImpl @Inject constructor(
    private val mealRecordDao: MealRecordDao,
    private val imageStore: RecordImageStore,
) : MealRecordRepository {

    override fun observeRecentRecords(limit: Int): Flow<List<MealRecordWithItems>> =
        mealRecordDao.observeRecent(limit)

    override fun observeTotalsBetween(from: Long, to: Long): Flow<MealTotalsRow> =
        mealRecordDao.observeTotalsBetween(from, to)

    override suspend fun saveMeal(draft: MealRecordDraft): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val totalCalories = draft.items.sumOf { it.calories }
        mealRecordDao.insertRecordWithItems(
            MealRecordEntity(
                id = id,
                createdAt = draft.createdAt,
                updatedAt = System.currentTimeMillis(),
                mealType = draft.mealType,
                source = draft.source,
                totalCalories = totalCalories,
                totalProteinG = draft.items.sumOf { it.proteinG },
                totalCarbsG = draft.items.sumOf { it.carbsG },
                totalFatG = draft.items.sumOf { it.fatG },
                confidence = draft.confidence,
                imageLocalPath = draft.imageLocalPath,
                rawNote = draft.rawNote,
            ),
            draft.items.map { item ->
                MealItemEntity(
                    recordId = id,
                    name = item.name,
                    portionText = item.portionText,
                    estCalories = item.calories,
                    estProteinG = item.proteinG,
                    estCarbsG = item.carbsG,
                    estFatG = item.fatG,
                    isEstimate = item.isEstimate,
                )
            },
        )
        AppLogger.info(
            LogTags.Record,
            "meal_record_saved",
            "recordId" to id,
            "mealType" to draft.mealType.name,
            "itemCount" to draft.items.size,
            "totalCalories" to totalCalories,
        )
        id
    }

    override suspend fun deleteMeal(recordId: String) = withContext(Dispatchers.IO) {
        val record = mealRecordDao.getRecord(recordId)
        mealRecordDao.deleteRecordWithItems(recordId)
        record?.imageLocalPath?.let { imageStore.delete(it) }
        AppLogger.info(LogTags.Record, "meal_record_deleted", "recordId" to recordId)
    }

    override suspend fun getRecordsBetween(from: Long, to: Long): List<MealRecordWithItems> =
        withContext(Dispatchers.IO) { mealRecordDao.getRecordsBetween(from, to) }

    override suspend fun getRecentCalibratedItems(sinceAt: Long, limit: Int): List<CalibratedMealItemRow> =
        withContext(Dispatchers.IO) { mealRecordDao.getCalibratedItems(sinceAt, limit) }
}
