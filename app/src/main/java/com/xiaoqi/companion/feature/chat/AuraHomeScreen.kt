package com.xiaoqi.companion.feature.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaoqi.companion.BuildConfig
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.core.presence.PresenceMode
import com.xiaoqi.companion.feature.chat.HomePresencePalette
import com.xiaoqi.companion.feature.chat.animated
import com.xiaoqi.companion.feature.chat.homePalette
import com.xiaoqi.companion.feature.chat.presence.LuminousAuraAvatar
import com.xiaoqi.companion.feature.chat.presence.PresenceBackdropAndHalo
import com.xiaoqi.companion.ui.theme.ChatColors

/**
 * 主屏(Aura Home Stage):
 * - 顶栏:Memory / MCP / Settings 入口
 * - 中央:Presence 视觉舞台(背景光晕 + Aura 角色 + 状态文案)
 *
 * 视觉绘制拆分到 [feature.chat.presence] 子包:
 * - [PresenceBackdropAndHalo]:背景光晕
 * - [LuminousAuraAvatar]:Aura 角色本体
 */
@Composable
fun AuraHomeScreen(
    viewModel: ChatViewModel,
    onOpenChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMemoryRoom: () -> Unit,
    onOpenMcpSettings: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var actionInsight by remember {
        mutableStateOf<com.xiaoqi.companion.feature.chat.ChatInsight?>(null)
    }
    var showEvidence by remember { mutableStateOf(false) }

    AuraHomeContent(
        uiState = uiState,
        onOpenChat = onOpenChat,
        onPresenceTapped = { viewModel.onPresenceTapped() },
        onOpenSettings = onOpenSettings,
        onOpenMemoryRoom = onOpenMemoryRoom,
        onOpenMcpSettings = onOpenMcpSettings,
        onInsightClick = { insight ->
            AppLogger.debug(
                LogTags.Chat,
                "insight_tapped",
                "insightId" to insight.id,
                "category" to insight.category,
            )
            showEvidence = false
            actionInsight = insight
            viewModel.openInsight(insight.id)
        },
        onInsightLongPress = { insight ->
            AppLogger.debug(
                LogTags.Chat,
                "insight_long_pressed",
                "insightId" to insight.id,
                "category" to insight.category,
            )
            showEvidence = false
            actionInsight = insight
        },
        onInsightDismiss = { insight ->
            AppLogger.info(
                LogTags.Chat,
                "insight_dismissed",
                "insightId" to insight.id,
                "category" to insight.category,
            )
            viewModel.dismissInsight(insight.id)
        },
        onInsightChat = { insight ->
            val prefill = "我们聊聊: ${insight.headline}?"
            AppLogger.info(
                LogTags.Chat,
                "insight_chat_pressed",
                "insightId" to insight.id,
                "prefillLength" to prefill.length,
            )
            viewModel.consumePrefillPrompt(prefill)
            viewModel.openInsight(insight.id)
            onOpenChat()
        },
        actionInsight = actionInsight,
        onActionDismiss = {
            AppLogger.debug(LogTags.Chat, "insight_action_dismissed")
            actionInsight = null; showEvidence = false
        },
        onActionMute = { insight, days ->
            AppLogger.info(
                LogTags.Chat,
                "insight_category_muted",
                "insightId" to insight.id,
                "category" to insight.category,
                "days" to days,
            )
            viewModel.muteInsightCategory(insight.id, insight.category, days)
            actionInsight = null
        },
        onActionAcknowledge = { insight ->
            AppLogger.info(
                LogTags.Chat,
                "insight_acknowledged",
                "insightId" to insight.id,
                "category" to insight.category,
            )
            viewModel.dismissInsight(insight.id)
            actionInsight = null
        },
        onShowEvidence = {
            showEvidence = !showEvidence
            AppLogger.debug(
                LogTags.Chat,
                "insight_evidence_toggled",
                "visible" to showEvidence,
            )
        },
        onChatFromAction = { insight ->
            AppLogger.info(
                LogTags.Chat,
                "insight_chat_from_action",
                "insightId" to insight.id,
            )
            viewModel.openInsight(insight.id)
            actionInsight = null
        },
        showEvidence = showEvidence,
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
    onInsightClick: (com.xiaoqi.companion.feature.chat.ChatInsight) -> Unit,
    onInsightLongPress: (com.xiaoqi.companion.feature.chat.ChatInsight) -> Unit,
    onInsightDismiss: (com.xiaoqi.companion.feature.chat.ChatInsight) -> Unit,
    onInsightChat: (com.xiaoqi.companion.feature.chat.ChatInsight) -> Unit,
    actionInsight: com.xiaoqi.companion.feature.chat.ChatInsight?,
    onActionDismiss: () -> Unit,
    onActionMute: (com.xiaoqi.companion.feature.chat.ChatInsight, Int) -> Unit,
    onActionAcknowledge: (com.xiaoqi.companion.feature.chat.ChatInsight) -> Unit,
    onShowEvidence: () -> Unit,
    onChatFromAction: (com.xiaoqi.companion.feature.chat.ChatInsight) -> Unit,
    showEvidence: Boolean,
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
                            ChatColors.InputSurface,
                            presenceColors.backgroundTint,
                            ChatColors.CardSurface,
                        ),
                    )
                ),
        ) {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                item {
                    HomeTopBar(
                        onOpenMemoryRoom = onOpenMemoryRoom,
                        onOpenMcpSettings = onOpenMcpSettings,
                        onOpenSettings = onOpenSettings,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                item {
                    PresenceStage(
                        uiState = uiState,
                        palette = presenceColors,
                        onPresenceTapped = onPresenceTapped,
                        onOpenChat = onOpenChat,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 280.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                }
                item {
                    com.xiaoqi.companion.feature.insight.MoodTrendChartSection(
                        snapshots = uiState.moodTrend,
                    )
                }
                if (uiState.insights.isNotEmpty()) {
                    item {
                        com.xiaoqi.companion.feature.insight.InsightCardList(
                            insights = uiState.insights,
                            onInsightClick = onInsightClick,
                            onInsightLongPress = onInsightLongPress,
                            onInsightDismiss = onInsightDismiss,
                            onInsightChat = onInsightChat,
                        )
                    }
                }
                item {
                    Spacer(
                        Modifier.windowInsetsBottomHeight(
                            androidx.compose.foundation.layout.WindowInsets.navigationBars,
                        ),
                    )
                }
            }
        }
    }

    actionInsight?.let { insight ->
        com.xiaoqi.companion.feature.insight.InsightLongPressDialog(
            insight = insight,
            evidence = insight.evidenceView,
            showEvidence = showEvidence,
            onDismiss = onActionDismiss,
            onMute = { days -> onActionMute(insight, days) },
            onAcknowledge = { onActionAcknowledge(insight) },
            onShowEvidence = onShowEvidence,
            onChat = { onChatFromAction(insight) },
        )
    }
}

@Composable
private fun HomeTopBar(
    onOpenMemoryRoom: () -> Unit,
    onOpenMcpSettings: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = BuildConfig.BRAND_NAME,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.sp,
                ),
                color = primary,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeTopActionIcon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "记忆",
                onClick = onOpenMemoryRoom,
            )
            HomeTopActionIcon(
                imageVector = Icons.Default.Build,
                contentDescription = "MCP",
                onClick = onOpenMcpSettings,
            )
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = Color.White.copy(alpha = 0.72f),
                shadowElevation = 4.dp,
                tonalElevation = 1.dp,
            ) {
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "设置",
                        tint = primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeTopActionIcon(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = androidx.compose.foundation.shape.CircleShape,
        color = Color.White.copy(alpha = 0.52f),
        shadowElevation = 2.dp,
        tonalElevation = 0.dp,
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.78f),
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
        val stageHeight = (maxHeight * 0.56f).coerceIn(260.dp, 360.dp)
        val avatarSize = (stageHeight * 0.52f).coerceIn(140.dp, 200.dp)
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
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .clickable(onClick = onOpenChat),
                )
            }
        }
    }
}

private fun ChatUiState.homePresenceLine(): String =
    when {
        isPreparingImage -> "读图中"
        pendingImage != null -> "图就绪"
        isLoading -> "思考中"
        inputText.isNotBlank() -> "在听"
        presence.mode == PresenceMode.SEARCHING -> "查找中"
        presence.mode == PresenceMode.HAPPY -> "心情好"
        presence.mode == PresenceMode.TIRED || presence.mode == PresenceMode.SLEEPING -> "休息中"
        else -> "在这里"
    }
