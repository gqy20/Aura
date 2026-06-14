package com.xiaoqi.companion.feature.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaoqi.companion.core.presence.PresenceMode
import com.xiaoqi.companion.core.presence.PresenceReaction
import com.xiaoqi.companion.core.presence.PresenceUiState
import com.xiaoqi.companion.ui.theme.CompanionTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AuraHomeScreen(
    viewModel: ChatViewModel,
    onOpenChat: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AuraHomeContent(
        uiState = uiState,
        onInputTextChanged = { viewModel.updateInputText(it) },
        onSendMessage = {
            viewModel.sendMessage(uiState.inputText)
            if (uiState.inputText.isNotBlank() && uiState.configStatus.isReady) {
                onOpenChat()
            }
        },
        onOpenChat = onOpenChat,
        onPresenceTapped = { viewModel.onPresenceTapped() },
        onOpenSettings = onOpenSettings,
    )
}

@Composable
private fun AuraHomeContent(
    uiState: ChatUiState,
    onInputTextChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    onOpenChat: () -> Unit,
    onPresenceTapped: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val presenceColors = uiState.presence.homePalette()

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
            SoftPresenceBackdrop(
                palette = presenceColors,
                modifier = Modifier.fillMaxSize(),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                HomeTopBar(
                    presence = uiState.presence,
                    onOpenSettings = onOpenSettings,
                )

                PresenceStage(
                    uiState = uiState,
                    palette = presenceColors,
                    onPresenceTapped = onPresenceTapped,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )

                HomeInputDock(
                    inputText = uiState.inputText,
                    placeholder = uiState.homeInputPlaceholder(),
                    canSend = uiState.canSendFromHome(),
                    isBusy = uiState.isLoading || uiState.isPreparingImage,
                    status = uiState.homeDockStatus(),
                    onInputTextChanged = onInputTextChanged,
                    onSendMessage = onSendMessage,
                    onOpenChat = onOpenChat,
                )
            }
        }
    }
}

@Composable
private fun HomeTopBar(
    presence: PresenceUiState,
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
            Text(
                text = presence.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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

@Composable
private fun PresenceStage(
    uiState: ChatUiState,
    palette: HomePresencePalette,
    onPresenceTapped: () -> Unit,
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
                PresenceHalo(
                    palette = palette,
                    mode = uiState.presence.mode,
                    reaction = uiState.presence.reaction,
                    modifier = Modifier.fillMaxSize(),
                )
                LuminousAuraAvatar(
                    presence = uiState.presence,
                    size = avatarSize,
                    onClick = onPresenceTapped,
                )
            }
            Text(
                text = uiState.homePresenceLine(),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.sp,
                ),
                color = Color(0xFF496B5E),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = uiState.presence.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 28.dp),
            )
            Spacer(modifier = Modifier.height(22.dp))
            HomeContextStrip(uiState = uiState)
            Spacer(modifier = Modifier.height(18.dp))
            PresenceWave(
                palette = palette,
                active = uiState.isLoading || uiState.inputText.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth(0.64f)
                    .height(32.dp),
            )
        }
    }
}

@Composable
private fun SoftPresenceBackdrop(
    palette: HomePresencePalette,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "home-backdrop")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000),
            repeatMode = RepeatMode.Restart,
        ),
        label = "drift",
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
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
    }
}

@Composable
private fun PresenceHalo(
    palette: HomePresencePalette,
    mode: PresenceMode,
    reaction: PresenceReaction?,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "home-halo")
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
        val center = Offset(w / 2f, h * 0.56f)
        val reactionBoost = reaction.haloBoost()
        val baseRadius = w * (0.29f + pulse * (0.015f + reactionBoost * 0.012f))

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.glow.copy(alpha = 0.34f + reactionBoost * 0.16f),
                    palette.glow.copy(alpha = 0.10f + reactionBoost * 0.06f),
                    Color.Transparent,
                ),
                center = center,
                radius = w * 0.36f,
            ),
            radius = w * 0.36f,
            center = center,
        )
        repeat(3) { index ->
            drawCircle(
                color = palette.ring.copy(alpha = 0.20f - index * 0.045f + reactionBoost * 0.06f),
                radius = baseRadius + index * w * 0.064f,
                center = center.copy(y = center.y + h * 0.22f),
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
                center = center,
            )
        }
        if (mode == PresenceMode.THINKING || mode == PresenceMode.SEARCHING || mode == PresenceMode.REMEMBERING) {
            repeat(3) { index ->
                val angle = (pulse * 2f * PI + index * 2.09f).toFloat()
                drawCircle(
                    color = palette.spark.copy(alpha = 0.38f),
                    radius = 4.2f,
                    center = Offset(
                        x = center.x + cos(angle) * w * 0.21f,
                        y = center.y + sin(angle) * h * 0.17f,
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

    Canvas(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = onClick),
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
        )
        drawAuraBellyStar(
            palette = palette,
            shimmer = shimmer,
            width = w,
            height = h,
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

@Composable
private fun PresenceWave(
    palette: HomePresencePalette,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "home-wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    Canvas(modifier = modifier) {
        val centerY = size.height / 2f
        val dotCount = 31
        val gap = size.width / (dotCount - 1)
        repeat(dotCount) { index ->
            val distance = kotlin.math.abs(index - dotCount / 2f) / (dotCount / 2f)
            val wave = if (active) {
                (sin((phase * 2f * PI + index * 0.58f).toFloat()) + 1f) * 0.5f
            } else {
                0.22f
            }
            val barHeight = (5f + (1f - distance) * 24f * wave).coerceAtLeast(3f)
            val x = index * gap
            drawLine(
                color = palette.ring.copy(alpha = 0.18f + (1f - distance) * 0.2f),
                start = Offset(x, centerY - barHeight / 2f),
                end = Offset(x, centerY + barHeight / 2f),
                strokeWidth = 3.2f,
                cap = StrokeCap.Round,
            )
        }
        drawCircle(
            color = Color.White.copy(alpha = 0.68f),
            radius = 15.dp.toPx(),
            center = Offset(size.width / 2f, centerY),
        )
        drawCircle(
            color = palette.spark.copy(alpha = 0.54f),
            radius = 5.dp.toPx(),
            center = Offset(size.width / 2f, centerY),
        )
    }
}

@Composable
private fun HomeInputDock(
    inputText: String,
    placeholder: String,
    canSend: Boolean,
    isBusy: Boolean,
    status: String,
    onInputTextChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    onOpenChat: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(34.dp),
        color = Color(0xFFF4EDDE),
        shadowElevation = 8.dp,
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenChat),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, end = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFFEDE4D3),
                modifier = Modifier.size(42.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "A",
                        color = Color(0xFF496B5E),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Normal,
                        ),
                    )
                }
            }
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputTextChanged,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                placeholder = {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSendMessage() }),
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFFFFCF6),
                    unfocusedContainerColor = Color(0xFFFFF8EA),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Color(0xFF496B5E),
                ),
            )
            Surface(
                shape = CircleShape,
                color = if (canSend) Color(0xFFDDE8D9) else Color(0xFFE4DBC9),
                modifier = Modifier.size(54.dp),
            ) {
                IconButton(
                    onClick = if (canSend) onSendMessage else onOpenChat,
                    enabled = !isBusy,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = status,
                        tint = if (canSend) Color(0xFF496B5E) else Color(0xFF746F67),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeContextStrip(uiState: ChatUiState) {
    val latestAssistant = uiState.messages.lastOrNull { it.role == "ASSISTANT" && it.content.isNotBlank() }
    val memory = uiState.memories.firstOrNull()
    Row(
        modifier = Modifier.fillMaxWidth(0.86f),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeSignalPill(
            label = when {
                !uiState.configStatus.isReady -> "Setup"
                uiState.isLoading -> "Replying"
                else -> uiState.presence.accent.replaceFirstChar { it.uppercase() }
            },
            detail = when {
                !uiState.configStatus.isReady -> uiState.configStatus.detail
                latestAssistant != null -> latestAssistant.content
                else -> "Ready when you are"
            },
            modifier = Modifier.weight(1f),
        )
        HomeSignalPill(
            label = "Memory",
            detail = memory?.content ?: "Nothing saved yet",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HomeSignalPill(
    label: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.58f),
        tonalElevation = 0.dp,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF496B5E),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private data class HomePresencePalette(
    val backgroundTint: Color,
    val glow: Color,
    val ring: Color,
    val spark: Color,
)

private fun PresenceUiState.homePalette(): HomePresencePalette =
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

private fun ChatUiState.homePresenceLine(): String =
    when {
        isPreparingImage -> "奥拉在整理这张图片。"
        pendingImage != null -> "这张图片正在等她看。"
        isLoading -> "奥拉正在想。"
        inputText.isNotBlank() -> "奥拉在听。"
        presence.mode == PresenceMode.REMEMBERING -> "她刚刚记住了一件小事。"
        presence.mode == PresenceMode.SEARCHING -> "她在翻找线索。"
        presence.mode == PresenceMode.HAPPY -> "奥拉今天很亮。"
        presence.mode == PresenceMode.SAD -> "奥拉安静地陪着你。"
        presence.mode == PresenceMode.TIRED || presence.mode == PresenceMode.SLEEPING -> "奥拉在轻轻休息。"
        messages.isEmpty() -> "奥拉在听。"
        else -> "上一次聊天还留着余温。"
    }

private fun ChatUiState.homeInputPlaceholder(): String =
    when {
        !configStatus.isReady -> "先完成模型配置..."
        isLoading -> "奥拉正在回应..."
        else -> "和奥拉聊一聊..."
    }

private fun ChatUiState.homeDockStatus(): String =
    when {
        isLoading || isPreparingImage -> "Aura is busy"
        canSendFromHome() -> "Send from home"
        !configStatus.isReady -> "Open chat setup"
        else -> "Open chat"
    }

private fun ChatUiState.canSendFromHome(): Boolean =
    inputText.isNotBlank() && configStatus.isReady && !isLoading && !isPreparingImage

@Preview(
    name = "Home / Ready",
    showBackground = true,
    widthDp = 393,
    heightDp = 852,
)
@Composable
private fun AuraHomeReadyPreview() {
    AuraHomePreviewContent(
        state = previewHomeState(
            presence = PresenceUiState(mode = PresenceMode.IDLE),
            messages = listOf(
                ChatMessage(
                    id = "assistant-1",
                    role = "ASSISTANT",
                    content = "上一次聊天还留着余温。",
                ),
            ),
        ),
    )
}

@Preview(
    name = "Home / Thinking",
    showBackground = true,
    widthDp = 393,
    heightDp = 852,
)
@Composable
private fun AuraHomeThinkingPreview() {
    AuraHomePreviewContent(
        state = previewHomeState(
            isLoading = true,
            presence = PresenceUiState(mode = PresenceMode.THINKING),
        ),
    )
}

@Preview(
    name = "Home / Needs Config",
    showBackground = true,
    widthDp = 393,
    heightDp = 852,
)
@Composable
private fun AuraHomeNeedsConfigPreview() {
    AuraHomePreviewContent(
        state = previewHomeState(
            configStatus = ChatConfigStatus(
                label = "模型未配置",
                isReady = false,
                detail = "需要填写 API Key",
            ),
            presence = PresenceUiState(mode = PresenceMode.ERROR),
        ),
    )
}

@Preview(
    name = "Home / Narrow",
    showBackground = true,
    widthDp = 320,
    heightDp = 720,
)
@Composable
private fun AuraHomeNarrowPreview() {
    AuraHomePreviewContent(
        state = previewHomeState(
            presence = PresenceUiState(mode = PresenceMode.REMEMBERING),
            memories = listOf(
                ChatMemory(
                    id = "memory-1",
                    content = "用户喜欢晚上喝咖啡。",
                    type = "PREFERENCE",
                    importance = 0.72f,
                ),
            ),
        ),
    )
}

@Composable
private fun AuraHomePreviewContent(state: ChatUiState) {
    CompanionTheme {
        AuraHomeContent(
            uiState = state,
            onInputTextChanged = {},
            onSendMessage = {},
            onOpenChat = {},
            onPresenceTapped = {},
            onOpenSettings = {},
        )
    }
}

private fun previewHomeState(
    configStatus: ChatConfigStatus = ChatConfigStatus(
        label = "GLM 已就绪",
        isReady = true,
        detail = "Preview",
    ),
    presence: PresenceUiState = PresenceUiState(),
    messages: List<ChatMessage> = emptyList(),
    memories: List<ChatMemory> = emptyList(),
    isLoading: Boolean = false,
): ChatUiState =
    ChatUiState(
        configStatus = configStatus,
        presence = presence,
        messages = messages,
        memories = memories,
        isLoading = isLoading,
    )

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
) {
    val flame = Path().apply {
        moveTo(width * 0.5f, height * 0.2f)
        cubicTo(width * 0.42f, height * 0.11f, width * 0.52f, height * 0.04f, width * 0.49f, height * 0.0f)
        cubicTo(width * 0.6f, height * 0.08f, width * 0.62f, height * 0.16f, width * 0.53f, height * 0.23f)
        cubicTo(width * 0.51f, height * 0.25f, width * 0.49f, height * 0.24f, width * 0.5f, height * 0.2f)
        close()
    }
    drawPath(
        path = flame,
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
    val star = Path().apply {
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
    drawPath(star, Color.White.copy(alpha = 0.95f))
}
