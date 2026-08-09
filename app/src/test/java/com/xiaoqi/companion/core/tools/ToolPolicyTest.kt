package com.xiaoqi.companion.core.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolPolicyTest {

    @Test
    fun chatDefault_allowsFourRoundsForCompositeMcpTasks() {
        assertEquals(4, ToolPolicy.chatDefault.maxToolRoundsPerTurn)
    }

    @Test
    fun readOnly_allowsReadContextAndRemoteReadOnly() {
        assertTrue(ToolPolicy.readOnly.allows(ToolMetadataRegistry.searchMemory))
        assertTrue(ToolPolicy.readOnly.allows(ToolMetadataRegistry.remoteMcp("mcp__search")))
        assertFalse(ToolPolicy.readOnly.allows(ToolMetadataRegistry.updateState))
    }

    @Test
    fun systemOnly_allowsLocalWriteButBlocksRemoteRead() {
        assertTrue(ToolPolicy.systemOnly.allows(ToolMetadataRegistry.updateState))
        assertTrue(ToolPolicy.systemOnly.allows(ToolMetadataRegistry.createLocalReminder))
        assertFalse(ToolPolicy.systemOnly.allows(ToolMetadataRegistry.remoteMcp("mcp__search")))
    }

    @Test
    fun none_disablesEverything() {
        assertFalse(ToolPolicy.none.allowTools)
        assertFalse(ToolPolicy.none.allows(ToolMetadataRegistry.searchMemory))
    }

    @Test
    fun remoteMcp_classifiesMutationVerbsAsRemoteWrites() {
        listOf(
            "delivery-create-address",
            "mall-create-order",
            "party-order-create",
            "auto-bind-coupons",
        ).forEach { name ->
            val metadata = ToolMetadataRegistry.remoteMcp(name)
            assertEquals(ToolCategory.REMOTE_WRITE, metadata.category)
            assertEquals(ToolRiskLevel.HIGH, metadata.riskLevel)
            assertFalse(ToolPolicy.chatDefault.allows(metadata))
        }
    }

    @Test
    fun remoteMcp_keepsOrderQueriesAndSearchesReadOnly() {
        listOf(
            "query-order",
            "order-list",
            "mall-order-detail",
            "query-nearby-stores",
            "bing_search",
        ).forEach { name ->
            val metadata = ToolMetadataRegistry.remoteMcp(name)
            assertEquals(ToolCategory.REMOTE_READ, metadata.category)
            assertEquals(ToolRiskLevel.LOW, metadata.riskLevel)
            assertTrue(ToolPolicy.chatDefault.allows(metadata))
        }
    }
}
