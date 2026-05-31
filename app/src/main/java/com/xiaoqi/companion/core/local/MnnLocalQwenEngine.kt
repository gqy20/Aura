package com.xiaoqi.companion.core.local

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@Singleton
class MnnLocalQwenEngine @Inject constructor() : LocalQwenEngine {

    override fun stream(request: LocalQwenRequest): Flow<String> = flow {
        throw IllegalStateException(
            "MNN local Qwen runtime is not available yet. " +
                "Place the MNN model/runtime integration behind LocalQwenEngine.",
        )
    }
}
