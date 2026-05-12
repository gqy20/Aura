package com.xiaoqi.companion.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xiaoqi.companion.data.db.entity.ToolCallEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ToolCallDao {

    @Query("SELECT * FROM tool_calls WHERE sessionId = :sessionId ORDER BY createdAt DESC")
    fun observeBySession(sessionId: String): Flow<List<ToolCallEntity>>

    @Query("SELECT * FROM tool_calls WHERE id = :id")
    suspend fun getById(id: String): ToolCallEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ToolCallEntity)

    @Query(
        """
        UPDATE tool_calls
        SET status = :status,
            resultJson = :resultJson,
            errorMessage = :errorMessage,
            completedAt = :completedAt
        WHERE id = :id
        """
    )
    suspend fun updateResult(
        id: String,
        status: String,
        resultJson: String,
        errorMessage: String?,
        completedAt: Long,
    )
}
