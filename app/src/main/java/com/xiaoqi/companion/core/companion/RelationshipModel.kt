package com.xiaoqi.companion.core.companion

interface RelationshipModel {
    val currentLevel: Float
    suspend fun applyDelta(delta: Float, reason: String = "")
    fun contextModifier(): String
}
