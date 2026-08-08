package com.xiaoqi.companion.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.xiaoqi.companion.core.companion.model.ToolCallStatus
import com.xiaoqi.companion.core.tools.parser.ToolResultSummary
import com.xiaoqi.companion.feature.chat.map.MapRouteDraft
import com.xiaoqi.companion.feature.chat.map.MapToolInteraction
import com.xiaoqi.companion.ui.theme.ChatStatusColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tool 调用结果详情面板 —— 在消息气泡的 tool 状态 pill 上点击时弹出。
 *
 * **数据流**:`ChatToolCall.summary: ToolResultSummary?` 已经是结构化摘要,UI 只负责
 * 把每种 summary 类型映射到对应渲染分支,不重新解析 resultJson。
 *
 * **设计原则**:
 * - summary 为 null → 友好兜底("暂无详情");不抛
 * - 标题/icon/配色按 [ToolCallStatus] + summary 类型派生,不引入独立 enum
 * - list 类型用 LazyColumn 滚动(防止大量命中时撑爆)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolCallDetailSheet(
    toolCall: ChatToolCall,
    onDismiss: () -> Unit,
    onOpenMap: (MapToolInteraction) -> Unit = {},
    onRerunRoute: (MapRouteDraft) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        ToolCallDetailContent(
            toolCall = toolCall,
            onOpenMap = onOpenMap,
            onRerunRoute = onRerunRoute,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun ToolCallDetailContent(
    toolCall: ChatToolCall,
    onOpenMap: (MapToolInteraction) -> Unit,
    onRerunRoute: (MapRouteDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header(toolCall)
        Divider()
        toolCall.mapInteraction?.let { interaction ->
            MapInteractionBody(
                toolCallId = toolCall.id,
                interaction = interaction,
                onOpenMap = onOpenMap,
                onRerunRoute = { draft ->
                    onDismiss()
                    onRerunRoute(draft)
                },
            )
            Divider()
        }
        if (toolCall.mapInteraction == null || toolCall.summary !is ToolResultSummary.Unknown) {
            SummaryBody(summary = toolCall.summary, errorMessage = toolCall.errorMessage)
        }
        toolCall.durationMs?.let { Footer(durationMs = it) }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun MapInteractionBody(
    toolCallId: String,
    interaction: MapToolInteraction,
    onOpenMap: (MapToolInteraction) -> Unit,
    onRerunRoute: (MapRouteDraft) -> Unit,
) {
    when (interaction) {
        is MapToolInteraction.Place -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(interaction.name, style = MaterialTheme.typography.titleSmall)
                interaction.address.takeIf(String::isNotBlank)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = interaction.coordinate.queryValue,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
                Button(onClick = { onOpenMap(interaction) }) {
                    Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("打开地图")
                }
            }
        }
        is MapToolInteraction.Route -> {
            var origin by rememberSaveable(toolCallId) { mutableStateOf(interaction.origin.queryValue) }
            var destination by rememberSaveable(toolCallId) { mutableStateOf(interaction.destination.queryValue) }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${interaction.travelMode.label}路线", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = routeMetric(interaction),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = origin,
                    onValueChange = { origin = it },
                    label = { Text("起点") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    IconButton(onClick = {
                        val previousOrigin = origin
                        origin = destination
                        destination = previousOrigin
                    }) {
                        Icon(Icons.Filled.SwapVert, contentDescription = "交换起终点")
                    }
                }
                OutlinedTextField(
                    value = destination,
                    onValueChange = { destination = it },
                    label = { Text("终点") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = { onOpenMap(interaction) }) {
                        Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("打开地图")
                    }
                    Button(
                        onClick = {
                            onRerunRoute(
                                MapRouteDraft(
                                    origin = origin,
                                    destination = destination,
                                    travelMode = interaction.travelMode,
                                )
                            )
                        },
                        enabled = origin.isNotBlank() && destination.isNotBlank(),
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("重新查询")
                    }
                }
            }
        }
    }
}

private fun routeMetric(route: MapToolInteraction.Route): String = buildList {
    route.distanceMeters?.let { distance ->
        add(if (distance >= 1000) "%.1f km".format(distance / 1000.0) else "$distance m")
    }
    route.durationSeconds?.let { duration -> add("约 ${duration / 60} 分钟") }
}.joinToString(" · ")

@Composable
private fun Header(toolCall: ChatToolCall) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            imageVector = statusIcon(toolCall.toolStatus),
            contentDescription = null,
            tint = statusColor(toolCall.toolStatus),
        )
        Column {
            Text(
                text = toolCall.label,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = toolCall.toolName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun Divider() {
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

@Composable
private fun SummaryBody(
    summary: ToolResultSummary?,
    errorMessage: String?,
) {
    if (errorMessage != null && summary !is ToolResultSummary.Failed) {
        Text(
            text = "错误信息:$errorMessage",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    when (summary) {
        null -> EmptyState()
        is ToolResultSummary.ListHits -> ListHitsBody(summary)
        is ToolResultSummary.SavedOne -> SavedOneBody(summary)
        is ToolResultSummary.Scheduled -> ScheduledBody(summary)
        is ToolResultSummary.KeyValueReport -> KeyValueReportBody(summary)
        is ToolResultSummary.Empty -> EmptyResultBody(summary)
        is ToolResultSummary.Failed -> FailedBody(summary)
        is ToolResultSummary.Unknown -> UnknownBody(summary)
    }
}

@Composable
private fun ListHitsBody(summary: ToolResultSummary.ListHits) {
    Text(
        text = "${summary.title} · ${summary.count} 条",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp)
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(summary.items) { item ->
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "·",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SavedOneBody(summary: ToolResultSummary.SavedOne) {
    Text(
        text = "${summary.title} · ${summary.subject}",
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun ScheduledBody(summary: ToolResultSummary.Scheduled) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "${summary.title} · ${summary.subject}",
            style = MaterialTheme.typography.bodyMedium,
        )
        summary.triggerAtMillis?.let {
            Text(
                text = "触发时间:${formatTime(it)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = if (summary.exact) "精确闹钟" else "非精确闹钟",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun KeyValueReportBody(summary: ToolResultSummary.KeyValueReport) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (summary.pairs.isEmpty()) {
            Text(
                text = "(无数据)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            summary.pairs.forEach { (key, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = key,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyResultBody(summary: ToolResultSummary.Empty) {
    Text(
        text = "${summary.title} · 暂无结果",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun FailedBody(summary: ToolResultSummary.Failed) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = summary.title,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "原因:${summary.reason}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        summary.hint?.takeIf { it.isNotBlank() }?.let { hint ->
            Text(
                text = "提示:$hint",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UnknownBody(summary: ToolResultSummary.Unknown) {
    Text(
        text = summary.raw.ifBlank { "(工具未返回结果)" },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = FontFamily.Monospace,
    )
}

@Composable
private fun EmptyState() {
    Text(
        text = "(暂无详情)",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Footer(durationMs: Long) {
    Text(
        text = "耗时:${durationMs}ms",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun statusIcon(status: ToolCallStatus): ImageVector = when (status) {
    ToolCallStatus.SUCCEEDED -> Icons.Filled.CheckCircle
    ToolCallStatus.FAILED -> Icons.Filled.ErrorOutline
    ToolCallStatus.STARTED -> Icons.Filled.HourglassEmpty
}

@Composable
private fun statusColor(status: ToolCallStatus): Color = when (status) {
    ToolCallStatus.SUCCEEDED -> ChatStatusColors.SuccessDot
    ToolCallStatus.FAILED -> MaterialTheme.colorScheme.error
    ToolCallStatus.STARTED -> MaterialTheme.colorScheme.tertiary
}

private fun formatTime(millis: Long): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return fmt.format(Date(millis))
}
