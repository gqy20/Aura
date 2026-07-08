package com.xiaoqi.companion.core.companion

import com.xiaoqi.companion.core.companion.model.UserInput
import com.xiaoqi.companion.core.tools.ToolCategory
import com.xiaoqi.companion.core.tools.ToolPolicy
import com.xiaoqi.companion.data.db.converter.LlmProvider
import com.xiaoqi.companion.data.repository.LlmConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentTurnPolicyTest {

    private val policy = AgentTurnPolicy()

    @Test
    fun decide_textRemoteProvider_allowsDefaultChatTools() {
        val decision = policy.decide(
            input = UserInput.Text("hello"),
            config = config(LlmProvider.GLM),
        )

        assertEquals(AgentTurnMode.CHAT_WITH_LOCAL_TOOLS, decision.mode)
        assertTrue(decision.allowTools)
        assertTrue(ToolCategory.READ_CONTEXT in decision.toolPolicy.allowedCategories)
        assertTrue(ToolCategory.LOCAL_WRITE in decision.toolPolicy.allowedCategories)
        assertTrue(ToolCategory.REMOTE_READ in decision.toolPolicy.allowedCategories)
    }

    @Test
    fun decide_vision_disablesTools() {
        val decision = policy.decide(
            input = UserInput.Vision(
                text = "look",
                imageBase64 = "abc",
                mediaType = "image/jpeg",
                displayText = "look",
            ),
            config = config(LlmProvider.GLM),
        )

        assertEquals(AgentTurnMode.VISION_REPLY_ONLY, decision.mode)
        assertFalse(decision.allowTools)
        assertEquals(ToolPolicy.none, decision.toolPolicy)
    }

    @Test
    fun decide_systemOnMcpOff_usesSystemOnlyPolicy() {
        val decision = policy.decide(
            input = UserInput.Text("hello"),
            config = config(LlmProvider.GLM),
            systemToolsEnabled = true,
            mcpEnabled = false,
        )

        assertEquals(ToolPolicy.systemOnly.allowedCategories, decision.toolPolicy.allowedCategories)
    }

    @Test
    fun decide_localProvider_disablesTools() {
        val decision = policy.decide(
            input = UserInput.Text("hello"),
            config = config(LlmProvider.LOCAL_QWEN),
        )

        assertEquals(AgentTurnMode.CHAT_ONLY, decision.mode)
        assertFalse(decision.allowTools)
    }

    private fun config(provider: LlmProvider): LlmConfig =
        LlmConfig(
            provider = provider,
            baseUrl = "https://example.test",
            apiKey = "key",
            modelName = "model",
        )
}
