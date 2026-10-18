package com.xiaoqi.companion.feature.record

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.xiaoqi.companion.data.db.converter.MealType
import com.xiaoqi.companion.ui.theme.ChatCardSurface
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val MEAL_TYPE_SHORT = mapOf(
    MealType.BREAKFAST to "早",
    MealType.LUNCH to "午",
    MealType.DINNER to "晚",
    MealType.SNACK to "加餐",
)
private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordHistoryScreen(
    viewModel: RecordHistoryViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("记录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        if (state.days.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("还没有记录", style = MaterialTheme.typography.titleMedium)
                Text(
                    "从主页「记一餐」开始,拍一张照片试试",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.days.forEach { day ->
                    item(key = "day-${day.date}") { DayHeader(day) }
                    items(day.entries, key = { it.id }) { entry ->
                        HistoryEntryCard(
                            entry = entry,
                            expanded = state.expandedEntryId == entry.id,
                            onToggle = { viewModel.toggleExpand(entry.id) },
                            onDelete = { viewModel.deleteEntry(entry.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(day: HistoryDay) {
    val label = when (day.date) {
        LocalDate.now() -> "今天"
        LocalDate.now().minusDays(1) -> "昨天"
        else -> day.date.format(DateTimeFormatter.ofPattern("M月d日"))
    }
    // 行内合并:日期 + 当天小计(餐 kcal / 采 购件数),不给小计单独一行
    val meals = day.entries.filterIsInstance<HistoryEntry.Meal>()
    val kcal = meals.sumOf { it.record.record.totalCalories }
    val summary = buildString {
        if (meals.isNotEmpty()) append("餐 ${kcal.toInt()} kcal")
        val purchases = day.entries.filterIsInstance<HistoryEntry.Purchase>()
        if (purchases.isNotEmpty()) {
            if (isNotEmpty()) append(" · ")
            append("购 ${purchases.sumOf { it.record.items.size }} 件")
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        if (summary.isNotBlank()) {
            Text(
                summary,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 左滑删除卡片:向左滑露出红色垃圾桶背景,点击垃圾桶或滑到底触发删除;
 * 点卡片本体展开/收起明细(两行:摘要行 + 展开的明细/原图)。
 */
@Composable
private fun HistoryEntryCard(
    entry: HistoryEntry,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    androidx.compose.runtime.LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            // 垃圾桶背景:点击也触发删除(轻扫到一半时更顺手)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除记录",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        ChatCardSurface(
            modifier = Modifier.clickable(onClick = onToggle),
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = if (entry is HistoryEntry.Meal) Icons.Default.Restaurant else Icons.Default.ShoppingCart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = firstLineSummary(entry),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                AnimatedVisibility(visible = expanded) {
                    Column {
                        DetailLines(entry)
                        ImageStrip(entry)
                    }
                }
            }
        }
    }
}

private fun firstLineSummary(entry: HistoryEntry): String {
    val time = TIME_FORMAT.format(Instant.ofEpochMilli(entry.timeMillis).atZone(ZoneId.systemDefault()))
    return when (entry) {
        is HistoryEntry.Meal -> {
            val type = MEAL_TYPE_SHORT[entry.mealType] ?: ""
            val kcal = entry.record.record.totalCalories.toInt()
            val names = entry.record.items.joinToString("、") { it.name }
            "$time $type $kcal kcal · $names"
        }
        is HistoryEntry.Purchase -> {
            val store = entry.record.record.storeName ?: "采购"
            val count = entry.record.items.size
            val names = entry.record.items.joinToString("、") { it.name }
            "$time $store · $count 件 · $names"
        }
    }
}

@Composable
private fun DetailLines(entry: HistoryEntry) {
    val lines = when (entry) {
        is HistoryEntry.Meal -> entry.record.items.map { item ->
            "${item.name} · ${item.estCalories.toInt()} kcal · 蛋白 ${item.estProteinG.toInt()}g · ${item.portionText}"
        }
        is HistoryEntry.Purchase -> entry.record.items.map { item ->
            buildString {
                append("${item.name} · ${formatQty(item.quantity)} ${item.unit}")
                item.brand?.let { append(" · $it") }
                item.unitPrice?.let { append(" · %.2f 元".format(it)) }
            }
        }
    }
    lines.forEach { line ->
        Text(
            line,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun ImageStrip(entry: HistoryEntry) {
    val paths: List<String> = when (entry) {
        is HistoryEntry.Meal -> entry.record.record.imageLocalPath?.split(",") ?: emptyList()
        is HistoryEntry.Purchase -> entry.record.record.imageLocalPath?.split(",") ?: emptyList()
    }
    if (paths.isEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 6.dp),
    ) {
        paths.take(4).forEach { path ->
            val file = File(path)
            if (file.exists()) {
                AsyncImage(
                    model = file,
                    contentDescription = "记录原图",
                    modifier = Modifier
                        .height(64.dp)
                        .width(88.dp)
                        .clip(MaterialTheme.shapes.small),
                )
            }
        }
    }
}

private fun formatQty(q: Double): String =
    if (q == q.toLong().toDouble()) "${q.toLong()}" else "%.1f".format(q)
