package com.xiaoqi.companion.data.repository

import com.xiaoqi.companion.data.db.dao.ConversationDao
import com.xiaoqi.companion.data.db.entity.ConversationEntity
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ConversationItem(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messageCount: Int,
    val summary: String?,
)

private fun ConversationEntity.toItem() = ConversationItem(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
    messageCount = messageCount,
    summary = summary,
)

class ConversationRepository @Inject constructor(
    private val dao: ConversationDao,
) {

    fun observeAll(): Flow<List<ConversationItem>> =
        dao.observeAll().map { entities ->
            entities.map { it.toItem() }
        }

    suspend fun getById(id: String): ConversationItem? =
        dao.getById(id)?.toItem()

    fun observeById(id: String): Flow<ConversationItem?> =
        dao.observeById(id).map { it?.toItem() }

    suspend fun createNew(firstMessage: String? = null): ConversationItem {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val title = generateTitle(firstMessage)
        val entity = ConversationEntity(
            id = id,
            title = title,
            createdAt = now,
            updatedAt = now,
            messageCount = 0,
        )
        dao.insert(entity)
        return entity.toItem()
    }

    suspend fun onMessageSent(sessionId: String, userMessage: String) {
        val existing = dao.getById(sessionId)
        if (existing != null) {
            dao.incrementMessageCount(sessionId, System.currentTimeMillis())
            if (existing.messageCount == 0 && existing.title == "New conversation") {
                dao.updateTitle(sessionId, generateTitle(userMessage), System.currentTimeMillis())
            }
        }
    }

    suspend fun updateTitle(id: String, title: String) {
        dao.updateTitle(id, title, System.currentTimeMillis())
    }

    suspend fun updateSummary(id: String, summary: String) {
        dao.updateSummary(id, summary)
    }

    suspend fun delete(id: String) {
        dao.deleteById(id)
    }

    suspend fun count(): Int = dao.count()

    private fun generateTitle(firstMessage: String?): String {
        if (firstMessage.isNullOrBlank()) return "New conversation"
        val cleaned = firstMessage.replace(Regex("\\s+"), " ").trim()
        return if (cleaned.length <= TITLE_MAX_LENGTH) cleaned
        else cleaned.take(TITLE_MAX_LENGTH) + "…"
    }

    companion object {
        const val DEFAULT_SESSION_ID = "default"
        private const val TITLE_MAX_LENGTH = 30
    }
}
