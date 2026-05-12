package com.xiaoqi.companion.core.companion

import com.xiaoqi.companion.core.prompt.BuiltPrompt
import com.xiaoqi.companion.data.repository.LlmConfig
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "Companion-Agent"

@Singleton
class KoogAgentFactoryImpl @Inject constructor() : KoogAgentFactory {

    override fun create(config: LlmConfig): KoogAgentWrapper {
        Timber.tag(TAG).d("Creating agent: provider=%s, model=%s", config.provider, config.modelName)
        return StubKoogAgentWrapper(config)
    }
}

class StubKoogAgentWrapper(private val config: LlmConfig) : KoogAgentWrapper {

    override suspend fun run(prompt: BuiltPrompt): String {
        Timber.tag(TAG).w("StubKoogAgentWrapper.run called — returning empty response")
        // TODO: integrate with real Koog AIAgent when API is configured
        return ""
    }
}
