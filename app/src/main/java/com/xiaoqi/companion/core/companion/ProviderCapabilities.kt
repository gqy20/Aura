package com.xiaoqi.companion.core.companion

import com.xiaoqi.companion.core.tools.ToolPolicy
import com.xiaoqi.companion.data.db.converter.LlmProvider

data class ProviderCapabilities(
    val supportsStreaming: Boolean,
    val supportsVision: Boolean,
    val supportsTools: Boolean,
    val supportsThinking: Boolean,
    val maxContextTokens: Int,
    val defaultToolPolicy: ToolPolicy,
)

object ProviderCapabilityRegistry {
    fun forProvider(provider: LlmProvider): ProviderCapabilities =
        when (provider) {
            LlmProvider.GLM -> ProviderCapabilities(
                supportsStreaming = true,
                supportsVision = true,
                supportsTools = true,
                supportsThinking = true,
                maxContextTokens = 200_000,
                defaultToolPolicy = ToolPolicy.chatDefault,
            )
            LlmProvider.KIMI -> ProviderCapabilities(
                supportsStreaming = true,
                supportsVision = true,
                supportsTools = true,
                supportsThinking = false,
                maxContextTokens = 256_000,
                defaultToolPolicy = ToolPolicy.chatDefault,
            )
            LlmProvider.MODELSCOPE -> ProviderCapabilities(
                supportsStreaming = true,
                supportsVision = false,
                supportsTools = true,
                supportsThinking = true,
                maxContextTokens = 128_000,
                defaultToolPolicy = ToolPolicy.chatDefault,
            )
            LlmProvider.LOCAL_QWEN -> ProviderCapabilities(
                supportsStreaming = true,
                supportsVision = true,
                supportsTools = false,
                supportsThinking = false,
                maxContextTokens = 32_000,
                defaultToolPolicy = ToolPolicy.none,
            )
        }
}
