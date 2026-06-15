package com.xiaoqi.companion.feature.insight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiaoqi.companion.data.db.entity.MoodSnapshotEntity
import java.util.Calendar

/**
 * Mood Trend 简易周柱状图(plan §9 M3 KPI #3)。
 *
 * 不引第三方图表库 — 4 根柱 + 横轴 week label + y 轴 intensity 0~1 即可。
 * 数据源:`MoodSnapshotDao.observeByDateRange(companionId, start, end)`。
 */
@Composable
internal fun MoodTrendChartSection(
    snapshots: List<MoodSnapshotEntity>,
    modifier: Modifier = Modifier,
) {
    val byWeek = remember(snapshots) {
        snapshots.groupBy {
            val cal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            val week = cal.get(Calendar.WEEK_OF_YEAR)
            week to cal.get(Calendar.YEAR)
        }.toSortedMap(compareBy { it.second * 100 + it.first })
    }

    val weekAverages = byWeek.entries.map { (weekKey, list) ->
        WeekAverage(
            label = "W${weekKey.first}",
            averageIntensity = list.map { it.intensity }.average().toFloat(),
        )
    }.takeLast(4)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "心情趋势",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
        )
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = Color(0xFFFFF8EA),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (weekAverages.isEmpty()) {
                    Text(
                        text = "暂未收集到情绪数据 — Aura 会在你聊几周后开始发现规律",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    BarChart(
                        values = weekAverages,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        weekAverages.forEach { week ->
                            Text(
                                text = week.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(4.dp))
}

private data class WeekAverage(val label: String, val averageIntensity: Float)

@Composable
private fun BarChart(
    values: List<WeekAverage>,
    modifier: Modifier = Modifier,
) {
    val onColor = Color(0xFF2E7D32)
    val offColor = Color(0xFFE5A100)
    val coldColor = Color(0xFFB39DDB)
    Canvas(modifier = modifier) {
        if (values.isEmpty()) return@Canvas
        val barWidth = size.width / (values.size * 1.8f)
        val gap = (size.width - barWidth * values.size) / (values.size + 1)
        values.forEachIndexed { index, week ->
            val x = gap + index * (barWidth + gap)
            val barHeight = size.height * week.averageIntensity.coerceIn(0f, 1f)
            val y = size.height - barHeight
            val color = when {
                week.averageIntensity >= 0.66f -> onColor
                week.averageIntensity >= 0.4f -> offColor
                else -> coldColor
            }
            drawRoundedBar(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
            )
        }
    }
}

private fun DrawScope.drawRoundedBar(
    color: Color,
    topLeft: Offset,
    size: Size,
) {
    drawRect(
        color = color,
        topLeft = topLeft,
        size = size,
    )
}
