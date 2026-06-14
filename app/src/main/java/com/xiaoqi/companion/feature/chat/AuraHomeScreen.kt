package com.xiaoqi.companion.feature.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaoqi.companion.core.presence.PresenceMode
import com.xiaoqi.companion.core.presence.PresenceReaction
import com.xiaoqi.companion.core.presence.PresenceUiState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AuraHomeScreen(
    viewModel: ChatViewModel,
    onOpenChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMemoryRoom: () -> Unit,
    onOpenMcpSettings: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AuraHomeContent(
        uiState = uiState,
        onOpenChat = onOpenChat,
        onPresenceTapped = { viewModel.onPresenceTapped() },
        onOpenSettings = onOpenSettings,
        onOpenMemoryRoom = onOpenMemoryRoom,
        onOpenMcpSettings = onOpenMcpSettings,
    )
}

@Composable
private fun AuraHomeContent(
    uiState: ChatUiState,
    onOpenChat: () -> Unit,
    onPresenceTapped: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMemoryRoom: () -> Unit,
    onOpenMcpSettings: () -> Unit,
) {
    val presenceColors = uiState.presence.homePalette().animated()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFFFCF6),
                            presenceColors.backgroundTint,
                            Color(0xFFF7F2EA),
                        ),
                    )
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                HomeTopBar(
                    onOpenMemoryRoom = onOpenMemoryRoom,
                    onOpenMcpSettings = onOpenMcpSettings,
                    onOpenSettings = onOpenSettings,
                )

                PresenceStage(
                    uiState = uiState,
                    palette = presenceColors,
                    onPresenceTapped = onPresenceTapped,
                    onOpenChat = onOpenChat,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HomeTopBar(
    onOpenMemoryRoom: () -> Unit,
    onOpenMcpSettings: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Aura",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.sp,
                ),
                color = Color(0xFF496B5E),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeTopActionIcon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Memory",
                onClick = onOpenMemoryRoom,
            )
            HomeTopActionIcon(
                imageVector = Icons.Default.Build,
                contentDescription = "MCP",
                onClick = onOpenMcpSettings,
            )
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.72f),
                shadowElevation = 4.dp,
                tonalElevation = 1.dp,
            ) {
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color(0xFF496B5E),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeTopActionIcon(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.52f),
        shadowElevation = 2.dp,
        tonalElevation = 0.dp,
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = Color(0xFF496B5E).copy(alpha = 0.78f),
            )
        }
    }
}

@Composable
private fun PresenceStage(
    uiState: ChatUiState,
    palette: HomePresencePalette,
    onPresenceTapped: () -> Unit,
    onOpenChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val stageHeight = (maxHeight * 0.62f).coerceIn(284.dp, 390.dp)
        val avatarSize = (stageHeight * 0.60f).coerceIn(168.dp, 236.dp)
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(stageHeight),
                contentAlignment = Alignment.Center,
            ) {
                PresenceBackdropAndHalo(
                    palette = palette,
                    mode = uiState.presence.mode,
                    reaction = uiState.presence.reaction,
                    modifier = Modifier.fillMaxSize(),
                )
                LuminousAuraAvatar(
                    presence = uiState.presence,
                    size = avatarSize,
                    onClick = {
                        onPresenceTapped()
                        onOpenChat()
                    },
                )
            }
            AnimatedContent(
                targetState = uiState.homePresenceLine(),
                transitionSpec = {
                    fadeIn(tween(400)) togetherWith fadeOut(tween(300))
                },
                label = "presence-line",
            ) { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.sp,
                    ),
                    color = Color(0xFF496B5E),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .clickable(onClick = onOpenChat),
                )
            }
        }
    }
}

@Composable
private fun PresenceBackdropAndHalo(
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

    // Reaction one-shot animation (Animatable driven by reaction change)
    val reactionProgress = remember { Animatable(0f) }
    LaunchedEffect(reaction) {
        if (reaction != null) {
            reactionProgress.snapTo(0f)
            reactionProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = when (reaction) {
                        PresenceReaction.ERROR_RECOVER -> 2600
                        PresenceReaction.MEMORY_SPARK -> 2300
                        PresenceReaction.SEARCH_SWEEP -> 1900
                        PresenceReaction.RETURN_BLINK -> 1600
                        PresenceReaction.TOUCH_NUZZLE -> 1200
                    },
                    easing = FastOutSlowInEasing,
                ),
            )
        } else {
            reactionProgress.animateTo(0f, tween(300))
        }
    }

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
                progress = reactionProgress.value,
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

@Composable
private fun LuminousAuraAvatar(
    presence: PresenceUiState,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "luminous-aura")
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900),
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
            .clip(CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        tapScale.animateTo(
                            0.94f,
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
            repeat(5) { index ->
                val angle = (shimmer * 2f * PI + index * 1.26f).toFloat()
                drawCircle(
                    color = palette.spark.copy(alpha = 0.34f),
                    radius = w * (0.012f + index % 2 * 0.004f),
                    center = Offset(
                        x = center.x + cos(angle) * w * (0.31f + index * 0.012f),
                        y = center.y + sin(angle) * h * 0.28f,
                    ),
                )
            }
        }
    }
}

private fun ChatUiState.homePresenceLine(): String =
    when {
        isPreparingImage -> "Reading image"
        pendingImage != null -> "Image ready"
        isLoading -> "Thinking"
        inputText.isNotBlank() -> "Listening"
        presence.mode == PresenceMode.REMEMBERING -> "Saved"
        presence.mode == PresenceMode.SEARCHING -> "Searching"
        presence.mode == PresenceMode.HAPPY -> "Bright"
        presence.mode == PresenceMode.SAD -> "Here"
        presence.mode == PresenceMode.TIRED || presence.mode == PresenceMode.SLEEPING -> "Resting"
        messages.isEmpty() -> "Here"
        else -> "Here"
    }
