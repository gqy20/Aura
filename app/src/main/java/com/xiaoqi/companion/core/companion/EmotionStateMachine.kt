package com.xiaoqi.companion.core.companion

import com.xiaoqi.companion.core.companion.model.EmotionSignal

interface EmotionStateMachine {
    val currentMood: String
    suspend fun feed(signal: EmotionSignal)
    fun getContext(): String
}
