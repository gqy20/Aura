package com.xiaoqi.companion.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AuraHomeScreen(
    viewModel: ChatViewModel,
    onOpenChat: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AuraHomeContent(
        uiState = uiState,
        onOpenChat = onOpenChat,
        onPresenceTapped = { viewModel.onPresenceTapped() },
        onOpenSettings = { viewModel.openSettings() },
    )
}

@Composable
private fun AuraHomeContent(
    uiState: ChatUiState,
    onOpenChat: () -> Unit,
    onPresenceTapped: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Aura",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = uiState.presence.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 30.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        AuraPetAvatar(
                            presence = uiState.presence,
                            size = 148.dp,
                            onClick = onPresenceTapped,
                        )
                        Text(
                            text = uiState.status.moodTitle(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = uiState.homeLine(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Button(onClick = onOpenChat) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Talk with Aura")
                        }
                    }
                }
            }

            HomeMemoryStrip(memories = uiState.memories)
        }
    }
}

@Composable
private fun HomeMemoryStrip(memories: List<ChatMemory>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Recent memories",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (memories.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Nothing saved yet. The first small thing will appear here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp),
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(132.dp),
            ) {
                items(memories.take(2), key = { it.id }) { memory ->
                    HomeMemoryItem(memory = memory)
                }
            }
        }
    }
}

@Composable
private fun HomeMemoryItem(memory: ChatMemory) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = memory.type.lowercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = memory.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
        }
    }
}

private fun CompanionStatus.moodTitle(): String =
    when (mood.lowercase()) {
        "happy", "joy", "excited" -> "Aura feels bright"
        "sad", "lonely", "anxious", "worried", "upset" -> "Aura is quiet"
        "tired", "sleepy", "exhausted", "low" -> "Aura is resting softly"
        "calm" -> "Aura is calm"
        else -> "Aura is here"
    }

private fun ChatUiState.homeLine(): String =
    when {
        isLoading -> "She is thinking through your last message."
        pendingImage != null -> "There is a picture waiting to be shared."
        messages.isEmpty() -> "Start with a thought, a small moment, or a picture."
        else -> "The last conversation is still warm."
    }
