package com.xiaoqi.companion.core.tools

import ai.koog.agents.core.tools.ToolRegistry
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.core.mcp.McpRemoteTool
import com.xiaoqi.companion.core.mcp.RemoteMcpClient
import com.xiaoqi.companion.data.datastore.AppPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

interface AgentToolRegistry {
    fun create(): ToolRegistry
}

class CompanionToolRegistry @Inject constructor(
    private val searchMemoryTool: SearchMemoryTool,
    private val searchRecordsTool: SearchRecordsTool,
    private val searchSummariesTool: SearchSummariesTool,
    private val getCurrentTimeTool: GetCurrentTimeTool,
    private val getRecentInteractionContextTool: GetRecentInteractionContextTool,
    private val getUserContextSettingsTool: GetUserContextSettingsTool,
    private val getDeviceStatusTool: GetDeviceStatusTool,
    private val getWeatherTool: GetWeatherTool,
    private val createLocalReminderTool: CreateLocalReminderTool,
    private val appPreferences: AppPreferences,
    private val remoteMcpClient: RemoteMcpClient,
) : AgentToolRegistry {
    override fun create(): ToolRegistry {
        val builder = ToolRegistry.builder()
            .tool(searchMemoryTool)
            .tool(searchRecordsTool)
            .tool(searchSummariesTool)
            .tool(getCurrentTimeTool)
            .tool(getRecentInteractionContextTool)
            .tool(getUserContextSettingsTool)
            .tool(getDeviceStatusTool)
            .tool(getWeatherTool)
            .tool(createLocalReminderTool)

        addRemoteMcpTools(builder)
        return builder.build()
    }

    private fun addRemoteMcpTools(builder: ai.koog.agents.core.tools.ToolRegistryBuilder) {
        val serverName = runBlocking { appPreferences.mcpServerName.first() }.trim()
        val serverUrl = runBlocking { appPreferences.mcpHttpUrl.first() }.trim()
        if (serverUrl.isBlank()) return

        runCatching {
            runBlocking { remoteMcpClient.listTools(serverUrl) }
        }.onSuccess { specs ->
            specs.forEach { spec ->
                builder.tool(
                    McpRemoteTool(
                        serverUrl = serverUrl,
                        serverName = serverName,
                        spec = spec,
                        client = remoteMcpClient,
                    )
                )
            }
            AppLogger.info(
                LogTags.Llm,
                "mcp_tools_registered",
                "count" to specs.size,
                "serverName" to serverName.ifBlank { serverUrl },
            )
        }.onFailure { error ->
            AppLogger.warn(
                LogTags.Llm,
                "mcp_tools_register_failed",
                "message" to (error.message ?: error::class.simpleName.orEmpty()),
            )
        }
    }
}
