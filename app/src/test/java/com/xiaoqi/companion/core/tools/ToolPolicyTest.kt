package com.xiaoqi.companion.core.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolPolicyTest {

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
}
