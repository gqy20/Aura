package com.xiaoqi.companion.feature.chat

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaoqi.companion.core.llm.ConnectivityResult

@Composable
fun McpSettingsScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    McpSettingsScreenContent(
        mcpServerName = uiState.mcpSettingsName,
        mcpHttpUrl = uiState.mcpSettingsUrl,
        currentMcpServerName = uiState.toolCapabilitySettings.mcpServerName,
        currentMcpHttpUrl = uiState.toolCapabilitySettings.mcpHttpUrl,
        message = uiState.mcpSettingsMessage,
        mcpConnectivityResult = uiState.mcpConnectivityResult,
        isCheckingMcp = uiState.isCheckingConnectivity,
        onMcpServerNameChanged = viewModel::updateMcpSettingsName,
        onMcpHttpUrlChanged = viewModel::updateMcpSettingsUrl,
        onSave = viewModel::saveMcpSettings,
        onTestConnection = viewModel::checkMcpConnectivity,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun McpSettingsScreenContent(
    mcpServerName: String,
    mcpHttpUrl: String,
    currentMcpServerName: String,
    currentMcpHttpUrl: String,
    message: String?,
    mcpConnectivityResult: ConnectivityResult?,
    isCheckingMcp: Boolean,
    onMcpServerNameChanged: (String) -> Unit,
    onMcpHttpUrlChanged: (String) -> Unit,
    onSave: () -> Unit,
    onTestConnection: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("MCP") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "Connection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Remote tools endpoint.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = mcpServerName,
                    onValueChange = onMcpServerNameChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name") },
                    placeholder = { Text("browser, notes, research") },
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = mcpHttpUrl,
                    onValueChange = onMcpHttpUrlChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("URL") },
                    placeholder = { Text("https://example.com/mcp") },
                    singleLine = true,
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onTestConnection,
                        enabled = !isCheckingMcp,
                    ) {
                        Text("Test connection")
                    }
                    if (isCheckingMcp) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    val (text, color) = when (mcpConnectivityResult) {
                        null -> "" to MaterialTheme.colorScheme.onSurfaceVariant
                        is ConnectivityResult.Success -> {
                            "OK · ${mcpConnectivityResult.modelName}" to Color(0xFF2E7D32)
                        }
                        is ConnectivityResult.AuthFailure -> {
                            "鉴权失败" to MaterialTheme.colorScheme.error
                        }
                        is ConnectivityResult.Unreachable -> {
                            "不可达: ${mcpConnectivityResult.cause}" to MaterialTheme.colorScheme.error
                        }
                    }
                    if (text.isNotEmpty()) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.labelSmall,
                            color = color,
                            maxLines = 1,
                        )
                    }
                }
            }
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = Color(0xFFF7F2EA),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            text = if (currentMcpHttpUrl.isBlank()) {
                                "Not connected"
                            } else {
                                currentMcpServerName.ifBlank { "Connected" }
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = currentMcpHttpUrl.ifBlank {
                                "Used in chat."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                message?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onBack) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onSave) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
