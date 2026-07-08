package com.xiaoqi.companion.core.companion

import com.xiaoqi.companion.core.companion.model.UserInput
import com.xiaoqi.companion.core.tools.ToolPolicy
import com.xiaoqi.companion.data.db.converter.LlmProvider
import com.xiaoqi.companion.data.repository.LlmConfig
import javax.inject.Inject
import javax.inject.Singleton

enum class AgentTurnMode {
    CHAT_ONLY,
    CHAT_WITH_LOCAL_TOOLS,
    VISION_REPLY_ONLY,
    VISION_WITH_PRE_CONTEXT,
    REMOTE_TOOL_TASK,
    REQUIRES_CONFIRMATION,
    LONG_RUNNING_TASK,
}

data class AgentTurnDecision(
    val mode: AgentTurnMode,
    val toolPolicy: ToolPolicy,
    val allowTools: Boolean,
    val reason: String,
)

@Singleton
class AgentTurnPolicy @Inject constructor() {
    fun decide(
        input: UserInput,
        config: LlmConfig,
        providerCapabilities: ProviderCapabilities = ProviderCapabilityRegistry.forProvider(config.provider),
        systemToolsEnabled: Boolean = true,
        mcpEnabled: Boolean = true,
    ): AgentTurnDecision {
        if (input is UserInput.Vision) {
            return AgentTurnDecision(
                mode = AgentTurnMode.VISION_REPLY_ONLY,
                toolPolicy = ToolPolicy.none,
                allowTools = false,
                reason = "vision_reply_no_tools",
            )
        }

        if (config.provider == LlmProvider.LOCAL_QWEN || !providerCapabilities.supportsTools) {
            return AgentTurnDecision(
                mode = AgentTurnMode.CHAT_ONLY,
                toolPolicy = ToolPolicy.none,
                allowTools = false,
                reason = "provider_tools_disabled",
            )
        }

        val basePolicy = providerCapabilities.defaultToolPolicy
        val effectivePolicy = when {
            !systemToolsEnabled && !mcpEnabled -> ToolPolicy.none
            systemToolsEnabled && mcpEnabled -> basePolicy
            systemToolsEnabled -> ToolPolicy.systemOnly
            else -> ToolPolicy.readOnly
        }
        return AgentTurnDecision(
            mode = if (effectivePolicy.allowTools) AgentTurnMode.CHAT_WITH_LOCAL_TOOLS else AgentTurnMode.CHAT_ONLY,
            toolPolicy = effectivePolicy,
            allowTools = effectivePolicy.allowTools,
            reason = "default_chat_policy",
        )
    }
}
