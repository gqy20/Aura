package com.xiaoqi.companion.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.xiaoqi.companion.feature.chat.map.MapToolInteraction

@Composable
fun MapResultCard(
    interaction: MapToolInteraction,
    onOpenMap: () -> Unit,
    onAdjustRoute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (interaction is MapToolInteraction.Place) Modifier.clickable(onClick = onOpenMap)
                else Modifier
            ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Map,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    when (interaction) {
                        is MapToolInteraction.Place -> {
                            Text(interaction.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = interaction.address.ifBlank { interaction.coordinate.queryValue },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                        is MapToolInteraction.Route -> {
                            Text("${interaction.travelMode.label}路线", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = compactRouteMetric(interaction),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (interaction is MapToolInteraction.Place) {
                    Text(
                        text = "打开",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (interaction is MapToolInteraction.Route) {
                Text(
                    text = "${interaction.origin.queryValue}  →  ${interaction.destination.queryValue}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onOpenMap) { Text("打开地图") }
                    FilledTonalButton(onClick = onAdjustRoute) {
                        Icon(Icons.Filled.SwapVert, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.size(5.dp))
                        Text("调整路线")
                    }
                }
            }
        }
    }
}

private fun compactRouteMetric(route: MapToolInteraction.Route): String = buildList {
    route.distanceMeters?.let { distance ->
        add(if (distance >= 1000) "%.1f km".format(distance / 1000.0) else "$distance m")
    }
    route.durationSeconds?.let { duration -> add("约 ${duration / 60} 分钟") }
}.joinToString(" · ").ifBlank { "路线已查询" }
