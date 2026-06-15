package com.xiaoqi.companion.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xiaoqi.companion.data.db.converter.MessageRole
import com.xiaoqi.companion.data.db.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

data class MessageInteractionSummary(
    val messageCount: Int,
    val userMessageCount: Int,
    val assistantMessageCount: Int,
    val messagesToday: Int,
    val userMessagesToday: Int,
    val assistantMessagesToday: Int,
    val lastMessageRole: String?,
    val lastMessageAt: Long,
    val lastUserMessageAt: Long,
)

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE session_id = :sessionId ORDER BY timestamp ASC")
    fun observeBySession(sessionId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: String): MessageEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM messages WHERE id = :id)")
    suspend fun existsById(id: String): Boolean

    @Query(
        """
        SELECT * FROM messages
        WHERE session_id = :sessionId
        ORDER BY timestamp DESC
        LIMIT :limit
        """
    )
    suspend fun getRecentMessages(
        sessionId: String,
        limit: Int,
    ): List<MessageEntity>

    @Query(
        """
        SELECT * FROM messages
        WHERE session_id = :sessionId
          AND (:role IS NULL OR role = :role)
          AND (:after IS NULL OR timestamp >= :after)
          AND (:before IS NULL OR timestamp <= :before)
          AND (
              :hasImage IS NULL
              OR (:hasImage = 1 AND imageBase64 IS NOT NULL)
              OR (:hasImage = 0 AND imageBase64 IS NULL)
          )
          AND content LIKE :pattern ESCAPE '\'
        ORDER BY timestamp DESC
        LIMIT :limit
        """
    )
    suspend fun searchRecords(
        sessionId: String,
        pattern: String,
        role: MessageRole?,
        after: Long?,
        before: Long?,
        hasImage: Boolean?,
        limit: Int,
    ): List<MessageEntity>

    @Query(
        """
        SELECT * FROM messages
        WHERE session_id = :sessionId
          AND timestamp < :timestamp
        ORDER BY timestamp DESC
        LIMIT :limit
        """
    )
    suspend fun getMessagesBefore(
        sessionId: String,
        timestamp: Long,
        limit: Int,
    ): List<MessageEntity>

    @Query(
        """
        SELECT * FROM messages
        WHERE session_id = :sessionId
          AND timestamp > :timestamp
        ORDER BY timestamp ASC
        LIMIT :limit
        """
    )
    suspend fun getMessagesAfter(
        sessionId: String,
        timestamp: Long,
        limit: Int,
    ): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<MessageEntity>)

    @Query(
        """
        SELECT
            COUNT(*) AS messageCount,
            COALESCE(SUM(CASE WHEN role = 'USER' THEN 1 ELSE 0 END), 0) AS userMessageCount,
            COALESCE(SUM(CASE WHEN role = 'ASSISTANT' THEN 1 ELSE 0 END), 0) AS assistantMessageCount,
            COALESCE(SUM(CASE WHEN timestamp >= :startOfToday THEN 1 ELSE 0 END), 0) AS messagesToday,
            COALESCE(SUM(CASE WHEN role = 'USER' AND timestamp >= :startOfToday THEN 1 ELSE 0 END), 0) AS userMessagesToday,
            COALESCE(SUM(CASE WHEN role = 'ASSISTANT' AND timestamp >= :startOfToday THEN 1 ELSE 0 END), 0) AS assistantMessagesToday,
            (
                SELECT role FROM messages
                WHERE session_id = :sessionId
                ORDER BY timestamp DESC
                LIMIT 1
            ) AS lastMessageRole,
            COALESCE(MAX(timestamp), 0) AS lastMessageAt,
            COALESCE(MAX(CASE WHEN role = 'USER' THEN timestamp ELSE NULL END), 0) AS lastUserMessageAt
        FROM messages
        WHERE session_id = :sessionId
        """
    )
    suspend fun getInteractionSummary(
        sessionId: String,
        startOfToday: Long,
    ): MessageInteractionSummary

    @Query("DELETE FROM messages WHERE session_id = :sessionId")
    suspend fun deleteBySession(sessionId: String)

    @Query("SELECT COUNT(*) FROM messages")
    suspend fun countAll(): Int
}
