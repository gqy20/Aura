package com.xiaoqi.companion.feature.chat.presence

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.xiaoqi.companion.core.presence.PresenceMode
import com.xiaoqi.companion.core.presence.PresenceReaction
import com.xiaoqi.companion.core.presence.PresenceUiState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 主屏 Presence 视觉层的背景与光晕（halo）渲染。
 *
 * 职责:
 * - 渐变背景的氛围光圈
 * - 角色背后的多层光晕圆环
 * - 各种 PresenceReaction 触发的光晕效果
 * - PresenceMode → 调色板的映射
 *
 * 与 [HomePresenceAvatar] 配合使用,后者在中央绘制 Aura 本体。
 */
@Composable
internal fun PresenceBackdropAndHalo(
    palette: HomePresencePalette,
    mode: PresenceMode,
    reaction: PresenceReaction?,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "presence-bg-halo")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000),
            repeatMode = RepeatMode.Restart,
        ),
        label = "drift",
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (mode == PresenceMode.THINKING) 1800 else 2600),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse",
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // --- Backdrop: ambient glow circles ---
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.glow.copy(alpha = 0.24f),
                    palette.glow.copy(alpha = 0.08f),
                    Color.Transparent,
                ),
                center = Offset(w * 0.52f, h * 0.39f),
                radius = w * 0.56f,
            ),
            radius = w * 0.56f,
            center = Offset(w * 0.52f, h * 0.39f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFD9E3BC).copy(alpha = 0.18f), Color.Transparent),
                center = Offset(w * -0.04f, h * 0.24f),
                radius = w * 0.32f,
            ),
            radius = w * 0.32f,
            center = Offset(w * -0.04f, h * 0.24f),
        )
        repeat(11) { index ->
            val angle = (drift * 2f * PI + index * 0.74f).toFloat()
            val x = w * (0.2f + (index % 5) * 0.16f) + cos(angle) * 10f
            val y = h * (0.22f + (index % 4) * 0.12f) + sin(angle * 0.7f) * 14f
            drawCircle(
                color = palette.spark.copy(alpha = 0.12f + (index % 3) * 0.045f),
                radius = 2.4f + (index % 3) * 1.4f,
                center = Offset(x, y),
            )
        }

        // --- Halo: glow + rings + reaction + orbiting particles ---
        val haloCenter = Offset(w / 2f, h * 0.56f)
        val reactionBoost = reaction.haloBoost()
        val baseRadius = w * (0.29f + pulse * (0.015f + reactionBoost * 0.012f))

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.glow.copy(alpha = 0.34f + reactionBoost * 0.16f),
                    palette.glow.copy(alpha = 0.10f + reactionBoost * 0.06f),
                    Color.Transparent,
                ),
                center = haloCenter,
                radius = w * 0.36f,
            ),
            radius = w * 0.36f,
            center = haloCenter,
        )
        repeat(3) { index ->
            drawCircle(
                color = palette.ring.copy(alpha = 0.20f - index * 0.045f + reactionBoost * 0.06f),
                radius = baseRadius + index * w * 0.064f,
                center = haloCenter.copy(y = haloCenter.y + h * 0.22f),
                style = Stroke(width = 1.1.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        reaction?.let {
            drawReactionHalo(
                reaction = it,
                palette = palette,
                pulse = pulse,
                width = w,
                height = h,
                center = haloCenter,
            )
        }
        if (mode == PresenceMode.THINKING || mode == PresenceMode.SEARCHING || mode == PresenceMode.REMEMBERING) {
            repeat(3) { index ->
                val angle = (pulse * 2f * PI + index * 2.09f).toFloat()
                drawCircle(
                    color = palette.spark.copy(alpha = 0.38f),
                    radius = 4.2f,
                    center = Offset(
                        x = haloCenter.x + cos(angle) * w * 0.21f,
                        y = haloCenter.y + sin(angle) * h * 0.17f,
                    ),
                )
            }
        }
    }
}

private fun DrawScope.drawReactionHalo(
    reaction: PresenceReaction,
    palette: HomePresencePalette,
    pulse: Float,
    width: Float,
    height: Float,
    center: Offset,
) {
    when (reaction) {
        PresenceReaction.RETURN_BLINK -> {
            drawCircle(
                color = palette.ring.copy(alpha = 0.18f * (1f - pulse)),
                radius = width * (0.24f + pulse * 0.22f),
                center = center.copy(y = center.y + height * 0.21f),
                style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        PresenceReaction.MEMORY_SPARK -> {
            repeat(4) { index ->
                val angle = (pulse * 2f * PI + index * 1.57f).toFloat()
                drawCircle(
                    color = palette.spark.copy(alpha = 0.34f * (1f - pulse * 0.35f)),
                    radius = 3.2f + index,
                    center = Offset(
                        x = center.x + cos(angle) * width * (0.14f + index * 0.025f),
                        y = center.y + sin(angle) * height * 0.12f,
                    ),
                )
            }
        }
        PresenceReaction.SEARCH_SWEEP -> {
            drawArc(
                color = palette.spark.copy(alpha = 0.32f),
                startAngle = -28f + pulse * 240f,
                sweepAngle = 58f,
                useCenter = false,
                topLeft = Offset(width * 0.23f, height * 0.24f),
                size = Size(width * 0.54f, height * 0.46f),
                style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        PresenceReaction.ERROR_RECOVER -> {
            drawCircle(
                color = palette.ring.copy(alpha = 0.16f * (1f - pulse)),
                radius = width * (0.30f + pulse * 0.08f),
                center = center.copy(y = center.y + height * 0.21f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        PresenceReaction.TOUCH_NUZZLE -> {
            drawCircle(
                color = Color.White.copy(alpha = 0.34f * (1f - pulse)),
                radius = width * (0.12f + pulse * 0.05f),
                center = Offset(center.x, center.y - height * 0.02f),
            )
        }
    }
}

internal data class HomePresencePalette(
    val backgroundTint: Color,
    val glow: Color,
    val ring: Color,
    val spark: Color,
)

@Composable
internal fun HomePresencePalette.animated(): HomePresencePalette {
    val animSpec = spring<Color>(stiffness = Spring.StiffnessLow)
    val bg by animateColorAsState(backgroundTint, animSpec, label = "pal-bg")
    val g by animateColorAsState(glow, animSpec, label = "pal-glow")
    val r by animateColorAsState(ring, animSpec, label = "pal-ring")
    val s by animateColorAsState(spark, animSpec, label = "pal-spark")
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

private fun PresenceReaction?.haloBoost(): Float =
    when (this) {
        PresenceReaction.ERROR_RECOVER -> 0.9f
        PresenceReaction.MEMORY_SPARK -> 0.7f
        PresenceReaction.SEARCH_SWEEP -> 0.55f
        PresenceReaction.RETURN_BLINK -> 0.38f
        PresenceReaction.TOUCH_NUZZLE -> 0.28f
        null -> 0f
    }
