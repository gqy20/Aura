package com.xiaoqi.companion.data.repository

import com.xiaoqi.companion.data.db.dao.MessageDao
import com.xiaoqi.companion.data.db.entity.MessageEntity
import com.xiaoqi.companion.data.db.converter.MessageRole
import timber.log.Timber
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

private const val TAG = "Companion-Repo"

interface MessageRepository {
    fun getMessagesBySession(sessionId: String): Flow<List<MessageEntity>>
    suspend fun sendMessage(sessionId: String, content: String, imageBase64: String? = null)
    suspend fun deleteSession(sessionId: String)
}

class MessageRepositoryImpl @Inject constructor(private val dao: MessageDao) : MessageRepository {

    override fun getMessagesBySession(sessionId: String): Flow<List<MessageEntity>> =
        dao.observeBySession(sessionId)

    override suspend fun sendMessage(sessionId: String, content: String, imageBase64: String?) {
        Timber.tag(TAG).d("sendMessage: session=%s, content length=%d, hasImage=%s",
            sessionId, content.length, imageBase64 != null)
        dao.insert(
            MessageEntity(
                id = java.util.UUID.randomUUID().toString(),
                sessionId = sessionId,
                role = MessageRole.USER,
                content = content,
                imageBase64 = imageBase64,
                timestamp = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun deleteSession(sessionId: String) {
        Timber.tag(TAG).d("deleteSession: session=%s", sessionId)
        dao.deleteBySession(sessionId)
    }
}
