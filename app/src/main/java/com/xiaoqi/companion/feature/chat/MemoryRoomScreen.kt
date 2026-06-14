package com.xiaoqi.companion.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun MemoryRoomScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MemoryRoomScreenContent(
        memories = uiState.memories,
        onDeleteMemory = viewModel::deleteMemory,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoryRoomScreenContent(
    memories: List<ChatMemory>,
    onDeleteMemory: (String) -> Unit,
    onBack: () -> Unit,
) {
    var selectedType by remember { mutableStateOf<String?>(null) }
    var selectedMemory by remember { mutableStateOf<ChatMemory?>(null) }
    var pendingDelete by remember { mutableStateOf<ChatMemory?>(null) }
    val visibleMemories = remember(memories, selectedType) {
        selectedType?.let { type -> memories.filter { it.type == type } } ?: memories
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Memory room") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "${visibleMemories.size} of ${memories.size} memories arranged from your conversations.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MemoryRoomStats(memories = memories)
            MemoryTypeFilters(
                selectedType = selectedType,
                onSelectedTypeChanged = { selectedType = it },
            )
            if (visibleMemories.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(28.dp),
                    ) {
                        Text(
                            text = if (memories.isEmpty()) "No long-term memories yet" else "No memories in this filter",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (memories.isEmpty()) {
                                "After more conversations, Aura will keep useful preferences, moments, and habits here."
                            } else {
                                "Try another type, or clear the filter."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visibleMemories, key = { it.id }) { memory ->
                        MemoryRoomItemCard(
                            memory = memory,
                            onOpen = { selectedMemory = memory },
                            onDelete = { pendingDelete = memory },
                        )
                    }
                }
            }
        }
    }

    selectedMemory?.let { memory ->
        MemoryDetailDialog(
            memory = memory,
            onDismiss = { selectedMemory = null },
            onDelete = {
                selectedMemory = null
                pendingDelete = memory
            },
        )
    }

    pendingDelete?.let { memory ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete memory?") },
            text = {
                Text(
                    text = memory.content,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteMemory(memory.id)
                        pendingDelete = null
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun MemoryRoomStats(memories: List<ChatMemory>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MemoryStatPill(label = "All", value = memories.size.toString())
        MemoryStatPill(label = "Facts", value = memories.count { it.type == "FACT" }.toString())
        MemoryStatPill(label = "Moments", value = memories.count { it.type == "EPISODE" }.toString())
        MemoryStatPill(label = "Habits", value = memories.count { it.type == "PROCEDURAL" }.toString())
    }
}

@Composable
private fun MemoryStatPill(label: String, value: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MemoryTypeFilters(
    selectedType: String?,
    onSelectedTypeChanged: (String?) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = selectedType == null,
            onClick = { onSelectedTypeChanged(null) },
            label = { Text("All") },
        )
        listOf("FACT", "EPISODE", "PROCEDURAL").forEach { type ->
            FilterChip(
                selected = selectedType == type,
                onClick = { onSelectedTypeChanged(type) },
                label = { Text(type.memoryTypeLabel()) },
            )
        }
    }
}

@Composable
private fun MemoryRoomItemCard(
    memory: ChatMemory,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = Color(0xFFF7F2EA),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(13.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CapabilityMetaPill(text = memory.type.memoryTypeLabel())
                    Text(
                        text = memory.source.memorySourceLabel(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = memory.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = memory.importance.memoryImportanceLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                )
            }
            TextButton(onClick = onOpen) {
                Text("Open")
            }
            Surface(
                shape = CircleShape,
                color = Color.Transparent,
            ) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(32.dp)
                        .semantics { contentDescription = "Delete memory" },
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryDetailDialog(
    memory: ChatMemory,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(memory.type.memoryTypeLabel())
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    CapabilityMetaPill(text = memory.source.memorySourceLabel())
                    CapabilityMetaPill(text = memory.importance.memoryImportanceLabel())
                }
                Text(
                    text = memory.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (memory.timestamp > 0L) {
                    Text(
                        text = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(memory.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDelete) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
    )
}

private fun String.memoryTypeLabel(): String =
    when (this) {
        "FACT" -> "Fact"
        "EPISODE" -> "Moment"
        "PROCEDURAL" -> "Habit"
        else -> lowercase()
    }

private fun String.memorySourceLabel(): String =
    when {
        isBlank() -> "Aura reflection"
        startsWith("tool:") -> "Aura reflection"
        startsWith("reflection:") -> "From chat"
        else -> this
    }

private fun Float.memoryImportanceLabel(): String =
    when {
        this >= 0.82f -> "Important memory"
        this >= 0.58f -> "Likely useful"
        else -> "Light note"
    }
