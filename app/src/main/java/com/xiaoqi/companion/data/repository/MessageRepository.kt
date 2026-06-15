package com.xiaoqi.companion.data.repository

import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogFieldSanitizer
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.data.db.converter.MessageRole
import com.xiaoqi.companion.data.db.dao.MessageDao
import com.xiaoqi.companion.data.db.dao.MessageSearchDao
import com.xiaoqi.companion.data.db.entity.MessageEntity
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun getMessagesBySession(sessionId: String): Flow<List<MessageEntity>>
    suspend fun getRecentMessages(sessionId: String, limit: Int): List<MessageEntity>
    suspend fun sendMessage(sessionId: String, content: String, imageBase64: String? = null): String
    suspend fun saveAssistantMessage(sessionId: String, content: String): String
    suspend fun deleteSession(sessionId: String)
}

/**
 * 消息读写仓储。
 *
 * **FTS5 索引同步**：每次 `dao.insert` 之后调 `messageSearchDao.index(entity)`，
 * 每次 `dao.deleteBySession` 之后调 `messageSearchDao.unindexSession(sessionId)`。
 * 两次 DAO 调用**不在同一事务里**——极小窗口不一致（messages 有但索引还没写，或反之）可接受；
 * FTS5 JOIN `messages` 表会自动过滤死引用，索引重建可在后续 migration 或显式调用里补齐。
 */
class MessageRepositoryImpl @Inject constructor(
    private val dao: MessageDao,
    private val messageSearchDao: MessageSearchDao,
) : MessageRepository {

    override fun getMessagesBySession(sessionId: String): Flow<List<MessageEntity>> =
        dao.observeBySession(sessionId)

    override suspend fun getRecentMessages(sessionId: String, limit: Int): List<MessageEntity> =
        dao.getRecentMessages(sessionId = sessionId, limit = limit.coerceAtLeast(1))

    override suspend fun sendMessage(sessionId: String, content: String, imageBase64: String?): String {
        AppLogger.debug(
            LogTags.Repo,
            "message_insert_started",
            "sessionHash" to LogFieldSanitizer.hash(sessionId),
            "contentLength" to content.length,
            "hasImage" to (imageBase64 != null),
        )
        val id = java.util.UUID.randomUUID().toString()
        val entity = MessageEntity(
            id = id,
            sessionId = sessionId,
            role = MessageRole.USER,
            content = content,
            imageBase64 = imageBase64,
            timestamp = System.currentTimeMillis(),
        )
        dao.insert(entity)
        messageSearchDao.index(entity)
        return id
    }

    override suspend fun saveAssistantMessage(sessionId: String, content: String): String {
        AppLogger.debug(
            LogTags.Repo,
            "assistant_message_insert_started",
            "sessionHash" to LogFieldSanitizer.hash(sessionId),
            "contentLength" to content.length,
        )
        val id = java.util.UUID.randomUUID().toString()
        val entity = MessageEntity(
            id = id,
            sessionId = sessionId,
            role = MessageRole.ASSISTANT,
            content = content,
            timestamp = System.currentTimeMillis(),
        )
        dao.insert(entity)
        messageSearchDao.index(entity)
        return id
    }

    override suspend fun deleteSession(sessionId: String) {
        AppLogger.info(
            LogTags.Repo,
            "session_delete_started",
            "sessionHash" to LogFieldSanitizer.hash(sessionId),
        )
        dao.deleteBySession(sessionId)
        messageSearchDao.unindexSession(sessionId)
    }
}
