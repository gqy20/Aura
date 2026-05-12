package com.xiaoqi.companion.core.companion

import com.xiaoqi.companion.core.companion.model.EmotionSignal
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmotionStateMachineImpl @Inject constructor() : EmotionStateMachine {

    override var currentMood: String = "neutral"
        private set

    override suspend fun feed(signal: EmotionSignal) {
        val previous = currentMood
        currentMood = signal.mood.ifBlank { "neutral" }
        if (previous != currentMood) {
            AppLogger.debug(
                LogTags.Emotion,
                "mood_changed",
                "previousMood" to previous,
                "currentMood" to currentMood,
                "intensity" to signal.intensity,
            )
        }
    }

    override fun getContext(): String =
        if (currentMood == "neutral") "" else "当前情绪：$currentMood"
}
