package com.xiaoqi.companion.data.repository

import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogFieldSanitizer
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.data.db.converter.MessageRole
import com.xiaoqi.companion.data.db.dao.MessageDao
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

class MessageRepositoryImpl @Inject constructor(private val dao: MessageDao) : MessageRepository {

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
        dao.insert(
            MessageEntity(
                id = id,
                sessionId = sessionId,
                role = MessageRole.USER,
                content = content,
                imageBase64 = imageBase64,
                timestamp = System.currentTimeMillis(),
            )
        )
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
        dao.insert(
            MessageEntity(
                id = id,
                sessionId = sessionId,
                role = MessageRole.ASSISTANT,
                content = content,
                timestamp = System.currentTimeMillis(),
            )
        )
        return id
    }

    override suspend fun deleteSession(sessionId: String) {
        AppLogger.info(
            LogTags.Repo,
            "session_delete_started",
            "sessionHash" to LogFieldSanitizer.hash(sessionId),
        )
        dao.deleteBySession(sessionId)
    }
}
