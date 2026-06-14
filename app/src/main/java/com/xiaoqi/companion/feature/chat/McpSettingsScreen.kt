package com.xiaoqi.companion.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
        onMcpServerNameChanged = viewModel::updateMcpSettingsName,
        onMcpHttpUrlChanged = viewModel::updateMcpSettingsUrl,
        onSave = viewModel::saveMcpSettings,
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
    onMcpServerNameChanged: (String) -> Unit,
    onMcpHttpUrlChanged: (String) -> Unit,
    onSave: () -> Unit,
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
