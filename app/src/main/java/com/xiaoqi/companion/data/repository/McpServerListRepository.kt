package com.xiaoqi.companion.data.repository

import com.xiaoqi.companion.core.mcp.CustomMcpServerPreset
import com.xiaoqi.companion.core.mcp.McpServerConfig
import com.xiaoqi.companion.core.mcp.McpServerPresets
import com.xiaoqi.companion.core.mcp.McpSettingsSnapshot
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.data.datastore.AppPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * MCP server 列表的持久化 + 迁移。
 *
 * DataStore 里只存一个 JSON 字符串 (`mcp_servers_json`),用 [McpServerConfig] 的
 * kotlinx-serialization 表达。读/写都加 [mutex] 串行化,避免并发写丢更新。
 *
 * 老数据迁移:首次 [readAll] 时如果 list 是空但 4 个老单 server 字段 (providerId/apiKey/name/url)
 * 任一非空,把它们包装成一项 + 写回 list。迁移完后老字段不再被读/写。
 */
@Singleton
class McpServerListRepository @Inject constructor(
    private val appPreferences: AppPreferences,
) {
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(McpServerConfig.serializer())
    private val mutex = Mutex()

    /**
     * 当前 server 列表的 reactive view(只反映 list 字段,不含迁移)。
     * 适合 UI 直接 collect。迁移请用 [readAll] (suspend) 显式触发。
     */
    val observeAll: Flow<List<McpServerConfig>> = appPreferences.mcpServersJson
        .map { parseOrEmpty(it) }
        .distinctUntilChanged()

    /**
     * 一次性读出当前 list + 自动迁移老数据。迁移在 mutex 内执行,首次调用线程安全。
     * 后续调用不会重复迁移 (因为 list 已非空)。
     */
    suspend fun readAll(): List<McpServerConfig> = mutex.withLock {
        val current = parseOrEmpty(appPreferences.mcpServersJson.first())
        if (current.isNotEmpty()) return@withLock current
        return@withLock migrateLegacyLocked()
    }

    suspend fun writeAll(list: List<McpServerConfig>) = mutex.withLock {
        val raw = json.encodeToString(serializer, list)
        appPreferences.setMcpServersJson(raw)
    }

    suspend fun add(server: McpServerConfig): List<McpServerConfig> = mutex.withLock {
        // 迁移一次(如果 list 是空且老数据有)再加新项
        val base = parseOrEmpty(appPreferences.mcpServersJson.first())
            .ifEmpty { migrateLegacyLocked() }
        val updated = base + server
        appPreferences.setMcpServersJson(json.encodeToString(serializer, updated))
        updated
    }

    suspend fun update(server: McpServerConfig): List<McpServerConfig> = mutex.withLock {
        val current = parseOrEmpty(appPreferences.mcpServersJson.first())
        val updated = current.map { if (it.id == server.id) server else it }
        appPreferences.setMcpServersJson(json.encodeToString(serializer, updated))
        updated
    }

    suspend fun remove(id: String): List<McpServerConfig> = mutex.withLock {
        val current = parseOrEmpty(appPreferences.mcpServersJson.first())
        val updated = current.filter { it.id != id }
        appPreferences.setMcpServersJson(json.encodeToString(serializer, updated))
        updated
    }

    suspend fun toggleEnabled(id: String): List<McpServerConfig> = mutex.withLock {
        val current = parseOrEmpty(appPreferences.mcpServersJson.first())
        val updated = current.map { if (it.id == id) it.copy(enabled = !it.enabled) else it }
        appPreferences.setMcpServersJson(json.encodeToString(serializer, updated))
        updated
    }

    /**
     * 把老单 server 字段 (mcpProviderId / mcpApiKey / mcpServerName / mcpHttpUrl) 包装成一项
     * 并写回 list。返回迁移后 list。
     *
     * 必须在 [mutex] 内调用。
     */
    private suspend fun migrateLegacyLocked(): List<McpServerConfig> {
        val providerId = appPreferences.mcpProviderId.first()
        val apiKey = appPreferences.mcpApiKey.first()
        val name = appPreferences.mcpServerName.first()
        val url = appPreferences.mcpHttpUrl.first()
        if (providerId.isBlank() && apiKey.isBlank() && name.isBlank() && url.isBlank()) {
            return emptyList()
        }
        val snapshot = McpSettingsSnapshot.from(providerId, apiKey, name, url)
        val migrated = McpServerConfig(
            displayName = name,
            providerId = snapshot.providerId,
            apiKey = snapshot.apiKey,
            customUrl = if (snapshot.provider is CustomMcpServerPreset) snapshot.url else "",
            enabled = true,
        )
        val list = listOf(migrated)
        appPreferences.setMcpServersJson(json.encodeToString(serializer, list))
        return list
    }

    private fun parseOrEmpty(raw: String): List<McpServerConfig> {
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }
            .onFailure {
                AppLogger.warn(
                    LogTags.Repo,
                    "mcp_servers_parse_failed",
                    "rawLength" to raw.length,
                    "error" to (it.message ?: it::class.simpleName.orEmpty()),
                )
            }
            .getOrDefault(emptyList())
    }

    companion object {
        /** Provider 列表给 UI SegmentedButton 用 — 这里不再 wrap 一层,直接复用 [McpServerPresets.all]。 */
        val providerOptions: List<com.xiaoqi.companion.core.mcp.McpServerPreset> = McpServerPresets.all
    }
}
