package com.xiaoqi.companion.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.xiaoqi.companion.data.db.entity.MealItemEntity
import com.xiaoqi.companion.data.db.entity.MealRecordEntity
import kotlinx.coroutines.flow.Flow

data class MealRecordWithItems(
    @Embedded val record: MealRecordEntity,
    @Relation(parentColumn = "id", entityColumn = "recordId")
    val items: List<MealItemEntity>,
)

data class MealTotalsRow(
    val calories: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val recordCount: Int,
)

@Dao
abstract class MealRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertRecord(entity: MealRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertItems(entities: List<MealItemEntity>)

    @Transaction
    open suspend fun insertRecordWithItems(record: MealRecordEntity, items: List<MealItemEntity>) {
        insertRecord(record)
        insertItems(items)
    }

    @Transaction
    @Query("SELECT * FROM meal_records ORDER BY createdAt DESC LIMIT :limit")
    abstract fun observeRecent(limit: Int): Flow<List<MealRecordWithItems>>

    @Query("SELECT * FROM meal_records WHERE id = :id")
    abstract suspend fun getRecord(id: String): MealRecordEntity?

    @Transaction
    @Query("SELECT * FROM meal_records WHERE createdAt >= :from AND createdAt < :to ORDER BY createdAt ASC")
    abstract suspend fun getRecordsBetween(from: Long, to: Long): List<MealRecordWithItems>

    @Query(
        """
        SELECT COALESCE(SUM(totalCalories), 0.0) AS calories,
               COALESCE(SUM(totalProteinG), 0.0) AS proteinG,
               COALESCE(SUM(totalCarbsG), 0.0) AS carbsG,
               COALESCE(SUM(totalFatG), 0.0) AS fatG,
               COUNT(*) AS recordCount
        FROM meal_records
        WHERE createdAt >= :from AND createdAt < :to
        """
    )
    abstract fun observeTotalsBetween(from: Long, to: Long): Flow<MealTotalsRow>

    @Query("DELETE FROM meal_records WHERE id = :id")
    abstract suspend fun deleteRecord(id: String)

    @Query("DELETE FROM meal_items WHERE recordId = :recordId")
    abstract suspend fun deleteItemsByRecordId(recordId: String)

    // 无外键约束，级联删除在这里手工完成
    @Transaction
    open suspend fun deleteRecordWithItems(id: String) {
        deleteItemsByRecordId(id)
        deleteRecord(id)
    }
}
