package com.xiaoqi.companion.feature.chat.presence

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
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
 * 主屏中央的"发光 Aura"角色 Composable。
 *
 * 由 [drawAuraEar] / [drawAuraBody] / [drawAuraFace] / [drawAuraFlame] / [drawAuraBellyStar] 拼装:
 * 1. 头两侧的耳
 * 2. 身体与肚子星
 * 3. 脸(眼睛/腮红/嘴) —— 随 PresenceMode 变化
 * 4. 头顶的小火焰
 *
 * 注意:
 * - [Path] 缓存(flamePath / starPath)与 `Animatable(1f)` tapScale 都必须在 Composable 函数体内 `remember`,
 *   不能下沉到子 DrawScope 扩展,否则每次重组都重建丢失缓存。
 */
@Composable
internal fun LuminousAuraAvatar(
    presence: PresenceUiState,
    animationState: PresenceAnimationState,
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "luminous-aura")
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = animationState.breathDurationMillis),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = animationState.shimmerDurationMillis),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer",
    )
    val palette = presence.homePalette()

    // Touch elastic feedback
    val tapScale = remember { Animatable(1f) }
    val flamePath = remember { Path() }
    val starPath = remember { Path() }

    Canvas(
        modifier = modifier
            .size(size)
            .scale(tapScale.value)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        tapScale.animateTo(
                            animationState.tapScaleTarget,
                            spring(stiffness = Spring.StiffnessHigh),
                        )
                        tryAwaitRelease()
                        tapScale.animateTo(
                            1f,
                            spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        )
                    },
                    onTap = { onClick() },
                )
            },
    ) {
        val w = this.size.width
        val h = this.size.height
        val center = Offset(w / 2f, h / 2f)
        val breatheScale = 1f + breath * 0.018f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.glow.copy(alpha = 0.34f),
                    palette.glow.copy(alpha = 0.13f),
                    Color.Transparent,
                ),
                center = center,
                radius = w * 0.56f,
            ),
            radius = w * 0.56f,
            center = center,
        )

        drawAuraEar(
            left = true,
            palette = palette,
            breath = breath,
            width = w,
            height = h,
        )
        drawAuraEar(
            left = false,
            palette = palette,
            breath = breath,
            width = w,
            height = h,
        )

        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFFFFF).copy(alpha = 0.98f),
                    Color(0xFFFFEFC6),
                    Color(0xFFF4CFA3).copy(alpha = 0.9f),
                ),
                center = Offset(w * 0.47f, h * 0.31f),
                radius = w * 0.5f,
            ),
            topLeft = Offset(w * 0.18f, h * (0.18f - breath * 0.01f)),
            size = Size(w * 0.64f * breatheScale, h * 0.54f * breatheScale),
        )
        drawOval(
            color = Color.White.copy(alpha = 0.44f),
            topLeft = Offset(w * 0.26f, h * 0.22f),
            size = Size(w * 0.2f, h * 0.08f),
        )

        drawAuraBody(
            palette = palette,
            breath = breath,
            width = w,
            height = h,
        )
        drawAuraFace(
            mode = presence.mode,
            reaction = presence.reaction,
            shimmer = shimmer,
            width = w,
            height = h,
        )
        drawAuraFlame(
            palette = palette,
            shimmer = shimmer,
            width = w,
            height = h,
            cachedFlame = flamePath,
        )
        drawAuraBellyStar(
            palette = palette,
            shimmer = shimmer,
            width = w,
            height = h,
            cachedStar = starPath,
        )

        if (presence.mode == PresenceMode.THINKING ||
            presence.mode == PresenceMode.SEARCHING ||
            presence.mode == PresenceMode.REMEMBERING ||
            presence.reaction == PresenceReaction.MEMORY_SPARK ||
            presence.reaction == PresenceReaction.SEARCH_SWEEP
        ) {
            repeat(animationState.orbitParticleCount) { index ->
                val angle = (shimmer * 2f * PI + index * 1.26f).toFloat()
                drawCircle(
                    color = palette.spark.copy(alpha = 0.34f),
                    radius = w * (0.012f + index % 2 * 0.004f),
                    center = Offset(
                        x = center.x + cos(angle) * w * ((0.31f + index * 0.012f) * animationState.orbitRadiusScale),
                        y = center.y + sin(angle) * h * (0.28f * animationState.orbitRadiusScale),
                    ),
                )
            }
        }
    }
}

private fun DrawScope.drawAuraEar(
    left: Boolean,
    palette: HomePresencePalette,
    breath: Float,
    width: Float,
    height: Float,
) {
    val side = if (left) -1f else 1f
    val cx = width * (0.5f + side * 0.35f)
    val cy = height * (0.48f + breath * 0.012f)
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.88f),
                palette.glow.copy(alpha = 0.32f),
                Color(0xFFF6D4AB).copy(alpha = 0.48f),
            ),
            center = Offset(cx - side * width * 0.04f, cy - height * 0.03f),
            radius = width * 0.18f,
        ),
        topLeft = Offset(cx - width * 0.085f, cy - height * 0.13f),
        size = Size(width * 0.17f, height * 0.28f),
    )
    drawOval(
        color = palette.glow.copy(alpha = 0.28f),
        topLeft = Offset(cx - width * 0.055f, cy + height * 0.015f),
        size = Size(width * 0.11f, height * 0.12f),
        style = Stroke(width = width * 0.012f),
    )
}

private fun DrawScope.drawAuraBody(
    palette: HomePresencePalette,
    breath: Float,
    width: Float,
    height: Float,
) {
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.9f),
                Color(0xFFFFE9B9).copy(alpha = 0.92f),
                Color(0xFFEFC59D).copy(alpha = 0.62f),
            ),
            center = Offset(width * 0.5f, height * 0.66f),
            radius = width * 0.36f,
        ),
        topLeft = Offset(width * 0.31f, height * (0.58f - breath * 0.008f)),
        size = Size(width * 0.38f, height * 0.32f),
    )
    drawOval(
        color = Color(0xFFEFCDA5).copy(alpha = 0.58f),
        topLeft = Offset(width * 0.22f, height * 0.78f),
        size = Size(width * 0.2f, height * 0.12f),
    )
    drawOval(
        color = Color(0xFFEFCDA5).copy(alpha = 0.58f),
        topLeft = Offset(width * 0.58f, height * 0.78f),
        size = Size(width * 0.2f, height * 0.12f),
    )
    drawCircle(
        color = palette.glow.copy(alpha = 0.22f),
        radius = width * 0.19f,
        center = Offset(width * 0.5f, height * 0.72f),
    )
}

private fun DrawScope.drawAuraFace(
    mode: PresenceMode,
    reaction: PresenceReaction?,
    shimmer: Float,
    width: Float,
    height: Float,
) {
    val eyeY = height * 0.45f
    val leftEye = Offset(width * 0.39f, eyeY)
    val rightEye = Offset(width * 0.61f, eyeY)
    val eyeColor = Color(0xFF3A342D)

    val shouldBlink = reaction == PresenceReaction.RETURN_BLINK ||
        reaction == PresenceReaction.TOUCH_NUZZLE ||
        mode == PresenceMode.SLEEPING ||
        mode == PresenceMode.TIRED

    if (shouldBlink) {
        drawLine(
            color = eyeColor.copy(alpha = 0.78f),
            start = Offset(leftEye.x - width * 0.045f, eyeY),
            end = Offset(leftEye.x + width * 0.045f, eyeY + height * 0.018f),
            strokeWidth = width * 0.013f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = eyeColor.copy(alpha = 0.78f),
            start = Offset(rightEye.x - width * 0.045f, eyeY + height * 0.018f),
            end = Offset(rightEye.x + width * 0.045f, eyeY),
            strokeWidth = width * 0.013f,
            cap = StrokeCap.Round,
        )
    } else {
        listOf(leftEye, rightEye).forEach { eye ->
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.92f),
                        eyeColor,
                        Color(0xFF0E1514),
                    ),
                    center = Offset(eye.x - width * 0.014f, eye.y - height * 0.018f),
                    radius = width * 0.05f,
                ),
                topLeft = Offset(eye.x - width * 0.042f, eye.y - height * 0.06f),
                size = Size(width * 0.084f, height * 0.12f),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.92f),
                radius = width * 0.011f,
                center = Offset(eye.x - width * 0.015f, eye.y - height * 0.03f),
            )
            drawCircle(
                color = Color(0xFF76E0D7).copy(alpha = 0.72f),
                radius = width * 0.012f,
                center = Offset(eye.x + width * 0.012f, eye.y + height * 0.035f + shimmer * height * 0.004f),
            )
        }
    }

    drawCircle(
        color = Color(0xFFFFB7A4).copy(alpha = 0.36f),
        radius = width * 0.036f,
        center = Offset(width * 0.31f, height * 0.53f),
    )
    drawCircle(
        color = Color(0xFFFFB7A4).copy(alpha = 0.36f),
        radius = width * 0.036f,
        center = Offset(width * 0.69f, height * 0.53f),
    )

    when (mode) {
        PresenceMode.SAD, PresenceMode.ERROR -> {
            drawArc(
                color = Color(0xFF9B694D),
                startAngle = 205f,
                sweepAngle = 130f,
                useCenter = false,
                topLeft = Offset(width * 0.455f, height * 0.58f),
                size = Size(width * 0.09f, height * 0.055f),
                style = Stroke(width = width * 0.01f, cap = StrokeCap.Round),
            )
        }
        PresenceMode.HAPPY -> {
            drawArc(
                color = Color(0xFF9B694D),
                startAngle = 24f,
                sweepAngle = 132f,
                useCenter = false,
                topLeft = Offset(width * 0.445f, height * 0.55f),
                size = Size(width * 0.11f, height * 0.07f),
                style = Stroke(width = width * 0.01f, cap = StrokeCap.Round),
            )
        }
        PresenceMode.REMEMBERING -> {
            drawCircle(
                color = Color(0xFFFFD17C).copy(alpha = 0.44f),
                radius = width * 0.018f,
                center = Offset(width * 0.5f, height * 0.59f),
            )
        }
        else -> {
            drawArc(
                color = Color(0xFF9B694D),
                startAngle = 30f,
                sweepAngle = 120f,
                useCenter = false,
                topLeft = Offset(width * 0.46f, height * 0.555f),
                size = Size(width * 0.08f, height * 0.048f),
                style = Stroke(width = width * 0.009f, cap = StrokeCap.Round),
            )
        }
    }
}

private fun DrawScope.drawAuraFlame(
    palette: HomePresencePalette,
    shimmer: Float,
    width: Float,
    height: Float,
    cachedFlame: Path,
) {
    cachedFlame.rewind()
    cachedFlame.apply {
        moveTo(width * 0.5f, height * 0.2f)
        cubicTo(width * 0.42f, height * 0.11f, width * 0.52f, height * 0.04f, width * 0.49f, height * 0.0f)
        cubicTo(width * 0.6f, height * 0.08f, width * 0.62f, height * 0.16f, width * 0.53f, height * 0.23f)
        cubicTo(width * 0.51f, height * 0.25f, width * 0.49f, height * 0.24f, width * 0.5f, height * 0.2f)
        close()
    }
    drawPath(
        path = cachedFlame,
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.82f),
                palette.glow.copy(alpha = 0.68f),
                Color(0xFF75E7E4).copy(alpha = 0.22f),
            ),
            center = Offset(width * 0.52f, height * (0.12f + shimmer * 0.018f)),
            radius = width * 0.16f,
        ),
    )
}

private fun DrawScope.drawAuraBellyStar(
    palette: HomePresencePalette,
    shimmer: Float,
    width: Float,
    height: Float,
    cachedStar: Path,
) {
    val center = Offset(width * 0.5f, height * 0.72f)
    drawCircle(
        color = Color.White.copy(alpha = 0.58f),
        radius = width * (0.084f + shimmer * 0.008f),
        center = center,
    )
    drawCircle(
        color = palette.spark.copy(alpha = 0.42f),
        radius = width * (0.052f + shimmer * 0.008f),
        center = center,
    )
    cachedStar.rewind()
    cachedStar.apply {
        moveTo(center.x, center.y - width * 0.042f)
        lineTo(center.x + width * 0.012f, center.y - width * 0.012f)
        lineTo(center.x + width * 0.044f, center.y)
        lineTo(center.x + width * 0.012f, center.y + width * 0.012f)
        lineTo(center.x, center.y + width * 0.044f)
        lineTo(center.x - width * 0.012f, center.y + width * 0.012f)
        lineTo(center.x - width * 0.044f, center.y)
        lineTo(center.x - width * 0.012f, center.y - width * 0.012f)
        close()
    }
    drawPath(cachedStar, Color.White.copy(alpha = 0.95f))
}
