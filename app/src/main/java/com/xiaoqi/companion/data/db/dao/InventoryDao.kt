package com.xiaoqi.companion.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xiaoqi.companion.data.db.entity.InventoryItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: InventoryItemEntity)

    @Query("SELECT * FROM inventory_items ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items WHERE id = :id")
    suspend fun getById(id: String): InventoryItemEntity?

    // MAX(a, b) 双参是标量 max：扣减不产生负库存
    @Query(
        """
        UPDATE inventory_items
        SET currentQuantity = MAX(0.0, currentQuantity + :delta), updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun adjustQuantity(id: String, delta: Double, updatedAt: Long)

    @Query("UPDATE inventory_items SET lowStockThreshold = :threshold, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setLowStockThreshold(id: String, threshold: Double?, updatedAt: Long)

    @Query("DELETE FROM inventory_items WHERE id = :id")
    suspend fun deleteById(id: String)
}
