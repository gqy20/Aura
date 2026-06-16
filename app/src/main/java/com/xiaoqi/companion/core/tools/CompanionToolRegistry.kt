package com.xiaoqi.companion.core.tools

import ai.koog.agents.core.tools.ToolRegistry
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.core.mcp.McpRemoteTool
import com.xiaoqi.companion.core.mcp.RemoteMcpClient
import com.xiaoqi.companion.data.repository.McpServerListRepository
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
    private val queryHealthDataTool: QueryHealthDataTool,
    private val updateStateTool: UpdateStateTool,
    private val remoteMcpClient: RemoteMcpClient,
    private val mcpServerListRepository: McpServerListRepository,
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
            .tool(queryHealthDataTool)
            .tool(updateStateTool)

        addRemoteMcpTools(builder)
        return builder.build()
    }

    private fun addRemoteMcpTools(builder: ai.koog.agents.core.tools.ToolRegistryBuilder) {
        // 多 server 模式:遍历所有 enabled=true 且 isReady=true 的 server,分别调 listTools。
        // 任何单个 server 失败不影响其他 server 的工具注册。
        val servers = runCatching {
            runBlocking { mcpServerListRepository.readAll() }
        }.getOrElse { error ->
            AppLogger.warn(
                LogTags.Llm,
                "mcp_servers_read_failed",
                "message" to (error.message ?: error::class.simpleName.orEmpty()),
            )
            return
        }
        val readyServers = servers.filter { it.enabled && it.isReady }
        if (readyServers.isEmpty()) return

        var totalCount = 0
        readyServers.forEach { server ->
            val url = server.resolvedUrl
            val name = server.resolvedName
            runCatching {
                runBlocking { remoteMcpClient.listTools(url) }
            }.onSuccess { specs ->
                specs.forEach { spec ->
                    builder.tool(
                        McpRemoteTool(
                            serverUrl = url,
                            serverName = name,
                            spec = spec,
                            client = remoteMcpClient,
                        )
                    )
                }
                totalCount += specs.size
                AppLogger.info(
                    LogTags.Llm,
                    "mcp_tools_registered",
                    "count" to specs.size,
                    "serverId" to server.id,
                    "serverName" to name,
                )
            }.onFailure { error ->
                AppLogger.warn(
                    LogTags.Llm,
                    "mcp_tools_register_failed",
                    "serverId" to server.id,
                    "message" to (error.message ?: error::class.simpleName.orEmpty()),
                )
            }
        }
        if (totalCount > 0) {
            AppLogger.info(
                LogTags.Llm,
                "mcp_tools_registered_summary",
                "serverCount" to readyServers.size,
                "toolCount" to totalCount,
            )
        }
    }
}
