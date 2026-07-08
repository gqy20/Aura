package com.xiaoqi.companion.core.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolConfirmationPolicyTest {

    @Test
    fun requirement_readToolsDoNotNeedConfirmation() {
        val requirement = ToolConfirmationPolicy.requirement(ToolMetadataRegistry.searchMemory)

        assertFalse(requirement.required)
    }

    @Test
    fun requirement_localWritesNeedConfirmation() {
        val requirement = ToolConfirmationPolicy.requirement(ToolMetadataRegistry.createLocalReminder)

        assertTrue(requirement.required)
        assertTrue(requirement.message.contains("本机"))
    }

    @Test
    fun requirement_remoteWritesNeedConfirmation() {
        val requirement = ToolConfirmationPolicy.requirement(
            ToolMetadata("send_email", ToolCategory.REMOTE_WRITE, ToolRiskLevel.HIGH)
        )

        assertTrue(requirement.required)
        assertTrue(requirement.message.contains("外部服务"))
    }
}
