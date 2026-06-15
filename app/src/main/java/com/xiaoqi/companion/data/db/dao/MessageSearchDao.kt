package com.xiaoqi.companion.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.SkipQueryVerification
import androidx.room.Transaction
import com.xiaoqi.companion.data.db.converter.MessageRole
import com.xiaoqi.companion.data.db.entity.MessageEntity

/**
 * FTS5 全文检索 DAO。
 *
 * 负责 `message_search_docs`（索引文档表）与 `message_search_docs_fts`（trigram 虚拟表）的
 * 写入、删除与查询。`MessageDao` 不再承担这些职责——它只管 `messages` 主表。
 *
 * **真 FTS5 行为只在 androidTest 验证**：`upsertSearchDoc` 用 SQLite 3.24+ 的 UPSERT 语法
 * （`ON CONFLICT DO UPDATE`），JVM Robolectric 的 sqlite4java 引擎（SQLite 3.7.x）不支持。
 *
 * @see MessageDao messages 主表 CRUD
 */
data class MessageSearchHit(
    val id: String,
    val sessionId: String,
    val role: MessageRole,
    val content: String,
    val imageBase64: String?,
    val timestamp: Long,
    val ftsRank: Double,
)

@Dao
@SkipQueryVerification
abstract class MessageSearchDao {

    @Query(
        """
        INSERT INTO message_search_docs(message_id, session_id, role, content, has_image, timestamp)
        VALUES(:messageId, :sessionId, :role, :content, :hasImage, :timestamp)
        ON CONFLICT(message_id) DO UPDATE SET
            session_id = excluded.session_id,
            role = excluded.role,
            content = excluded.content,
            has_image = excluded.has_image,
            timestamp = excluded.timestamp
        """
    )
    abstract suspend fun upsertSearchDoc(
        messageId: String,
        sessionId: String,
        role: MessageRole,
        content: String,
        hasImage: Boolean,
        timestamp: Long,
    )

    @Query(
        """
        INSERT OR REPLACE INTO message_search_docs_fts(rowid, content)
        SELECT search_id, content FROM message_search_docs WHERE message_id = :messageId
        """
    )
    abstract suspend fun upsertSearchFts(messageId: String)

    @Transaction
    open suspend fun index(entity: MessageEntity) {
        upsertSearchDoc(
            messageId = entity.id,
            sessionId = entity.sessionId,
            role = entity.role,
            content = entity.content,
            hasImage = entity.imageBase64 != null,
            timestamp = entity.timestamp,
        )
        upsertSearchFts(entity.id)
    }

    @Transaction
    open suspend fun indexAll(entities: List<MessageEntity>) {
        entities.forEach { index(it) }
    }

    @Query(
        """
        SELECT
            m.id AS id,
            m.session_id AS sessionId,
            m.role AS role,
            m.content AS content,
            m.imageBase64 AS imageBase64,
            m.timestamp AS timestamp,
            bm25(message_search_docs_fts) AS ftsRank
        FROM message_search_docs_fts
        JOIN message_search_docs d ON d.search_id = message_search_docs_fts.rowid
        JOIN messages m ON m.id = d.message_id
        WHERE message_search_docs_fts MATCH :matchQuery
          AND d.session_id = :sessionId
          AND (:role IS NULL OR d.role = :role)
          AND (:after IS NULL OR d.timestamp >= :after)
          AND (:before IS NULL OR d.timestamp <= :before)
          AND (
              :hasImage IS NULL
              OR (:hasImage = 1 AND d.has_image = 1)
              OR (:hasImage = 0 AND d.has_image = 0)
          )
        ORDER BY ftsRank ASC, d.timestamp DESC
        LIMIT :limit
        """
    )
    abstract suspend fun searchRecordsFts(
        sessionId: String,
        matchQuery: String,
        role: MessageRole?,
        after: Long?,
        before: Long?,
        hasImage: Boolean?,
        limit: Int,
    ): List<MessageSearchHit>

    @Query(
        """
        DELETE FROM message_search_docs_fts
        WHERE rowid IN (
            SELECT search_id FROM message_search_docs WHERE session_id = :sessionId
        )
        """
    )
    abstract suspend fun deleteSearchFtsBySession(sessionId: String)

    @Query("DELETE FROM message_search_docs WHERE session_id = :sessionId")
    abstract suspend fun deleteSearchDocsBySession(sessionId: String)

    @Transaction
    open suspend fun unindexSession(sessionId: String) {
        deleteSearchFtsBySession(sessionId)
        deleteSearchDocsBySession(sessionId)
    }
}
