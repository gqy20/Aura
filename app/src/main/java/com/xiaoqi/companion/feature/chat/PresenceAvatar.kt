package com.xiaoqi.companion.feature.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xiaoqi.companion.core.presence.PresenceMode
import com.xiaoqi.companion.core.presence.PresenceReaction
import com.xiaoqi.companion.core.presence.PresenceUiState
import kotlin.math.sin

@Composable
fun PresenceAvatar(
    presence: PresenceUiState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val transition = rememberInfiniteTransition(label = "presence")
    val breath by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse",
    )

    val palette = presence.palette()
    val reactionScale = when (presence.reaction) {
        PresenceReaction.TOUCH_NUZZLE -> 1.08f + sin(pulse * 6.28f) * 0.018f
        PresenceReaction.RETURN_BLINK -> 1.03f
        PresenceReaction.MEMORY_SPARK -> 1.05f
        PresenceReaction.SEARCH_SWEEP -> 1.02f
        PresenceReaction.ERROR_RECOVER -> 0.98f + sin(pulse * 6.28f) * 0.012f
        null -> 1f
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(54.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = presence.label },
    ) {
        Canvas(
            modifier = Modifier
                .size(54.dp)
                .scale((if (presence.mode == PresenceMode.SPEAKING) 1f + sin(pulse * 6.28f) * 0.025f else breath) * reactionScale),
        ) {
            val w = size.width
            val h = size.height
            val center = Offset(w / 2f, h / 2f)
            val glowRadius = w * (0.46f + pulse * 0.06f)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(palette.glow.copy(alpha = 0.34f), Color.Transparent),
                    center = center,
                    radius = glowRadius,
                ),
                radius = glowRadius,
                center = center,
            )
            drawOval(
                brush = Brush.linearGradient(
                    colors = listOf(palette.faceTop, palette.faceBottom),
                    start = Offset(w * 0.28f, h * 0.12f),
                    end = Offset(w * 0.76f, h * 0.9f),
                ),
                topLeft = Offset(w * 0.16f, h * 0.13f),
                size = Size(w * 0.68f, h * 0.74f),
            )
            drawOval(
                color = palette.stroke.copy(alpha = 0.42f),
                topLeft = Offset(w * 0.16f, h * 0.13f),
                size = Size(w * 0.68f, h * 0.74f),
                style = Stroke(width = w * 0.035f),
            )

            drawPresenceEyes(
                mode = presence.mode,
                reaction = presence.reaction,
                color = palette.feature,
                pulse = pulse,
                width = w,
                height = h,
            )
            drawPresenceMouth(
                mode = presence.mode,
                color = palette.feature.copy(alpha = 0.76f),
                pulse = pulse,
                width = w,
                height = h,
            )

            if (presence.mode == PresenceMode.SEARCHING || presence.mode == PresenceMode.REMEMBERING) {
                val dotColor = if (presence.mode == PresenceMode.SEARCHING) palette.accent else palette.glow
                drawCircle(dotColor.copy(alpha = 0.72f), w * 0.045f, Offset(w * (0.23f + pulse * 0.08f), h * 0.24f))
                drawCircle(dotColor.copy(alpha = 0.52f), w * 0.032f, Offset(w * (0.78f - pulse * 0.06f), h * 0.74f))
            }
            drawPresenceReaction(
                reaction = presence.reaction,
                palette = palette,
                pulse = pulse,
                width = w,
                height = h,
            )
        }
    }
}

private data class PresencePalette(
    val faceTop: Color,
    val faceBottom: Color,
    val stroke: Color,
    val feature: Color,
    val glow: Color,
    val accent: Color,
)

@Composable
private fun PresenceUiState.palette(): PresencePalette {
    val scheme = MaterialTheme.colorScheme
    return when (mode) {
        PresenceMode.HAPPY -> PresencePalette(
            faceTop = Color(0xFFFFF0B8),
            faceBottom = Color(0xFFFFC6A8),
            stroke = Color(0xFFD98C5F),
            feature = Color(0xFF47302A),
            glow = Color(0xFFFFC857),
            accent = scheme.primary,
        )
        PresenceMode.SAD, PresenceMode.TIRED -> PresencePalette(
            faceTop = Color(0xFFDDE8FF),
            faceBottom = Color(0xFFBFD1EA),
            stroke = Color(0xFF7188B5),
            feature = Color(0xFF253552),
            glow = Color(0xFF86A8E7),
            accent = scheme.secondary,
        )
        PresenceMode.ERROR -> PresencePalette(
            faceTop = Color(0xFFFFDAD6),
            faceBottom = Color(0xFFF2B8B5),
            stroke = Color(0xFFBA1A1A),
            feature = Color(0xFF410002),
            glow = Color(0xFFE89B96),
            accent = scheme.error,
        )
        PresenceMode.SEARCHING -> PresencePalette(
            faceTop = Color(0xFFD6F3FF),
            faceBottom = Color(0xFFB4E2E3),
            stroke = Color(0xFF3F8E9F),
            feature = Color(0xFF173B43),
            glow = Color(0xFF64D2E7),
            accent = Color(0xFF1E88A8),
        )
        PresenceMode.REMEMBERING -> PresencePalette(
            faceTop = Color(0xFFE9DDFB),
            faceBottom = Color(0xFFD5C2EF),
            stroke = Color(0xFF7A62A8),
            feature = Color(0xFF35234B),
            glow = Color(0xFFB895F2),
            accent = Color(0xFF7E57C2),
        )
        else -> PresencePalette(
            faceTop = scheme.primaryContainer,
            faceBottom = scheme.tertiaryContainer,
            stroke = scheme.primary,
            feature = scheme.onPrimaryContainer,
            glow = scheme.primary,
            accent = scheme.tertiary,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPresenceEyes(
    mode: PresenceMode,
    reaction: PresenceReaction?,
    color: Color,
    pulse: Float,
    width: Float,
    height: Float,
) {
    val eyeY = height * 0.43f
    val leftX = width * 0.38f
    val rightX = width * 0.62f
    when {
        reaction == PresenceReaction.RETURN_BLINK || reaction == PresenceReaction.TOUCH_NUZZLE -> {
            val openness = if (pulse < 0.45f) 0.02f else 0.09f
            drawOval(color, Offset(leftX - width * 0.04f, eyeY - height * openness / 2f), Size(width * 0.08f, height * openness))
            drawOval(color, Offset(rightX - width * 0.04f, eyeY - height * openness / 2f), Size(width * 0.08f, height * openness))
        }
        mode == PresenceMode.SLEEPING || mode == PresenceMode.TIRED -> {
            drawLine(color, Offset(width * 0.31f, eyeY), Offset(width * 0.45f, eyeY + height * 0.025f), strokeWidth = width * 0.035f, cap = StrokeCap.Round)
            drawLine(color, Offset(width * 0.55f, eyeY + height * 0.025f), Offset(width * 0.69f, eyeY), strokeWidth = width * 0.035f, cap = StrokeCap.Round)
        }
        mode == PresenceMode.HAPPY -> {
            drawArc(
                color = color,
                startAngle = 195f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(width * 0.31f, height * 0.37f),
                size = Size(width * 0.14f, height * 0.15f),
                style = Stroke(width * 0.035f, cap = StrokeCap.Round),
            )
            drawArc(
                color = color,
                startAngle = 195f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(width * 0.55f, height * 0.37f),
                size = Size(width * 0.14f, height * 0.15f),
                style = Stroke(width * 0.035f, cap = StrokeCap.Round),
            )
        }
        mode == PresenceMode.THINKING || mode == PresenceMode.SEARCHING || reaction == PresenceReaction.SEARCH_SWEEP -> {
            val offset = (pulse - 0.5f) * width * 0.045f
            drawCircle(color, width * 0.035f, Offset(leftX + offset, eyeY))
            drawCircle(color, width * 0.035f, Offset(rightX + offset, eyeY))
        }
        else -> {
            drawOval(color, Offset(leftX - width * 0.035f, eyeY - height * 0.045f), Size(width * 0.07f, height * 0.09f))
            drawOval(color, Offset(rightX - width * 0.035f, eyeY - height * 0.045f), Size(width * 0.07f, height * 0.09f))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPresenceReaction(
    reaction: PresenceReaction?,
    palette: PresencePalette,
    pulse: Float,
    width: Float,
    height: Float,
) {
    when (reaction) {
        PresenceReaction.MEMORY_SPARK -> {
            val radius = width * (0.04f + pulse * 0.04f)
            drawCircle(palette.glow.copy(alpha = 0.82f - pulse * 0.35f), radius, Offset(width * 0.74f, height * 0.24f))
            drawCircle(palette.accent.copy(alpha = 0.62f), width * 0.026f, Offset(width * (0.68f - pulse * 0.18f), height * (0.3f + pulse * 0.28f)))
        }
        PresenceReaction.SEARCH_SWEEP -> {
            val x = width * (0.22f + pulse * 0.56f)
            drawLine(
                color = palette.accent.copy(alpha = 0.34f),
                start = Offset(x, height * 0.22f),
                end = Offset(x + width * 0.08f, height * 0.78f),
                strokeWidth = width * 0.025f,
                cap = StrokeCap.Round,
            )
        }
        PresenceReaction.TOUCH_NUZZLE -> {
            drawCircle(palette.glow.copy(alpha = 0.28f), width * 0.08f, Offset(width * 0.82f, height * 0.34f))
        }
        PresenceReaction.ERROR_RECOVER -> {
            drawArc(
                color = palette.accent.copy(alpha = 0.5f),
                startAngle = -20f,
                sweepAngle = 220f * pulse,
                useCenter = false,
                topLeft = Offset(width * 0.2f, height * 0.17f),
                size = Size(width * 0.6f, height * 0.66f),
                style = Stroke(width * 0.024f, cap = StrokeCap.Round),
            )
        }
        PresenceReaction.RETURN_BLINK, null -> Unit
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPresenceMouth(
    mode: PresenceMode,
    color: Color,
    pulse: Float,
    width: Float,
    height: Float,
) {
    when (mode) {
        PresenceMode.SPEAKING -> {
            val mouthH = height * (0.05f + pulse * 0.045f)
            drawOval(color.copy(alpha = 0.74f), Offset(width * 0.45f, height * 0.61f), Size(width * 0.1f, mouthH))
        }
        PresenceMode.HAPPY -> {
            drawArc(
                color = color,
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(width * 0.4f, height * 0.55f),
                size = Size(width * 0.2f, height * 0.17f),
                style = Stroke(width * 0.03f, cap = StrokeCap.Round),
            )
        }
        PresenceMode.SAD, PresenceMode.ERROR -> {
            drawArc(
                color = color,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(width * 0.4f, height * 0.62f),
                size = Size(width * 0.2f, height * 0.16f),
                style = Stroke(width * 0.03f, cap = StrokeCap.Round),
            )
        }
        PresenceMode.REMEMBERING -> {
            val path = Path().apply {
                moveTo(width * 0.43f, height * 0.64f)
                quadraticTo(width * 0.5f, height * 0.69f, width * 0.57f, height * 0.64f)
            }
            drawPath(path, color, style = Stroke(width * 0.03f, cap = StrokeCap.Round))
        }
        else -> {
            drawLine(color, Offset(width * 0.44f, height * 0.64f), Offset(width * 0.56f, height * 0.64f), strokeWidth = width * 0.025f, cap = StrokeCap.Round)
        }
    }
}
