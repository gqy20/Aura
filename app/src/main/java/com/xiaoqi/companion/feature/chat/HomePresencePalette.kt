package com.xiaoqi.companion.feature.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.xiaoqi.companion.core.presence.PresenceMode
import com.xiaoqi.companion.core.presence.PresenceReaction
import com.xiaoqi.companion.core.presence.PresenceUiState

internal data class HomePresencePalette(
    val backgroundTint: Color,
    val glow: Color,
    val ring: Color,
    val spark: Color,
)

@Composable
internal fun HomePresencePalette.animated(): HomePresencePalette {
    val bg by animateColorAsState(backgroundTint, AuraMotion.colorSpring, label = "pal-bg")
    val g by animateColorAsState(glow, AuraMotion.colorSpring, label = "pal-glow")
    val r by animateColorAsState(ring, AuraMotion.colorSpring, label = "pal-ring")
    val s by animateColorAsState(spark, AuraMotion.colorSpring, label = "pal-spark")
    return HomePresencePalette(bg, g, r, s)
}

internal fun PresenceUiState.homePalette(): HomePresencePalette =
    when (mode) {
        PresenceMode.HAPPY -> HomePresencePalette(
            backgroundTint = Color(0xFFFFF6E2),
            glow = Color(0xFFFFD884),
            ring = Color(0xFFD5AF62),
            spark = Color(0xFFFFC857),
        )
        PresenceMode.THINKING, PresenceMode.SEARCHING -> HomePresencePalette(
            backgroundTint = Color(0xFFF0F8F4),
            glow = Color(0xFF9BEAE5),
            ring = Color(0xFF7EB8AF),
            spark = Color(0xFF74DDE0),
        )
        PresenceMode.REMEMBERING -> HomePresencePalette(
            backgroundTint = Color(0xFFF7F0FA),
            glow = Color(0xFFCDB4F6),
            ring = Color(0xFFA892C7),
            spark = Color(0xFFB895F2),
        )
        PresenceMode.SAD, PresenceMode.TIRED, PresenceMode.SLEEPING -> HomePresencePalette(
            backgroundTint = Color(0xFFF2F5F8),
            glow = Color(0xFFAFC8E8),
            ring = Color(0xFF8DA3BF),
            spark = Color(0xFF9FBDE8),
        )
        PresenceMode.ERROR -> HomePresencePalette(
            backgroundTint = Color(0xFFFFF2EF),
            glow = Color(0xFFE9A39D),
            ring = Color(0xFFC3847E),
            spark = Color(0xFFE07C73),
        )
        PresenceMode.LISTENING, PresenceMode.SPEAKING, PresenceMode.IDLE -> HomePresencePalette(
            backgroundTint = Color(0xFFFFF9ED),
            glow = Color(0xFFA8E7DE),
            ring = Color(0xFFB8C8AA),
            spark = Color(0xFFFFD17C),
        )
    }

internal fun PresenceReaction?.haloBoost(): Float =
    when (this) {
        PresenceReaction.ERROR_RECOVER -> 0.9f
        PresenceReaction.MEMORY_SPARK -> 0.7f
        PresenceReaction.SEARCH_SWEEP -> 0.55f
        PresenceReaction.RETURN_BLINK -> 0.38f
        PresenceReaction.TOUCH_NUZZLE -> 0.28f
        null -> 0f
    }
