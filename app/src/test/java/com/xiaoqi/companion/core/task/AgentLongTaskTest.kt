package com.xiaoqi.companion.core.task

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentLongTaskTest {

    @Test
    fun isActive_onlyForOpenStatuses() {
        assertTrue(AgentLongTask("1", "Queued", AgentLongTaskStatus.QUEUED).isActive)
        assertTrue(AgentLongTask("2", "Running", AgentLongTaskStatus.RUNNING).isActive)
        assertTrue(AgentLongTask("3", "Waiting", AgentLongTaskStatus.WAITING_FOR_USER).isActive)
        assertFalse(AgentLongTask("4", "Done", AgentLongTaskStatus.SUCCEEDED).isActive)
        assertFalse(AgentLongTask("5", "Failed", AgentLongTaskStatus.FAILED).isActive)
    }

    @Test
    fun activeSummary_countsActiveAndPicksLatestTitle() {
        val summary = listOf(
            AgentLongTask("1", "Old", AgentLongTaskStatus.RUNNING, updatedAtMillis = 10),
            AgentLongTask("2", "Done", AgentLongTaskStatus.SUCCEEDED, updatedAtMillis = 30),
            AgentLongTask("3", "New", AgentLongTaskStatus.WAITING_FOR_USER, updatedAtMillis = 20),
        ).activeSummary()

        assertEquals(2, summary.activeCount)
        assertEquals("New", summary.latestTitle)
        assertTrue(summary.hasActiveTasks)
    }

    @Test
    fun clampedProgress_keepsUiSafe() {
        assertEquals(0f, AgentLongTask("1", "A", AgentLongTaskStatus.RUNNING, progress = -1f).clampedProgress)
        assertEquals(1f, AgentLongTask("2", "B", AgentLongTaskStatus.RUNNING, progress = 2f).clampedProgress)
    }
}
