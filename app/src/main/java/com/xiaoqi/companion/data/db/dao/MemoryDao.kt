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

    @Query("SELECT * FROM memories ORDER BY pinned DESC, importance DESC, lastAccessed DESC")
    fun observeAllPinnedFirst(): Flow<List<MemoryEntity>>

    @Query("UPDATE memories SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("UPDATE memories SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean)

    @Query("SELECT COUNT(*) FROM memories WHERE archived = 1")
    suspend fun countArchived(): Int

    @Query("SELECT * FROM memories WHERE type = :type ORDER BY importance DESC")
    fun observeByType(type: com.xiaoqi.companion.data.db.converter.MemoryType): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun getById(id: String): MemoryEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM memories WHERE id = :id)")
    suspend fun existsById(id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MemoryEntity)

    @Query(
        """
        SELECT * FROM memories
        WHERE type = :type
          AND content LIKE :pattern ESCAPE '\'
        ORDER BY updatedAt DESC
        LIMIT :limit
        """
    )
    suspend fun findSimilar(
        pattern: String,
        type: com.xiaoqi.companion.data.db.converter.MemoryType,
        limit: Int,
    ): List<MemoryEntity>

    @Query("UPDATE memories SET lastAccessed = :time WHERE id = :id")
    suspend fun updateLastAccessed(id: String, time: Long)

    @Query("SELECT * FROM memories WHERE timestamp >= :after ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(after: Long, limit: Int): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories ORDER BY importance DESC, lastAccessed DESC LIMIT :limit")
    suspend fun getPromptMemories(limit: Int): List<MemoryEntity>

    @Query("SELECT * FROM memories ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMemories(limit: Int): List<MemoryEntity>

    @Query(
        """
        SELECT * FROM memories
        WHERE (:type IS NULL OR type = :type)
          AND content LIKE :pattern ESCAPE '\'
        ORDER BY importance DESC, lastAccessed DESC
        LIMIT :limit
        """
    )
    suspend fun searchByContent(
        pattern: String,
        type: com.xiaoqi.companion.data.db.converter.MemoryType?,
        limit: Int,
    ): List<MemoryEntity>

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM memories")
    suspend fun countAll(): Int

    @Query("DELETE FROM memories")
    suspend fun clearAll()
}
