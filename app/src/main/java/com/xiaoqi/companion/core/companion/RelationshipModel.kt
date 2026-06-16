package com.xiaoqi.companion.core.companion

import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RelationshipModel @Inject constructor() {

    var currentLevel: Float = 0f
        private set

    suspend fun applyDelta(delta: Float, reason: String = "") {
        if (delta == 0f) return
        val previous = currentLevel
        currentLevel = (currentLevel + delta).coerceIn(0f, 1f)
        AppLogger.debug(
            LogTags.Relation,
            "relationship_level_changed",
            "previousLevel" to previous,
            "currentLevel" to currentLevel,
            "delta" to delta,
            "reason" to reason,
        )
    }

    fun contextModifier(): String {
        val desc = when {
            currentLevel >= 0.8f -> "闈炲父浜插瘑"
            currentLevel >= 0.5f -> "姣旇緝鐔熸倝"
            currentLevel >= 0.2f -> "鍒氳璇嗕笉涔?"
            else -> "闄岀敓浜?"
        }
        return "鍏崇郴绛夌骇锛?desc(${String.format("%.2f", currentLevel)})"
    }
}
