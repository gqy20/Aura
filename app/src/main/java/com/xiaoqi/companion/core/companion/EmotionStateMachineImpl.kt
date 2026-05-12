package com.xiaoqi.companion.core.companion

import com.xiaoqi.companion.core.companion.model.EmotionSignal
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "Companion-Emotion"

@Singleton
class EmotionStateMachineImpl @Inject constructor() : EmotionStateMachine {

    override var currentMood: String = "neutral"
        private set

    override suspend fun feed(signal: EmotionSignal) {
        val previous = currentMood
        currentMood = signal.mood.ifBlank { "neutral" }
        if (previous != currentMood) {
            Timber.tag(TAG).d("Mood changed: %s -> %s (intensity=%.2f)", previous, currentMood, signal.intensity)
        }
    }

    override fun getContext(): String =
        if (currentMood == "neutral") "" else "当前情绪：$currentMood"
}
