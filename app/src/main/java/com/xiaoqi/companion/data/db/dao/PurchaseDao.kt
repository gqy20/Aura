package com.xiaoqi.companion.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.xiaoqi.companion.data.db.entity.PurchaseItemEntity
import com.xiaoqi.companion.data.db.entity.PurchaseRecordEntity
import kotlinx.coroutines.flow.Flow

data class PurchaseRecordWithItems(
    @Embedded val record: PurchaseRecordEntity,
    @Relation(parentColumn = "id", entityColumn = "recordId")
    val items: List<PurchaseItemEntity>,
)

@Dao
abstract class PurchaseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertRecord(entity: PurchaseRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertItems(entities: List<PurchaseItemEntity>)

    @Transaction
    open suspend fun insertPurchaseWithItems(record: PurchaseRecordEntity, items: List<PurchaseItemEntity>) {
        insertRecord(record)
        insertItems(items)
    }

    @Transaction
    @Query("SELECT * FROM purchase_records ORDER BY purchasedAt DESC LIMIT :limit")
    abstract fun observeRecent(limit: Int): Flow<List<PurchaseRecordWithItems>>

    @Query("SELECT * FROM purchase_records WHERE id = :id")
    abstract suspend fun getRecord(id: String): PurchaseRecordEntity?

    @Query("DELETE FROM purchase_records WHERE id = :id")
    abstract suspend fun deleteRecord(id: String)

    @Query("DELETE FROM purchase_items WHERE recordId = :recordId")
    abstract suspend fun deleteItemsByRecordId(recordId: String)

    // 无外键约束，级联删除在这里手工完成
    @Transaction
    open suspend fun deletePurchaseWithItems(id: String) {
        deleteItemsByRecordId(id)
        deleteRecord(id)
    }
}
