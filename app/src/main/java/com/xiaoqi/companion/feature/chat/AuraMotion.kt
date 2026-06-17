package com.xiaoqi.companion.feature.chat

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xiaoqi.companion.ui.theme.ChatStatusColors
import androidx.compose.animation.core.Spring

internal object AuraMotion {
    const val ShortMs = 180
    const val MediumMs = 260
    const val LongMs = 420

    val emphasizedTween: FiniteAnimationSpec<Float> =
        tween(durationMillis = MediumMs, easing = FastOutSlowInEasing)

    val gentleTween: FiniteAnimationSpec<Float> =
        tween(durationMillis = LongMs, easing = FastOutSlowInEasing)

    val colorSpring: FiniteAnimationSpec<Color> =
        spring(
            stiffness = Spring.StiffnessLow,
            dampingRatio = Spring.DampingRatioNoBouncy,
        )
}

@Composable
internal fun AuraLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = ChatStatusColors.SuccessDot,
    secondaryColor: Color = color.copy(alpha = 0.35f),
    size: androidx.compose.ui.unit.Dp = 18.dp,
    contentDescription: String? = null,
) {
    val transition = rememberInfiniteTransition(label = "aura-loading")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = AuraMotion.LongMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "aura-loading-progress",
    )

    Row(
        modifier = if (contentDescription != null) {
            modifier.semantics { this.contentDescription = contentDescription }
        } else {
            modifier
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            drawAuraLoadingDots(t = t, color = color, secondaryColor = secondaryColor)
        }
        Spacer(Modifier.width(6.dp))
        Canvas(modifier = Modifier.size(size)) {
            drawAuraLoadingDots(t = (t + 0.33f) % 1f, color = secondaryColor, secondaryColor = color.copy(alpha = 0.18f))
        }
        Spacer(Modifier.width(6.dp))
        Canvas(modifier = Modifier.size(size)) {
            drawAuraLoadingDots(t = (t + 0.66f) % 1f, color = color, secondaryColor = secondaryColor)
        }
    }
}

private fun DrawScope.drawAuraLoadingDots(
    t: Float,
    color: Color,
    secondaryColor: Color,
) {
    val width = size.width
    val height = size.height
    val baseY = height * 0.5f
    val radius = width * 0.14f
    repeat(3) { index ->
        val phase = (t + index * 0.18f) % 1f
        val scale = 0.68f + kotlin.math.sin(phase * Math.PI.toFloat()) * 0.22f
        val alpha = 0.42f + scale * 0.48f
        val dotColor = if (index == 1) color else secondaryColor
        drawCircle(
            color = dotColor.copy(alpha = alpha),
            radius = radius * scale,
            center = androidx.compose.ui.geometry.Offset(
                x = width * (0.18f + index * 0.32f),
                y = baseY + kotlin.math.sin((phase + index * 0.15f) * Math.PI.toFloat()) * height * 0.06f,
            ),
        )
    }
}
