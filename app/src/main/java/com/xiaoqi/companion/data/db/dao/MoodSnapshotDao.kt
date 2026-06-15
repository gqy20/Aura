package com.xiaoqi.companion.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xiaoqi.companion.data.db.entity.MoodSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MoodSnapshotDao {

    @Query("SELECT * FROM mood_snapshots WHERE companion_id = :companionId ORDER BY timestamp DESC")
    fun observeByCompanionId(companionId: String): Flow<List<MoodSnapshotEntity>>

    @Query("SELECT * FROM mood_snapshots WHERE companion_id = :companionId AND timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    fun observeByDateRange(companionId: String, start: Long, end: Long): Flow<List<MoodSnapshotEntity>>

    @Query("SELECT * FROM mood_snapshots WHERE companion_id = :companionId AND timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    suspend fun findInRange(companionId: String, start: Long, end: Long): List<MoodSnapshotEntity>

    @Query("SELECT * FROM mood_snapshots WHERE companion_id = :companionId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestSnapshot(companionId: String): MoodSnapshotEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM mood_snapshots WHERE id = :id)")
    suspend fun existsById(id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MoodSnapshotEntity)

    @Query("DELETE FROM mood_snapshots WHERE companion_id = :companionId")
    suspend fun deleteByCompanionId(companionId: String)

    @Query("SELECT COUNT(*) FROM mood_snapshots")
    suspend fun countAll(): Int

    @Query("DELETE FROM mood_snapshots")
    suspend fun clearAll()
}
