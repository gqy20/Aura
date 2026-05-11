package com.xiaoqi.companion.data.db.dao

import com.xiaoqi.companion.data.db.BaseDaoTest
import kotlinx.coroutines.test.runTest
import app.cash.turbine.test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class AgentStateDaoTest : BaseDaoTest() {

    private lateinit var dao: AgentStateDao

    override fun initDaos() {
        dao = db.agentStateDao()
    }

    @Test
    fun observeByCompanionId_returnsState() = runTest {
        val state = makeState(companionId = "c1")
        dao.insert(state)

        dao.observeByCompanionId("c1").test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals("c1", result!!.companionId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeByCompanionId_returnsNullForMissing() = runTest {
        dao.observeByCompanionId("nonexistent").test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun insert_sameCompanionId_replacesExisting() = runTest {
        dao.insert(makeState(companionId = "c1", mood = "happy"))
        dao.insert(makeState(id = UUID.randomUUID().toString(), companionId = "c1", mood = "sad"))

        val result = dao.getByCompanionId("c1")!!
        assertEquals("sad", result.mood)
    }

    @Test
    fun updateMood_changesOnlyMood() = runTest {
        val now = System.currentTimeMillis()
        dao.insert(makeState(companionId = "c1", mood = "happy", updatedAt = now))
        dao.updateMood("c1", "excited", now + 1000)

        val result = dao.getByCompanionId("c1")!!
        assertEquals("excited", result.mood)
        assertEquals(now + 1000, result.updatedAt)
    }

    @Test
    fun updateRelationshipLevel_changesOnlyLevel() = runTest {
        val now = System.currentTimeMillis()
        dao.insert(makeState(companionId = "c1", relationshipLevel = 0.3f, updatedAt = now))
        dao.updateRelationshipLevel("c1", 0.8f, now + 2000)

        val result = dao.getByCompanionId("c1")!!
        assertEquals(0.8f, result.relationshipLevel, 0.001f)
        assertEquals(now + 2000, result.updatedAt)
    }

    @Test
    fun deleteByCompanionId_removesState() = runTest {
        dao.insert(makeState(companionId = "c1"))
        dao.deleteByCompanionId("c1")
        assertNull(dao.getByCompanionId("c1"))
    }

    private fun makeState(
        id: String = UUID.randomUUID().toString(),
        companionId: String = "default",
        mood: String = "neutral",
        relationshipLevel: Float = 0f,
        createdAt: Long = System.currentTimeMillis(),
        updatedAt: Long = System.currentTimeMillis(),
    ) = com.xiaoqi.companion.data.db.entity.AgentStateEntity(
        id = id, companionId = companionId, mood = mood,
        relationshipLevel = relationshipLevel, createdAt = createdAt, updatedAt = updatedAt,
    )
}
