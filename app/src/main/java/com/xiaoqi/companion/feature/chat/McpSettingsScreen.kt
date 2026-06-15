package com.xiaoqi.companion.feature.chat

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaoqi.companion.core.llm.ConnectivityResult
import com.xiaoqi.companion.core.mcp.McpServerConfig
import com.xiaoqi.companion.core.mcp.McpServerPresets

/**
 * MCP 设置页面 — 多 server 模式。
 *
 * - 列表模式:每个 server 一张卡片,显示 name / provider / enabled 开关。
 *   点卡片进 editor 编辑;点 "Add" 进 editor 新建。
 * - editor 模式:复用 [McpServerPresets] 简化表单 — amap 模式只让用户填 key,
 *   custom 模式让用户填 URL。带 "测试连接" 和 "保存/删除" 按钮。
 *
 * 内部用 [rememberSaveable] 保存当前模式,旋转屏幕不丢。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpSettingsScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableStateOf(McpSettingsMode.LIST) }
    val snackbarHostState = remember { SnackbarHostState() }

    // 一次性"已保存"事件:saveMcpSettings 成功时 UseCase 会 increment mcpEditorJustSaved,
    // UI 在这里观察到这个变化 → 切回 list + 弹 snackbar 给用户反馈。
    // key 用 mcpEditorJustSaved 而非 mcpSettingsMessage 是因为后者被 UseCase
    // 设回 null,只有这个 tick 能稳定地表达"刚保存"这个离散事件。
    LaunchedEffect(uiState.mcpEditorJustSaved) {
        if (uiState.mcpEditorJustSaved > 0L && mode == McpSettingsMode.EDITOR) {
            mode = McpSettingsMode.LIST
            snackbarHostState.showSnackbar("已保存")
        }
    }

    when (mode) {
        McpSettingsMode.LIST -> McpListScreen(
            servers = uiState.toolCapabilitySettings.mcpServers,
            serverTools = uiState.mcpServerTools,
            isCheckingConnectivity = uiState.isCheckingConnectivity,
            connectivityResult = uiState.mcpConnectivityResult,
            snackbarHostState = snackbarHostState,
            onBack = onBack,
            onAdd = {
                viewModel.startNewMcpSettings()
                mode = McpSettingsMode.EDITOR
            },
            onEdit = { id ->
                viewModel.loadMcpServerForEditing(id)
                mode = McpSettingsMode.EDITOR
            },
            onToggleEnabled = { id -> viewModel.toggleMcpServerEnabled(id) },
            onTestConnection = { viewModel.checkMcpConnectivity() },
        )
        McpSettingsMode.EDITOR -> McpEditorScreen(
            viewModel = viewModel,
            uiState = uiState,
            snackbarHostState = snackbarHostState,
            onBack = { mode = McpSettingsMode.LIST },
        )
    }
}

private enum class McpSettingsMode { LIST, EDITOR }

//region List Mode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun McpListScreen(
    servers: List<McpServerConfig>,
    serverTools: Map<String, List<String>>,
    isCheckingConnectivity: Boolean,
    connectivityResult: ConnectivityResult?,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onToggleEnabled: (String) -> Unit,
    onTestConnection: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("MCP") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = if (servers.isEmpty()) {
                        "还没有 MCP 服务，点下方添加高德或其他自定义。"
                    } else {
                        "共 ${servers.size} 个，点卡片编辑，关右侧开关可临时停用。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(items = servers, key = { it.id }) { server ->
                McpServerCard(
                    server = server,
                    discoveredTools = serverTools[server.id],
                    onClick = { onEdit(server.id) },
                    onToggleEnabled = { onToggleEnabled(server.id) },
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(
                        onClick = onAdd,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("添加 MCP")
                    }
                    if (servers.isNotEmpty()) {
                        OutlinedButton(
                            onClick = onTestConnection,
                            enabled = !isCheckingConnectivity,
                        ) {
                            Text("测试连接")
                        }
                    }
                }
            }
            if (isCheckingConnectivity) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = "检查中…",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            connectivityResult?.let { result ->
                item {
                    val (text, color) = when (result) {
                        is ConnectivityResult.Success ->
                            "OK · ${result.modelName}" to Color(0xFF2E7D32)
                        is ConnectivityResult.AuthFailure ->
                            "鉴权失败" to MaterialTheme.colorScheme.error
                        is ConnectivityResult.Unreachable ->
                            "不可达: ${result.cause}" to MaterialTheme.colorScheme.error
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                    )
                }
            }
        }
    }
}

@Composable
private fun McpServerCard(
    server: McpServerConfig,
    discoveredTools: List<String>?,
    onClick: () -> Unit,
    onToggleEnabled: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (server.enabled) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = server.resolvedName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = when {
                        !server.enabled -> "已停用"
                        !server.isReady -> "未配置（缺 ${if (server.providerId == "amap") "API Key" else "URL"}）"
                        discoveredTools == null -> "已配置 · 未发现工具，点测试连接"
                        discoveredTools.isEmpty() -> "已连接 · 0 个工具"
                        else -> "已连接 · ${discoveredTools.size} 个工具（${discoveredTools.take(3).joinToString(", ")}${if (discoveredTools.size > 3) ", …" else ""}）"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = server.enabled,
                onCheckedChange = { onToggleEnabled() },
            )
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onClick) {
                Icon(Icons.Filled.Edit, contentDescription = "编辑")
            }
        }
    }
}

//endregion

//region Discovered tools

/**
 * "已发现的工具"区 — 只在已编辑/已保存的 server 上显示。
 *
 * 状态:
 * - null (没探过)        → 提示"未发现"
 * - emptyList() (探过)    → 警告"0 个工具"
 * - non-empty             → 列出所有工具名(全宽 grid 风格)
 */
@Composable
private fun DiscoveredToolsSection(
    tools: List<String>?,
    isLoading: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "已发现工具",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = Color(0xFFF7F2EA),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when {
                    isLoading -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Text("发现中…", style = MaterialTheme.typography.bodySmall)
                    }
                    tools == null -> Text(
                        "尚未发现工具。点下方\"测试连接并发现工具\"。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    tools.isEmpty() -> Text(
                        "已连接，但服务器未提供工具。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    else -> androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        tools.forEach { name ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = Color.White,
                                shadowElevation = 1.dp,
                            ) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF496B5E),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

//endregion

//region Editor Mode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun McpEditorScreen(
    viewModel: ChatViewModel,
    uiState: ChatUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
) {
    val settings = uiState
    val preset = McpServerPresets.byId(settings.mcpSettingsProviderId)
    val isEditing = settings.mcpEditingServerId != null

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "编辑 MCP" else "添加 MCP") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = "服务商",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.size(4.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    McpServerPresets.all.forEachIndexed { i, p ->
                        SegmentedButton(
                            selected = p.id == settings.mcpSettingsProviderId,
                            onClick = { viewModel.selectMcpProvider(p.id) },
                            shape = SegmentedButtonDefaults.itemShape(i, McpServerPresets.all.size),
                        ) { Text(p.displayName) }
                    }
                }
                Spacer(Modifier.size(4.dp))
                Text(
                    text = preset.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                HorizontalDivider()
                OutlinedTextField(
                    value = settings.mcpSettingsName,
                    onValueChange = viewModel::updateMcpSettingsName,
                    label = { Text("显示名（可选）") },
                    placeholder = { Text(preset.displayName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (preset is com.xiaoqi.companion.core.mcp.TemplatedMcpServerPreset) {
                item {
                    OutlinedTextField(
                        value = settings.mcpSettingsApiKey,
                        onValueChange = viewModel::updateMcpSettingsApiKey,
                        label = { Text("${preset.keyHint} *") },
                        placeholder = { Text(preset.keyPlaceholder) },
                        singleLine = true,
                        visualTransformation = if (settings.mcpSettingsKeyVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = viewModel::toggleMcpKeyVisibility) {
                                Icon(
                                    imageVector = if (settings.mcpSettingsKeyVisible) {
                                        Icons.Filled.VisibilityOff
                                    } else {
                                        Icons.Filled.Visibility
                                    },
                                    contentDescription = if (settings.mcpSettingsKeyVisible) {
                                        "隐藏"
                                    } else {
                                        "显示"
                                    },
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = Color(0xFFF7F2EA),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "接入地址（只读）",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = preset.urlTemplate.replace(
                                    "{key}",
                                    settings.mcpSettingsApiKey.ifBlank { "<your-key>" },
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            } else {
                item {
                    OutlinedTextField(
                        value = settings.mcpSettingsUrl,
                        onValueChange = viewModel::updateMcpSettingsUrl,
                        label = { Text("MCP URL *") },
                        placeholder = { Text("https://example.com/mcp") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            settings.mcpSettingsMessage?.let { msg ->
                item {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // "已发现的工具"区:仅在编辑现有 server 时显示(新建的还没保存,没 server id 关联)
            if (isEditing) {
                val toolsForServer = settings.mcpEditingServerId
                    ?.let { id -> settings.mcpServerTools[id] }
                item {
                    DiscoveredToolsSection(
                        tools = toolsForServer,
                        isLoading = settings.isCheckingConnectivity,
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { viewModel.checkMcpConnectivity() },
                        enabled = !settings.isCheckingConnectivity,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (settings.isCheckingConnectivity) "发现中…" else "测试连接并发现工具")
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isEditing) {
                        OutlinedButton(
                            onClick = {
                                viewModel.removeMcpServer(settings.mcpEditingServerId!!)
                                onBack()
                            },
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("删除")
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onBack) { Text("取消") }
                    Button(onClick = viewModel::saveMcpSettings) { Text("保存") }
                }
            }
        }
    }
}

//endregion
