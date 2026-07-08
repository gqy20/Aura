package com.xiaoqi.companion.core.companion

import com.xiaoqi.companion.core.tools.ToolCategory
import com.xiaoqi.companion.data.db.converter.LlmProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCapabilitiesTest {

    @Test
    fun remoteProviders_supportStreamingAndTools() {
        val glm = ProviderCapabilityRegistry.forProvider(LlmProvider.GLM)
        val kimi = ProviderCapabilityRegistry.forProvider(LlmProvider.KIMI)

        assertTrue(glm.supportsStreaming)
        assertTrue(glm.supportsTools)
        assertTrue(ToolCategory.REMOTE_READ in glm.defaultToolPolicy.allowedCategories)
        assertTrue(kimi.supportsStreaming)
        assertTrue(kimi.supportsTools)
    }

    @Test
    fun localQwen_disablesDefaultTools() {
        val capabilities = ProviderCapabilityRegistry.forProvider(LlmProvider.LOCAL_QWEN)

        assertTrue(capabilities.supportsStreaming)
        assertFalse(capabilities.supportsTools)
        assertFalse(capabilities.defaultToolPolicy.allowTools)
    }
}
