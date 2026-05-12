package com.xiaoqi.companion.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xiaoqi.companion.data.db.entity.MemoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {

    @Query("SELECT * FROM memories ORDER BY importance DESC, lastAccessed DESC")
    fun observeAll(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE type = :type ORDER BY importance DESC")
    fun observeByType(type: com.xiaoqi.companion.data.db.converter.MemoryType): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun getById(id: String): MemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MemoryEntity)

    @Query("UPDATE memories SET lastAccessed = :time WHERE id = :id")
    suspend fun updateLastAccessed(id: String, time: Long)

    @Query("SELECT * FROM memories WHERE timestamp >= :after ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(after: Long, limit: Int): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories ORDER BY importance DESC, lastAccessed DESC LIMIT :limit")
    suspend fun getPromptMemories(limit: Int): List<MemoryEntity>

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteById(id: String)
}
