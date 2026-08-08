package com.xiaoqi.companion.core.tools

import ai.koog.agents.core.tools.ToolRegistry
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.core.mcp.McpRemoteTool
import com.xiaoqi.companion.core.mcp.McpServerRouter
import com.xiaoqi.companion.core.mcp.McpServerConfig
import com.xiaoqi.companion.core.mcp.McpToolSpec
import com.xiaoqi.companion.core.mcp.McpToolSelector
import com.xiaoqi.companion.core.mcp.RemoteMcpClient
import com.xiaoqi.companion.data.datastore.AppPreferences
import com.xiaoqi.companion.data.repository.McpServerListRepository
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

interface AgentToolRegistry {
    fun create(scope: ToolScope = ToolScope.ALL, policy: ToolPolicy = ToolPolicy.chatDefault): ToolRegistry
    fun createForQuery(
        query: String,
        scope: ToolScope = ToolScope.ALL,
        policy: ToolPolicy = ToolPolicy.chatDefault,
    ): ToolRegistry = create(scope, policy)
    suspend fun warmMcpTools(policy: ToolPolicy = ToolPolicy.readOnly) = Unit
}

/** 控制 [CompanionToolRegistry.create] 注册哪些类别的工具。 */
enum class ToolScope {
    /** 系统内置 + MCP 全部注册(云端路径默认)。 */
    ALL,
    /** 仅注册系统内置工具(本地模型路径默认,避免 MCP 网络开销)。 */
    SYSTEM_ONLY,
    /** 仅注册 MCP 工具。 */
    MCP_ONLY,
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
    private val appPreferences: AppPreferences,
) : AgentToolRegistry {
    private val mcpToolSpecCache = ConcurrentHashMap<String, List<McpToolSpec>>()
    private val mcpFailureCooldowns = ConcurrentHashMap<String, Long>()

    override fun create(scope: ToolScope, policy: ToolPolicy): ToolRegistry {
        return createInternal(scope = scope, policy = policy, queryHint = null)
    }

    override fun createForQuery(query: String, scope: ToolScope, policy: ToolPolicy): ToolRegistry {
        return createInternal(scope = scope, policy = policy, queryHint = query)
    }

    private fun createInternal(scope: ToolScope, policy: ToolPolicy, queryHint: String?): ToolRegistry {
        val builder = ToolRegistry.builder()
        val includeSystem = scope == ToolScope.ALL || scope == ToolScope.SYSTEM_ONLY
        val includeMcp = scope == ToolScope.ALL || scope == ToolScope.MCP_ONLY

        val systemToolsEnabled = if (includeSystem) readSystemToolsPref() else false
        if (systemToolsEnabled) {
            if (policy.allows(ToolMetadataRegistry.searchMemory)) builder.tool(searchMemoryTool)
            if (policy.allows(ToolMetadataRegistry.searchRecords)) builder.tool(searchRecordsTool)
            if (policy.allows(ToolMetadataRegistry.searchSummaries)) builder.tool(searchSummariesTool)
            if (policy.allows(ToolMetadataRegistry.getCurrentTime)) builder.tool(getCurrentTimeTool)
            if (policy.allows(ToolMetadataRegistry.getRecentInteractionContext)) builder.tool(getRecentInteractionContextTool)
            if (policy.allows(ToolMetadataRegistry.getUserContextSettings)) builder.tool(getUserContextSettingsTool)
            if (policy.allows(ToolMetadataRegistry.getDeviceStatus)) builder.tool(getDeviceStatusTool)
            if (policy.allows(ToolMetadataRegistry.getWeather)) builder.tool(getWeatherTool)
            if (policy.allows(ToolMetadataRegistry.createLocalReminder)) builder.tool(createLocalReminderTool)
            if (policy.allows(ToolMetadataRegistry.queryHealthData)) builder.tool(queryHealthDataTool)
            if (policy.allows(ToolMetadataRegistry.updateState)) builder.tool(updateStateTool)
        }

        // MCP 注册独立于 systemToolsEnabled,只受 mcpEnabled + scope 控制。
        if (includeMcp && isMcpEnabled() && policy.allowedCategories.contains(ToolCategory.REMOTE_READ)) {
            addRemoteMcpTools(builder, policy, queryHint)
        }
        return builder.build()
    }

    override suspend fun warmMcpTools(policy: ToolPolicy) {
        if (!isMcpEnabledSuspend() || !policy.allowedCategories.contains(ToolCategory.REMOTE_READ)) return
        readReadyMcpServers().forEach { server ->
            runCatching { listMcpToolsCached(server) }
                .onSuccess { specs ->
                    AppLogger.info(
                        LogTags.Llm,
                        "mcp_tools_warmed",
                        "count" to specs.size,
                        "serverId" to server.id,
                    )
                }
                .onFailure { error ->
                    AppLogger.warn(
                        LogTags.Llm,
                        "mcp_tools_warm_failed",
                        "serverId" to server.id,
                        "message" to (error.message ?: error::class.simpleName.orEmpty()),
                    )
                }
        }
    }

    private fun readSystemToolsPref(): Boolean = runCatching {
        runBlocking { appPreferences.systemToolsEnabled.first() }
    }.getOrElse {
        AppLogger.warn(LogTags.Llm, "system_tools_pref_read_failed", "message" to (it.message ?: ""))
        true
    }

    private fun isMcpEnabled(): Boolean = runCatching {
        runBlocking { appPreferences.mcpEnabled.first() }
    }.getOrElse {
        AppLogger.warn(LogTags.Llm, "mcp_pref_read_failed", "message" to (it.message ?: ""))
        true
    }

    private suspend fun isMcpEnabledSuspend(): Boolean =
        runCatching { appPreferences.mcpEnabled.first() }.getOrElse {
            AppLogger.warn(LogTags.Llm, "mcp_pref_read_failed", "message" to (it.message ?: ""))
            true
        }

    private fun addRemoteMcpTools(
        builder: ai.koog.agents.core.tools.ToolRegistryBuilder,
        policy: ToolPolicy,
        queryHint: String?,
    ) {
        // 多 server 模式:遍历所有 enabled=true 且 isReady=true 的 server,分别调 listTools。
        // 任何单个 server 失败不影响其他 server 的工具注册。
        val readyServers = runCatching {
            runBlocking { readReadyMcpServers() }
        }.getOrElse { error ->
            AppLogger.warn(
                LogTags.Llm,
                "mcp_servers_read_failed",
                "message" to (error.message ?: error::class.simpleName.orEmpty()),
            )
            return
        }
        val selectedServers = queryHint?.let { McpServerRouter.select(it, readyServers) } ?: readyServers
        if (selectedServers.isEmpty()) {
            if (queryHint != null) {
                AppLogger.debug(LogTags.Llm, "mcp_tools_route_empty", "queryLength" to queryHint.length)
            }
            return
        }
        if (queryHint != null) {
            AppLogger.info(
                LogTags.Llm,
                "mcp_tools_routed",
                "availableServers" to readyServers.size,
                "selectedServers" to selectedServers.size,
                "queryLength" to queryHint.length,
            )
        }

        var totalCount = 0
        selectedServers.forEach { server ->
            val url = server.resolvedUrl
            val name = server.resolvedName
            val cacheKey = server.cacheKey()
            val cooldownUntil = mcpFailureCooldowns[cacheKey] ?: 0L
            if (cooldownUntil > System.currentTimeMillis()) {
                AppLogger.info(
                    LogTags.Llm,
                    "mcp_tools_register_cooldown_skip",
                    "serverId" to server.id,
                    "remainingMs" to (cooldownUntil - System.currentTimeMillis()),
                )
                return@forEach
            }
            runCatching {
                runBlocking { listMcpToolsCached(server) }
            }.onSuccess { specs ->
                mcpFailureCooldowns.remove(cacheKey)
                val selectionStartedAt = System.nanoTime()
                val selectedSpecs = queryHint?.let { McpToolSelector.select(it, specs) } ?: specs
                var registeredCount = 0
                selectedSpecs.forEach { spec ->
                    if (policy.allows(ToolMetadataRegistry.remoteMcp(spec.name))) {
                        builder.tool(
                            McpRemoteTool(
                                serverUrl = url,
                                serverName = name,
                                spec = spec,
                                client = remoteMcpClient,
                                headers = server.authHeaders,
                            )
                        )
                        registeredCount += 1
                    }
                }
                totalCount += registeredCount
                AppLogger.info(
                    LogTags.Llm,
                    "mcp_tools_registered",
                    "availableCount" to specs.size,
                    "selectedCount" to selectedSpecs.size,
                    "registeredCount" to registeredCount,
                    "selectionDurationMs" to ((System.nanoTime() - selectionStartedAt) / 1_000_000.0),
                    "serverId" to server.id,
                    "serverName" to name,
                )
            }.onFailure { error ->
                mcpFailureCooldowns[cacheKey] = System.currentTimeMillis() + MCP_FAILURE_COOLDOWN_MS
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
                "serverCount" to selectedServers.size,
                "toolCount" to totalCount,
            )
        }
    }

    private suspend fun readReadyMcpServers(): List<McpServerConfig> =
        mcpServerListRepository.readAll().filter { it.enabled && it.isReady }

    private suspend fun listMcpToolsCached(server: McpServerConfig): List<McpToolSpec> {
        val cacheKey = server.cacheKey()
        mcpToolSpecCache[cacheKey]?.let { return it }
        return remoteMcpClient.listTools(server.resolvedUrl, server.authHeaders)
            .also { mcpToolSpecCache[cacheKey] = it }
    }

    private fun McpServerConfig.cacheKey(): String = "$resolvedUrl|${authHeaders.hashCode()}"

    private companion object {
        const val MCP_FAILURE_COOLDOWN_MS = 5L * 60L * 1000L
    }
}
