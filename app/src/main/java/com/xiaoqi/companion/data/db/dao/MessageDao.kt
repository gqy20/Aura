package com.xiaoqi.companion.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xiaoqi.companion.data.db.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * messages 主表 DAO。
 *
 * 只负责 `messages` 表的 CRUD。FTS5 索引维护（`message_search_docs` + `message_search_docs_fts`）
 * 由 [MessageSearchDao] 独立承担，caller 在 insert/delete 后需手动调 `messageSearchDao.index()` /
 * `messageSearchDao.unindexSession()` 同步索引。
 *
 * @see MessageSearchDao FTS5 索引与检索
 */
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
abstract class MessageDao {

    @Query("SELECT * FROM messages WHERE session_id = :sessionId ORDER BY timestamp ASC")
    abstract fun observeBySession(sessionId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id")
    abstract suspend fun getById(id: String): MessageEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM messages WHERE id = :id)")
    abstract suspend fun existsById(id: String): Boolean

    @Query(
        """
        SELECT * FROM messages
        WHERE session_id = :sessionId
        ORDER BY timestamp DESC
        LIMIT :limit
        """
    )
    abstract suspend fun getRecentMessages(
        sessionId: String,
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
    abstract suspend fun getMessagesBefore(
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
    abstract suspend fun getMessagesAfter(
        sessionId: String,
        timestamp: Long,
        limit: Int,
    ): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(entity: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(entities: List<MessageEntity>)

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
    abstract suspend fun getInteractionSummary(
        sessionId: String,
        startOfToday: Long,
    ): MessageInteractionSummary

    @Query("DELETE FROM messages WHERE session_id = :sessionId")
    abstract suspend fun deleteBySession(sessionId: String)

    @Query("SELECT COUNT(*) FROM messages")
    abstract suspend fun countAll(): Int
}
