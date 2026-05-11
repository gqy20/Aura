package com.xiaoqi.companion.core.companion

import com.xiaoqi.companion.core.companion.model.InteractionSignal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RelationshipModelImpl @Inject constructor() : RelationshipModel {

    override var currentLevel: Float = 0f
        private set

    override suspend fun update(signal: InteractionSignal) {
        currentLevel = (currentLevel + signal.affinityDelta).coerceIn(0f, 1f)
    }

    override fun contextModifier(): String {
        val desc = when {
            currentLevel >= 0.8f -> "非常亲密"
            currentLevel >= 0.5f -> "比较熟悉"
            currentLevel >= 0.2f -> "刚认识不久"
            else -> "陌生人"
        }
        return "关系等级：$desc(${String.format("%.2f", currentLevel)})"
    }
}
