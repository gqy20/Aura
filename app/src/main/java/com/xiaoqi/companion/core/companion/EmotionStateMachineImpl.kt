package com.xiaoqi.companion.core.companion

import com.xiaoqi.companion.core.companion.model.EmotionSignal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmotionStateMachineImpl @Inject constructor() : EmotionStateMachine {

    override var currentMood: String = "neutral"
        private set

    override suspend fun feed(signal: EmotionSignal) {
        currentMood = signal.mood.ifBlank { "neutral" }
    }

    override fun getContext(): String =
        if (currentMood == "neutral") "" else "当前情绪：$currentMood"
}
