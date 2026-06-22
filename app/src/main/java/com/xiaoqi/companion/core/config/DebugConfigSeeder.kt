package com.xiaoqi.companion.core.config

import com.xiaoqi.companion.BuildConfig
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.core.mcp.McpServerConfig
import com.xiaoqi.companion.data.datastore.AppPreferences
import com.xiaoqi.companion.data.db.converter.LlmProvider
import com.xiaoqi.companion.data.repository.DefaultLlmValues
import com.xiaoqi.companion.data.repository.McpServerListRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import org.json.JSONObject

/**
 * Debug 构建专用：把 `.env -> BuildConfig.ENV_*` 的配置预填进 DataStore，
 * 省去开发/演示时每次在设置页手填 API key / MCP server。
 *
 * 触发：[com.xiaoqi.companion.CompanionApplication.onCreate] 里 `if (BuildConfig.DEBUG)`。
 * Release 构建的 ENV_* 是 defaultConfig 空占位，seed() 实质 no-op，且不会进入该分支。
 *
 * 策略（由 .env 的 ENV_FORCE_SEED 控制）：
 * - true  → 每次启动强制用 .env 覆盖 DataStore
 * - false → 仅当字段为空/默认时填，不破坏运行时手改的值
 */
@Singleton
class DebugConfigSeeder @Inject constructor(
    private val appPreferences: AppPreferences,
    private val mcpServerListRepository: McpServerListRepository,
) {
    suspend fun seed() {
        val force = BuildConfig.ENV_FORCE_SEED
        AppLogger.info(LogTags.Config, "debug_config_seed_started", "force" to force)

        seedLlm(force)
        seedLocalModel(force)
        seedMcpServers(force)

        AppLogger.info(LogTags.Config, "debug_config_seed_done")
    }

    private suspend fun seedLlm(force: Boolean) {
        val provider = runCatching { LlmProvider.valueOf(BuildConfig.ENV_LLM_PROVIDER) }
            .getOrNull() ?: return
        val model = BuildConfig.ENV_LLM_MODEL

        // 构建 per-provider key map，所有非空 key 同时注入
        val keyMap = mapOf(
            LlmProvider.GLM to BuildConfig.ENV_GLM_API_KEY,
            LlmProvider.MODELSCOPE to BuildConfig.ENV_MODELSCOPE_API_KEY,
            LlmProvider.KIMI to BuildConfig.ENV_KIMI_API_KEY,
        ).filterValues { it.isNotBlank() }

        if (keyMap.isNotEmpty()) {
            val existingJson = appPreferences.apiKeysJson.first()
            val existing = runCatching { JSONObject(existingJson) }.getOrNull() ?: JSONObject()
            val shouldSeed = force || existing.length() == 0
            if (shouldSeed) {
                val json = JSONObject()
                keyMap.forEach { (p, k) -> json.put(p.name, k) }
                appPreferences.setApiKeysJson(json.toString())
            }
        }

        if (force || appPreferences.llmProvider.first() == AppPreferences.defaultLlmProvider) {
            appPreferences.setLlmProvider(provider)
        }
        if (model.isNotBlank() && (force || appPreferences.modelName.first() == DefaultLlmValues.GLM_MODEL)) {
            appPreferences.setModelName(model)
        }
    }

    private suspend fun seedLocalModel(force: Boolean) {
        val model = BuildConfig.ENV_LOCAL_QWEN_MODEL
        if (model.isBlank()) return
        if (force || appPreferences.dreamLoopModelName.first().isBlank()) {
            appPreferences.setDreamLoopModelName(model)
        }
    }

    private suspend fun seedMcpServers(force: Boolean) {
        val desired = buildDesiredServers()
        if (desired.isEmpty()) return
        val current = mcpServerListRepository.readAll()
        // 非强制且已有用户配置 → 不动；强制 → 用 .env 覆盖
        if (!force && current.isNotEmpty()) return
        mcpServerListRepository.writeAll(desired)
    }

    /** 组装 .env 里声明的高德 + 自定义 MCP 列表。 */
    private fun buildDesiredServers(): List<McpServerConfig> {
        val list = mutableListOf<McpServerConfig>()

        val amapKey = BuildConfig.ENV_MCP_AMAP_KEY
        if (amapKey.isNotBlank()) {
            list += McpServerConfig(
                displayName = "高德地图",
                providerId = "amap",
                apiKey = amapKey,
                enabled = true,
            )
        }

        data class CustomEntry(val name: String, val url: String, val token: String)
        val customs = listOf(
            CustomEntry(BuildConfig.ENV_MCP_CUSTOM_1_NAME, BuildConfig.ENV_MCP_CUSTOM_1_URL, BuildConfig.ENV_MCP_CUSTOM_1_TOKEN),
            CustomEntry(BuildConfig.ENV_MCP_CUSTOM_2_NAME, BuildConfig.ENV_MCP_CUSTOM_2_URL, BuildConfig.ENV_MCP_CUSTOM_2_TOKEN),
            CustomEntry(BuildConfig.ENV_MCP_CUSTOM_3_NAME, BuildConfig.ENV_MCP_CUSTOM_3_URL, BuildConfig.ENV_MCP_CUSTOM_3_TOKEN),
            CustomEntry(BuildConfig.ENV_MCP_CUSTOM_4_NAME, BuildConfig.ENV_MCP_CUSTOM_4_URL, BuildConfig.ENV_MCP_CUSTOM_4_TOKEN),
            CustomEntry(BuildConfig.ENV_MCP_CUSTOM_5_NAME, BuildConfig.ENV_MCP_CUSTOM_5_URL, BuildConfig.ENV_MCP_CUSTOM_5_TOKEN),
            CustomEntry(BuildConfig.ENV_MCP_CUSTOM_6_NAME, BuildConfig.ENV_MCP_CUSTOM_6_URL, BuildConfig.ENV_MCP_CUSTOM_6_TOKEN),
        )
        customs.forEach { e ->
            if (e.name.isNotBlank() && e.url.isNotBlank()) {
                list += McpServerConfig(
                    displayName = e.name,
                    providerId = "custom",
                    customUrl = e.url,
                    authToken = e.token,
                    enabled = true,
                )
            }
        }
        return list
    }
}
