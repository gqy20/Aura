package com.xiaoqi.companion.core.companion

import com.xiaoqi.companion.core.prompt.BuiltPrompt
import com.xiaoqi.companion.data.repository.LlmConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KoogAgentFactoryImpl @Inject constructor() : KoogAgentFactory {

    override fun create(config: LlmConfig): KoogAgentWrapper {
        return StubKoogAgentWrapper(config)
    }
}

class StubKoogAgentWrapper(private val config: LlmConfig) : KoogAgentWrapper {

    override suspend fun run(prompt: BuiltPrompt): String {
        // TODO: integrate with real Koog AIAgent when API is configured
        return ""
    }
}
