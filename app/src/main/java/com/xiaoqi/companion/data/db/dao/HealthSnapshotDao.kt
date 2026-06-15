package com.xiaoqi.companion.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xiaoqi.companion.data.db.entity.HealthSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthSnapshotDao {

    @Query("SELECT * FROM health_snapshots ORDER BY date DESC")
    fun observeAll(): Flow<List<HealthSnapshotEntity>>

    @Query("SELECT * FROM health_snapshots WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun observeRange(startDate: Int, endDate: Int): Flow<List<HealthSnapshotEntity>>

    @Query("SELECT * FROM health_snapshots WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    suspend fun findInRange(startDate: Int, endDate: Int): List<HealthSnapshotEntity>

    @Query("SELECT * FROM health_snapshots WHERE date = :date LIMIT 1")
    suspend fun findByDate(date: Int): HealthSnapshotEntity?

    @Query("SELECT * FROM health_snapshots ORDER BY date DESC LIMIT 1")
    suspend fun getLatest(): HealthSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HealthSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<HealthSnapshotEntity>)

    @Query("DELETE FROM health_snapshots")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM health_snapshots")
    suspend fun countAll(): Int
}
