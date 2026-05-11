package com.xiaoqi.companion.core.companion

import com.xiaoqi.companion.core.companion.model.InteractionSignal

interface RelationshipModel {
    val currentLevel: Float
    suspend fun update(signal: InteractionSignal)
    fun contextModifier(): String
}
