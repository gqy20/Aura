package com.xiaoqi.companion.core.tools

import com.xiaoqi.companion.data.db.dao.AgentStateDao
import com.xiaoqi.companion.data.db.entity.AgentStateEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateRelationshipToolTest {

    private val agentStateDao: AgentStateDao = mockk(relaxed = true)

    @Test
    fun execute_updatesExistingRelationship() = runTest {
        val existing = AgentStateEntity(
            id = "state-1", companionId = "comp-1", mood = "happy",
            relationshipLevel = 0.5f, createdAt = 1000L, updatedAt = 1000L,
        )
        coEvery { agentStateDao.getByCompanionId("comp-1") } returns existing

        val tool = UpdateRelationshipTool(agentStateDao, companionIdProvider = { "comp-1" })
        val result = tool.execute(UpdateRelationshipTool.Args(delta = 0.1f, reason = "User shared a personal story"))

        assertTrue(result.contains("updated"))
        coVerify {
            agentStateDao.updateRelationshipLevel("comp-1", 0.6f, any())
        }
    }

    @Test
    fun execute_createsNewStateWhenNoneExists() = runTest {
        coEvery { agentStateDao.getByCompanionId("comp-1") } returns null

        val tool = UpdateRelationshipTool(agentStateDao, companionIdProvider = { "comp-1" })
        tool.execute(UpdateRelationshipTool.Args(delta = 0.3f))

        coVerify {
            agentStateDao.insert(match<AgentStateEntity> {
                it.companionId == "comp-1" && it.relationshipLevel == 0.3f
            })
        }
    }

    @Test
    fun execute_coercesLevelToZeroToOne() = runTest {
        val existing = AgentStateEntity(
            id = "state-1", companionId = "comp-1", mood = "neutral",
            relationshipLevel = 0.8f, createdAt = 1000L, updatedAt = 1000L,
        )
        coEvery { agentStateDao.getByCompanionId("comp-1") } returns existing

        val tool = UpdateRelationshipTool(agentStateDao, companionIdProvider = { "comp-1" })
        tool.execute(UpdateRelationshipTool.Args(delta = 0.5f))  // 0.8 + 0.5 = 1.3 -> coerced to 1.0

        coVerify { agentStateDao.updateRelationshipLevel("comp-1", 1.0f, any()) }
    }

    @Test
    fun execute_handlesNegativeDelta() = runTest {
        val existing = AgentStateEntity(
            id = "state-1", companionId = "comp-1", mood = "neutral",
            relationshipLevel = 0.7f, createdAt = 1000L, updatedAt = 1000L,
        )
        coEvery { agentStateDao.getByCompanionId("comp-1") } returns existing

        val tool = UpdateRelationshipTool(agentStateDao, companionIdProvider = { "comp-1" })
        tool.execute(UpdateRelationshipTool.Args(delta = -0.2f, reason = "User was upset"))

        coVerify { agentStateDao.updateRelationshipLevel("comp-1", 0.5f, any()) }
    }

    @Test
    fun descriptor_exposesKoogToolMetadata() {
        val tool = UpdateRelationshipTool(mockk(), companionIdProvider = { "comp-1" })

        assertEquals("update_relationship", tool.name)
        val desc = tool.descriptor.description
        assertTrue(desc.contains("relationship", ignoreCase = true) || desc.contains("affinity", ignoreCase = true))
    }
}
