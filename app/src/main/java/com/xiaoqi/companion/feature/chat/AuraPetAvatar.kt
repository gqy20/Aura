package com.xiaoqi.companion.feature.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiaoqi.companion.R
import com.xiaoqi.companion.core.presence.PresenceAnimationState
import com.xiaoqi.companion.core.presence.PresenceMode
import com.xiaoqi.companion.core.presence.PresenceUiState
import com.xiaoqi.companion.core.presence.animationState
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun AuraPetAvatar(
    presence: PresenceUiState,
    animationState: PresenceAnimationState = presence.animationState(),
    modifier: Modifier = Modifier,
    size: Dp = 54.dp,
    isLocalModel: Boolean = false,
    onClick: () -> Unit = {},
) {
    val sheet = ImageBitmap.imageResource(R.drawable.aura_pet_spritesheet)
    val timeline = presence.mode.toPetTimeline(animationState)
    var timeMillis by remember { mutableLongStateOf(0L) }

    LaunchedEffect(timeline) {
        while (true) {
            timeMillis = withInfiniteAnimationFrameMillis { it }
        }
    }

    val sprite = timeline.spriteAt(timeMillis)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clickable(onClick = onClick)
            .semantics { contentDescription = presence.label },
    ) {
        if (isLocalModel) {
            LocalModelGlow(modifier = Modifier.fillMaxSize())
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawSpriteFrame(
                sheet = sheet,
                sprite = sprite,
                frameWidth = PetFrameWidth,
                frameHeight = PetFrameHeight,
                columns = PetColumns,
            )
        }
    }
}

/** 本地模型呼吸光环：绿色脉冲 ring + 柔 radial glow，绘在 sprite 下层。 */
@Composable
private fun LocalModelGlow(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "local-model-glow")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "glow-pulse",
    )
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val center = Offset(w / 2f, h / 2f)
        // 呼吸因子：0.6 ~ 1.0 平滑正弦
        val breath = 0.6f + 0.4f * ((sin(pulse * 2f * PI).toFloat() + 1f) / 2f)
        val baseRadius = w * 0.42f
        val ringRadius = baseRadius * (0.92f + breath * 0.08f)
        // 外圈 ring
        drawCircle(
            color = LocalModelGlowColor.copy(alpha = 0.30f * breath),
            radius = ringRadius,
            center = center,
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round),
        )
        // 内层柔光
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    LocalModelGlowColor.copy(alpha = 0.18f * breath),
                    Color.Transparent,
                ),
                center = center,
                radius = baseRadius,
            ),
            radius = baseRadius,
            center = center,
        )
    }
}

private val LocalModelGlowColor = Color(0xFF4ADE80)

private data class PetFrame(
    val sprite: Int,
    val durationMillis: Int,
)

private data class PetTimeline(
    val frames: List<PetFrame>,
) {
    private val totalDuration = frames.sumOf { it.durationMillis }.coerceAtLeast(1)

    fun spriteAt(timeMillis: Long): Int {
        val position = (timeMillis % totalDuration).toInt()
        var cursor = 0
        for (frame in frames) {
            cursor += frame.durationMillis
            if (position < cursor) return frame.sprite
        }
        return frames.lastOrNull()?.sprite ?: 0
    }
}

private fun PresenceMode.toPetTimeline(animationState: PresenceAnimationState): PetTimeline =
    when (this) {
        PresenceMode.SPEAKING -> PetTimeline(
            listOf(
                PetFrame(6, scaledDuration(160, animationState)),
                PetFrame(7, scaledDuration(160, animationState)),
                PetFrame(8, scaledDuration(160, animationState)),
                PetFrame(9, scaledDuration(200, animationState)),
            )
        )
        PresenceMode.HAPPY -> PetTimeline(
            listOf(
                PetFrame(10, scaledDuration(300, animationState)),
                PetFrame(11, scaledDuration(300, animationState)),
            )
        )
        PresenceMode.TIRED, PresenceMode.SLEEPING -> PetTimeline(listOf(PetFrame(18, scaledDuration(900, animationState))))
        PresenceMode.THINKING -> PetTimeline(
            listOf(
                PetFrame(12, scaledDuration(320, animationState)),
                PetFrame(13, scaledDuration(320, animationState)),
            )
        )
        PresenceMode.SEARCHING -> PetTimeline(
            listOf(
                PetFrame(14, scaledDuration(260, animationState)),
                PetFrame(15, scaledDuration(260, animationState)),
            )
        )
        PresenceMode.REMEMBERING -> PetTimeline(listOf(PetFrame(16, scaledDuration(700, animationState))))
        PresenceMode.ERROR -> PetTimeline(listOf(PetFrame(19, scaledDuration(600, animationState))))
        PresenceMode.LISTENING -> PetTimeline(
            listOf(
                PetFrame(4, scaledDuration(420, animationState)),
                PetFrame(5, scaledDuration(420, animationState)),
            )
        )
        PresenceMode.SAD -> PetTimeline(listOf(PetFrame(17, scaledDuration(700, animationState))))
        PresenceMode.IDLE,
        -> PetTimeline(
            listOf(
                PetFrame(0, scaledDuration(520, animationState)),
                PetFrame(1, scaledDuration(520, animationState)),
                PetFrame(2, scaledDuration(520, animationState)),
                PetFrame(3, scaledDuration(720, animationState)),
            )
        )
    }

private fun scaledDuration(baseDuration: Int, animationState: PresenceAnimationState): Int =
    (baseDuration * animationState.petFrameDurationScale).toInt().coerceAtLeast(80)

private fun DrawScope.drawSpriteFrame(
    sheet: ImageBitmap,
    sprite: Int,
    frameWidth: Int,
    frameHeight: Int,
    columns: Int,
) {
    val sourceX = (sprite % columns) * frameWidth
    val sourceY = (sprite / columns) * frameHeight
    val scale = minOf(size.width / frameWidth, size.height / frameHeight)
    val targetWidth = frameWidth * scale
    val targetHeight = frameHeight * scale
    val targetOffset = Offset(
        x = (size.width - targetWidth) / 2f,
        y = (size.height - targetHeight) / 2f,
    )

    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
        }
        canvas.nativeCanvas.drawBitmap(
            sheet.asAndroidBitmap(),
            android.graphics.Rect(sourceX, sourceY, sourceX + frameWidth, sourceY + frameHeight),
            android.graphics.RectF(
                targetOffset.x,
                targetOffset.y,
                targetOffset.x + targetWidth,
                targetOffset.y + targetHeight,
            ),
            paint,
        )
    }
}

private const val PetFrameWidth = 192
private const val PetFrameHeight = 208
private const val PetColumns = 5
