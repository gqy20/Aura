package com.xiaoqi.companion.feature.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import com.xiaoqi.companion.data.source.HealthConnectDataSource
import com.xiaoqi.companion.data.source.HealthSyncManager
import com.xiaoqi.companion.data.source.SensorManagerHealthSource
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

/**
 * M7 Health Connect 设置区。
 *
 * 显示:
 * - Health Connect 三个权限开关状态(步数 / 心率 / 睡眠),带"去授权"按钮
 * - **本机传感器** 状态(步数) — 当 HC 不可用时是唯一数据源
 * - 上次同步时间(显示为"N 分钟前 / 刚刚 / 从未同步")
 * - 同步状态指示(Loading / 失败原因)
 * - "自动同步"开关
 * - "立即同步"按钮(force=true 绕过防抖)
 */
@Composable
fun HealthDataSection(
    syncState: HealthSyncManager.SyncState,
    autoSyncEnabled: Boolean,
    lastSyncAtMillis: Long,
    onAutoSyncEnabledChanged: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    healthConnectDataSource: HealthConnectDataSource,
    sensorSource: SensorManagerHealthSource,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsSectionTitle(
            title = "健康数据接入",
            subtitle = "Health Connect · 步数/心率/睡眠；HC 不可用时用本机传感器兜底步数",
        )

        HealthPermissionsCard(dataSource = healthConnectDataSource)
        SensorSourceCard(sensorSource = sensorSource)

        HealthSyncStatusCard(
            syncState = syncState,
            lastSyncAtMillis = lastSyncAtMillis,
            autoSyncEnabled = autoSyncEnabled,
            onAutoSyncEnabledChanged = onAutoSyncEnabledChanged,
            onSyncNow = onSyncNow,
        )
    }
}

@Composable
private fun HealthPermissionsCard(dataSource: HealthConnectDataSource) {
    var probeResult by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var sdkAvailable by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    /**
     * 用真 read 反推权限。原因:某些 ROM 的 `getGrantedPermissions()` 在
     * `enforceValidPackage` 阶段会拒绝 debug 包,直接抛 `invalid package`,
     * UI 永远显示"未授权"。readRecords 走的是另一条路径,能跑通就证明权限真的开了。
     */
    val refresh = {
        scope.launch {
            sdkAvailable = dataSource.isAvailable()
            if (sdkAvailable) {
                probeResult = mapOf(
                    HealthConnectDataSource.READ_STEPS to dataSource.probePermission(HealthConnectDataSource.READ_STEPS),
                    HealthConnectDataSource.READ_HEART_RATE to dataSource.probePermission(HealthConnectDataSource.READ_HEART_RATE),
                    HealthConnectDataSource.READ_SLEEP to dataSource.probePermission(HealthConnectDataSource.READ_SLEEP),
                )
            } else {
                probeResult = emptyMap()
            }
        }
    }
    LaunchedEffect(Unit) { refresh() }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { _: Set<String> ->
        // contract 自身也走 `enforceValidPackage`,在 realme 上不可靠。
        // 不管返回啥,都强制重新 probe 一次拿真信号。
        refresh()
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = Color(0xFFF7F2EA),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                !sdkAvailable -> {
                    Text(
                        text = "本机未集成 Health Connect 运行时",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "部分 ROM（如 ColorOS）只装了 HC APK 但未挂载服务，无法授权。Pixel / 国行三星 / 部分小米机型可用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    PermissionRow("步数", HealthConnectDataSource.READ_STEPS, probeResult[HealthConnectDataSource.READ_STEPS])
                    PermissionRow("心率", HealthConnectDataSource.READ_HEART_RATE, probeResult[HealthConnectDataSource.READ_HEART_RATE])
                    PermissionRow("睡眠", HealthConnectDataSource.READ_SLEEP, probeResult[HealthConnectDataSource.READ_SLEEP])
                    Text(
                        text = "说明：状态以实际 readRecords 是否成功为准（部分 ROM 的 HC 控制器无法查询已授权列表）。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = {
                            val perms = setOf(
                                HealthPermission.getReadPermission(StepsRecord::class),
                                HealthPermission.getReadPermission(HeartRateRecord::class),
                                HealthPermission.getReadPermission(SleepSessionRecord::class),
                            )
                            permissionLauncher.launch(perms)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("去系统授权")
                    }
                    Spacer(Modifier.height(4.dp))
                    // 兜底:跳过 SDK contract,直接拉系统 HC 控制器的 SettingsActivity。
                    // 适用于 controller APK 没 launcher 图标、contract 弹不出的 ROM。
                    TextButtonCompat(
                        onClick = {
                            val intent = Intent().apply {
                                setClassName(
                                    "com.android.healthconnect.controller",
                                    "com.android.healthconnect.controller.permissions.shared.SettingsActivity",
                                )
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            runCatching { context.startActivity(intent) }
                                .onFailure { err ->
                                    runCatching {
                                        context.startActivity(
                                            Intent().apply {
                                                setClassName(
                                                    "com.android.healthconnect.controller",
                                                    "com.android.healthconnect.controller.MainActivity",
                                                )
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            },
                                        )
                                    }.onFailure {
                                        android.util.Log.w("HealthDataSection", "no HC settings", err)
                                    }
                                }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("打开系统 Health Connect")
                    }
                }
            }
        }
    }
}

/**
 * 本机传感器卡片 — 显示:
 * - ACTIVITY_RECOGNITION 权限状态
 * - TYPE_STEP_COUNTER 硬件存在
 * - 若两者都 ok,显示"✓ 已支持(本机步数)"
 * - 若权限未授,显示"授予活动识别"按钮(Android 10+ 必须)
 */
@Composable
private fun SensorSourceCard(sensorSource: SensorManagerHealthSource) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasPermission by remember {
        mutableStateOf(hasActivityRecognitionGranted(context))
    }
    var isAvailable by remember { mutableStateOf(false) }
    var hasHardware by remember { mutableStateOf(false) }

    val refresh = {
        scope.launch {
            hasPermission = hasActivityRecognitionGranted(context)
            hasHardware = sensorSource.let {
                val sm = context.getSystemService(android.hardware.SensorManager::class.java)
                sm?.getDefaultSensor(android.hardware.Sensor.TYPE_STEP_COUNTER) != null
            }
            isAvailable = sensorSource.isAvailable()
        }
    }
    LaunchedEffect(Unit) { refresh() }

    val activityRecognitionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _: Boolean ->
        refresh()
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = Color(0xFFF7F2EA),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "本机传感器兜底",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "HC 不可用时（如国行 ROM、realme），用本机传感器统计今日步数。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SensorRow("活动识别", if (hasPermission) "已授予" else "未授予",
                ok = hasPermission, error = !hasPermission)
            SensorRow("计步传感器", if (hasHardware) "已内置" else "不支持",
                ok = hasHardware, error = !hasHardware)
            SensorRow("传感器", if (isAvailable) "可用" else "不可用",
                ok = isAvailable, error = !isAvailable)
            if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                OutlinedButton(
                    onClick = {
                        activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("授予活动识别")
                }
            }
        }
    }
}

private fun hasActivityRecognitionGranted(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACTIVITY_RECOGNITION,
    ) == PackageManager.PERMISSION_GRANTED
}

@Composable
private fun SensorRow(label: String, statusText: String, ok: Boolean, error: Boolean) {
    val color = when {
        ok -> Color(0xFF2E7D32)
        error -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TextButtonCompat(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick, modifier = modifier) { content() }
}

@Composable
private fun PermissionRow(label: String, perm: String, granted: Boolean?) {
    val (text, color) = when (granted) {
        null -> "…" to MaterialTheme.colorScheme.onSurfaceVariant
        true -> "✓ 已授权" to Color(0xFF2E7D32)
        false -> "✗ 未授权" to MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun HealthSyncStatusCard(
    syncState: HealthSyncManager.SyncState,
    lastSyncAtMillis: Long,
    autoSyncEnabled: Boolean,
    onAutoSyncEnabledChanged: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = Color(0xFFF7F2EA),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val (statusText, statusColor) = describeSyncState(syncState)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "状态",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (syncState is HealthSyncManager.SyncState.Syncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.size(6.dp))
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Text(
                text = "上次同步: ${formatLastSync(lastSyncAtMillis)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "自动同步", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "回到 App 时自动拉取近 7 天数据",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = autoSyncEnabled, onCheckedChange = onAutoSyncEnabledChanged)
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onSyncNow,
                enabled = syncState !is HealthSyncManager.SyncState.Syncing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("立即同步")
            }
        }
    }
}

@Composable
private fun describeSyncState(state: HealthSyncManager.SyncState): Pair<String, Color> = when (state) {
    is HealthSyncManager.SyncState.Idle -> "空闲" to MaterialTheme.colorScheme.onSurfaceVariant
    is HealthSyncManager.SyncState.Syncing -> "同步中…" to MaterialTheme.colorScheme.primary
    is HealthSyncManager.SyncState.Skipped -> "防抖 · 上次同步在 ${humanizeDuration(state.sinceLastMs)} 前" to MaterialTheme.colorScheme.onSurfaceVariant
    is HealthSyncManager.SyncState.Success -> "成功 · ${state.daysWithData} 天有数据" to Color(0xFF2E7D32)
    is HealthSyncManager.SyncState.Failure -> "失败: ${state.reason}" to MaterialTheme.colorScheme.error
}

private fun humanizeDuration(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    if (minutes < 1) return "刚刚"
    if (minutes < 60) return "$minutes 分钟"
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    if (hours < 24) return "$hours 小时"
    val days = TimeUnit.MILLISECONDS.toDays(ms)
    return "$days 天"
}

private fun formatLastSync(atMillis: Long): String {
    if (atMillis == 0L) return "从未同步"
    val delta = System.currentTimeMillis() - atMillis
    if (delta < 0) return "刚刚"
    return humanizeDuration(delta) + "前"
}
