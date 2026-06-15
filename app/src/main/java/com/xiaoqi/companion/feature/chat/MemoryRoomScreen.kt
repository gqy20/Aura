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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.outlined.Archive
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
import com.xiaoqi.companion.BuildConfig

@Composable
fun MemoryRoomScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MemoryRoomScreenContent(
        memories = uiState.memories,
        onDeleteMemory = viewModel::deleteMemory,
        onPinMemory = viewModel::pinMemory,
        onUnpinMemory = viewModel::unpinMemory,
        onArchiveMemory = viewModel::archiveMemory,
        onUnarchiveMemory = viewModel::unarchiveMemory,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoryRoomScreenContent(
    memories: List<ChatMemory>,
    onDeleteMemory: (String) -> Unit,
    onPinMemory: (String) -> Unit,
    onUnpinMemory: (String) -> Unit,
    onArchiveMemory: (String) -> Unit,
    onUnarchiveMemory: (String) -> Unit,
    onBack: () -> Unit,
) {
    var selectedType by remember { mutableStateOf<String?>(null) }
    var selectedMemory by remember { mutableStateOf<ChatMemory?>(null) }
    var pendingDelete by remember { mutableStateOf<ChatMemory?>(null) }
    var actionMemory by remember { mutableStateOf<ChatMemory?>(null) }
    val visibleMemories = remember(memories, selectedType) {
        selectedType?.let { type -> memories.filter { it.type == type } } ?: memories
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("记忆") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                            text = if (memories.isEmpty()) "暂无记忆" else "该筛选下无内容",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (memories.isEmpty()) {
                                "记忆将出现在这里"
                            } else {
                                "换其他类型试试"
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
                            onLongPress = { actionMemory = memory },
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
            title = { Text("删除？") },
            text = {
                Text(
                    text = memory.content,
                    maxLines = 2,
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
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消")
                }
            },
        )
    }

    actionMemory?.let { memory ->
        MemoryActionDialog(
            memory = memory,
            onPin = { onPinMemory(memory.id); actionMemory = null },
            onUnpin = { onUnpinMemory(memory.id); actionMemory = null },
            onArchive = { onArchiveMemory(memory.id); actionMemory = null },
            onUnarchive = { onUnarchiveMemory(memory.id); actionMemory = null },
            onDelete = {
                actionMemory = null
                pendingDelete = memory
            },
            onDismiss = { actionMemory = null },
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
        MemoryStatPill(label = "事实", value = memories.count { it.type == "FACT" }.toString())
        MemoryStatPill(label = "时刻", value = memories.count { it.type == "EPISODE" }.toString())
        MemoryStatPill(label = "习惯", value = memories.count { it.type == "PROCEDURAL" }.toString())
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
            label = { Text("全部") },
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemoryRoomItemCard(
    memory: ChatMemory,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = Color(0xFFF7F2EA),
        tonalElevation = 0.dp,
        modifier = Modifier.combinedClickable(
            onClick = onOpen,
            onLongClick = onLongPress,
        ),
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
                    if (memory.pinned) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "已置顶",
                            tint = Color(0xFFE5A100),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    if (memory.archived) {
                        Icon(
                            imageVector = Icons.Filled.Archive,
                            contentDescription = "已归档",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
                            modifier = Modifier.size(14.dp),
                        )
                    }
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.size(7.dp)) {
                        drawCircle(
                            color = when {
                                memory.importance >= 0.82f -> Color(0xFF3FA86B)
                                memory.importance >= 0.58f -> Color(0xFFE5A100)
                                else -> Color(0xFFB7B0A4)
                            },
                        )
                    }
                    Text(
                        text = memory.importance.memoryImportanceLabel(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    )
                }
            }
            Surface(
                shape = CircleShape,
                color = Color.Transparent,
            ) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(32.dp)
                        .semantics { contentDescription = "删除记忆" },
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
private fun MemoryActionDialog(
    memory: ChatMemory,
    onPin: () -> Unit,
    onUnpin: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(memory.type.memoryTypeLabel()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = memory.content,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (memory.pinned) {
                    TextButton(onClick = onUnpin) {
                        Icon(
                            imageVector = Icons.Outlined.StarOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("取消置顶")
                    }
                } else {
                    TextButton(onClick = onPin) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("置顶")
                    }
                }
                if (memory.archived) {
                    TextButton(onClick = onUnarchive) {
                        Icon(
                            imageVector = Icons.Outlined.Archive,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("取消归档")
                    }
                } else {
                    TextButton(onClick = onArchive) {
                        Icon(
                            imageVector = Icons.Filled.Archive,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("归档")
                    }
                }
                TextButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("删除")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
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
                    Icon(Icons.Default.Close, contentDescription = "关闭")
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
                Text("删除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        },
    )
}

private fun String.memoryTypeLabel(): String =
    when (this) {
        "FACT" -> "事实"
        "EPISODE" -> "时刻"
        "PROCEDURAL" -> "习惯"
        else -> lowercase()
    }

private fun String.memorySourceLabel(): String =
    when {
        isBlank() -> BuildConfig.BRAND_NAME
        startsWith("tool:") -> BuildConfig.BRAND_NAME
        startsWith("reflection:") -> "对话"
        else -> this
    }

private fun Float.memoryImportanceLabel(): String =
    when {
        this >= 0.82f -> "重要"
        this >= 0.58f -> "有用"
        else -> "一般"
    }
