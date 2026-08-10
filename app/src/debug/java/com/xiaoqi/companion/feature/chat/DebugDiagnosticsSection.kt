package com.xiaoqi.companion.feature.chat

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.ui.theme.ChatCardSurface
import kotlinx.coroutines.launch

@Composable
internal fun DebugDiagnosticsSection() {
    val context = LocalContext.current
    val exporter = remember(context) { DebugDiagnosticExporter(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsSectionTitle(
            title = "Debug 诊断",
            subtitle = "仅 Debug APK 可见 · 导出前自动脱敏",
        )
        ChatCardSurface {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "生成包含版本信息、运行日志和最近崩溃记录的 ZIP，方便在真机测试后直接反馈问题。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isExporting = true
                            status = null
                            runCatching { exporter.export() }
                                .onSuccess { archive ->
                                    shareDiagnosticArchive(context, archive.file)
                                    status = if (archive.includesCrashLog) {
                                        "诊断包已生成，包含最近一次崩溃记录"
                                    } else {
                                        "诊断包已生成"
                                    }
                                }
                                .onFailure { error ->
                                    AppLogger.error(LogTags.App, error, "debug_diagnostics_export_failed")
                                    status = "导出失败，请重试"
                                }
                            isExporting = false
                        }
                    },
                    enabled = !isExporting && AppLogger.fileProvider() != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Text("正在生成…")
                    } else {
                        Text("导出诊断包")
                    }
                }
                status?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun shareDiagnosticArchive(context: android.content.Context, file: java.io.File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.debugdiagnostics",
        file,
    )
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "分享 Debug 诊断包"))
}
