package com.xiaoqi.companion.core.companion

interface EmotionStateMachine {
    val currentMood: String
    val latestIntensity: Float
    suspend fun record(mood: String, intensity: Float = 0.5f, reason: String = "")
    fun getContext(): String
}
