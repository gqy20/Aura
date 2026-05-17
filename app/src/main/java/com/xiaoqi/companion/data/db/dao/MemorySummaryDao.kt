package com.xiaoqi.companion.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xiaoqi.companion.data.db.converter.SummaryType
import com.xiaoqi.companion.data.db.entity.MemorySummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemorySummaryDao {

    @Query("SELECT * FROM memory_summaries ORDER BY importance DESC, lastAccessed DESC")
    fun observeAll(): Flow<List<MemorySummaryEntity>>

    @Query("SELECT * FROM memory_summaries WHERE id = :id")
    suspend fun getById(id: String): MemorySummaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MemorySummaryEntity)

    @Query("UPDATE memory_summaries SET lastAccessed = :time WHERE id = :id")
    suspend fun updateLastAccessed(id: String, time: Long)

    @Query(
        """
        SELECT * FROM memory_summaries
        WHERE (:type IS NULL OR type = :type)
          AND (
              title LIKE :pattern ESCAPE '\'
              OR summary LIKE :pattern ESCAPE '\'
              OR keywords LIKE :pattern ESCAPE '\'
          )
        ORDER BY importance DESC, lastAccessed DESC
        LIMIT :limit
        """
    )
    suspend fun searchByText(
        pattern: String,
        type: SummaryType?,
        limit: Int,
    ): List<MemorySummaryEntity>

    @Query("DELETE FROM memory_summaries WHERE id = :id")
    suspend fun deleteById(id: String)
}
