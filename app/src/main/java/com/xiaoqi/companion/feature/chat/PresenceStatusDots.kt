package com.xiaoqi.companion.feature.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xiaoqi.companion.core.presence.PresenceMode
import kotlin.math.PI
import kotlin.math.sin

/**
 * 替代文字 subtitle 的动画指示器：3 个小圆点，按 PresenceMode 切换动画模式与颜色。
 *
 * 模式：
 * - IDLE/HAPPY/SAD/TIRED/SLEEPING：单次柔和呼吸
 * - LISTENING：波浪式跳动（iMessage 风格）
 * - THINKING/SPEAKING：三圆同步脉冲
 * - SEARCHING/REMEMBERING：来回扫描
 * - ERROR：闪烁
 */
@Composable
internal fun PresenceStatusDots(
    mode: PresenceMode,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "status-dots")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = mode.dotDurationMillis(),
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "dot-t",
    )

    val color = mode.dotColor()
    val pattern = mode.dotPattern()

    Canvas(
        modifier = modifier
            .width(24.dp)
            .height(8.dp),
    ) {
        val w = size.width
        val h = size.height
        val baseRadius = h * 0.22f
        val spacing = w / 4f

        repeat(3) { i ->
            val cx = spacing * (i + 1)
            val scale = when (pattern) {
                DotPattern.BREATH -> {
                    val breath = (sin(t * 2f * PI).toFloat() + 1f) / 2f
                    0.7f + 0.3f * breath
                }
                DotPattern.WAVE -> {
                    val phase = (t * 2f * PI).toFloat() - i * 1.2f
                    0.6f + 0.4f * ((sin(phase.toDouble()).toFloat() + 1f) / 2f)
                }
                DotPattern.PULSE -> {
                    0.6f + 0.4f * ((sin(t * 2f * PI).toFloat() + 1f) / 2f)
                }
                DotPattern.SWEEP -> {
                    val sweepPos = (sin(t * 2f * PI).toFloat() + 1f) / 2f
                    val dist = kotlin.math.abs(sweepPos - i / 2f)
                    0.5f + 0.5f * (1f - dist).coerceAtLeast(0f)
                }
                DotPattern.BLINK -> {
                    if (t < 0.5f) 1f else 0.3f
                }
            }
            val alpha = when (pattern) {
                DotPattern.BREATH -> 0.5f + 0.5f * scale
                DotPattern.BLINK -> if (t < 0.5f) 0.9f else 0.25f
                else -> 0.6f + 0.4f * scale
            }
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = baseRadius * scale,
                center = Offset(cx, h / 2f),
            )
        }
    }
}

private enum class DotPattern { BREATH, WAVE, PULSE, SWEEP, BLINK }

private fun PresenceMode.dotPattern(): DotPattern = when (this) {
    PresenceMode.IDLE, PresenceMode.HAPPY, PresenceMode.SAD,
    PresenceMode.TIRED, PresenceMode.SLEEPING -> DotPattern.BREATH
    PresenceMode.LISTENING -> DotPattern.WAVE
    PresenceMode.THINKING, PresenceMode.SPEAKING -> DotPattern.PULSE
    PresenceMode.SEARCHING, PresenceMode.REMEMBERING -> DotPattern.SWEEP
    PresenceMode.ERROR -> DotPattern.BLINK
}

private fun PresenceMode.dotDurationMillis(): Int = when (this) {
    PresenceMode.IDLE -> 3200
    PresenceMode.HAPPY -> 2800
    PresenceMode.SAD, PresenceMode.TIRED, PresenceMode.SLEEPING -> 3600
    PresenceMode.LISTENING -> 1400
    PresenceMode.THINKING -> 1600
    PresenceMode.SPEAKING -> 1200
    PresenceMode.SEARCHING -> 1800
    PresenceMode.REMEMBERING -> 2200
    PresenceMode.ERROR -> 800
}

@Composable
private fun PresenceMode.dotColor(): Color = when (this) {
    PresenceMode.IDLE -> Color(0xFFB8C8AA)
    PresenceMode.HAPPY -> Color(0xFFFFC857)
    PresenceMode.SAD -> Color(0xFF8DA3BF)
    PresenceMode.TIRED, PresenceMode.SLEEPING -> Color(0xFFAFC8E8)
    PresenceMode.LISTENING -> Color(0xFF7EB8AF)
    PresenceMode.THINKING -> Color(0xFF74DDE0)
    PresenceMode.SPEAKING -> Color(0xFF9BEAE5)
    PresenceMode.SEARCHING -> Color(0xFF64D2E7)
    PresenceMode.REMEMBERING -> Color(0xFFB895F2)
    PresenceMode.ERROR -> Color(0xFFE07C73)
}
