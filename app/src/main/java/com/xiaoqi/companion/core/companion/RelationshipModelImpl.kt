package com.xiaoqi.companion.core.companion

import com.xiaoqi.companion.core.companion.model.InteractionSignal
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "Companion-Relation"

@Singleton
class RelationshipModelImpl @Inject constructor() : RelationshipModel {

    override var currentLevel: Float = 0f
        private set

    override suspend fun update(signal: InteractionSignal) {
        val previous = currentLevel
        currentLevel = (currentLevel + signal.affinityDelta).coerceIn(0f, 1f)
        if (signal.affinityDelta != 0f) {
            Timber.tag(TAG).d("Relationship: %.2f -> %.2f (delta=%.2f)", previous, currentLevel, signal.affinityDelta)
        }
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
