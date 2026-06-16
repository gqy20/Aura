package com.xiaoqi.companion.feature.chat.presence

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import com.xiaoqi.companion.core.presence.PresenceAnimationState
import com.xiaoqi.companion.core.presence.PresenceMode
import com.xiaoqi.companion.core.presence.PresenceReaction
import com.xiaoqi.companion.core.presence.PresenceUiState
import com.xiaoqi.companion.feature.chat.HomePresencePalette
import com.xiaoqi.companion.feature.chat.homePalette
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
    animationState: PresenceAnimationState,
    mode: PresenceMode = PresenceMode.IDLE,
    reaction: PresenceReaction? = null,
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
            animation = tween(durationMillis = animationState.pulseDurationMillis),
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
        // 居中对齐 Aura 角色本体(Avatar Canvas 中心在 w/2, h/2)。
        val haloCenter = Offset(w / 2f, h * 0.5f)
        val reactionBoost = animationState.haloBoost
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
                center = haloCenter,
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
            repeat(animationState.orbitParticleCount.coerceAtLeast(3)) { index ->
                val angle = (pulse * 2f * PI + index * 2.09f).toFloat()
                drawCircle(
                    color = palette.spark.copy(alpha = 0.38f),
                    radius = 4.2f,
                    center = Offset(
                        x = haloCenter.x + cos(angle) * w * (0.21f * animationState.orbitRadiusScale),
                        y = haloCenter.y + sin(angle) * h * (0.17f * animationState.orbitRadiusScale),
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
    // 反应光环与 ring 共享同一个 haloCenter(已对齐 Aura 角色),不再额外 y 偏移。
    when (reaction) {
        PresenceReaction.RETURN_BLINK -> {
            drawCircle(
                color = palette.ring.copy(alpha = 0.18f * (1f - pulse)),
                radius = width * (0.24f + pulse * 0.22f),
                center = center,
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
                center = center,
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
