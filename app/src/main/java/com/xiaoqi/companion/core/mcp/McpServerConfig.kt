package com.xiaoqi.companion.core.mcp

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 一条 MCP server 配置 — 在 DataStore 里以 JSON 列表形式持久化。
 *
 * @param id         稳定 UUID,作为列表项 key + 写入 DataStore 的主键
 * @param displayName 用户友好的展示名(可空 → 用 preset.displayName)
 * @param providerId "amap" / "custom"
 * @param apiKey     amap preset 用
 * @param customUrl  custom preset 用
 * @param enabled    软开关。false 时 [com.xiaoqi.companion.core.tools.CompanionToolRegistry] 不会注册它的工具
 */
@Serializable
data class McpServerConfig(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String = "",
    val providerId: String = "amap",
    val apiKey: String = "",
    val customUrl: String = "",
    val enabled: Boolean = true,
) {
    /** 派生 final url — 用 provider 模板拼。custom preset 直接用 customUrl。 */
    val resolvedUrl: String
        get() = McpServerPresets.byId(providerId).resolveUrl(apiKey, customUrl)

    /** 派生 final name — displayName 为空时 fallback 到 preset.displayName。 */
    val resolvedName: String
        get() = displayName.ifBlank { McpServerPresets.byId(providerId).displayName }

    /** 是否已就绪 (有可用的 resolvedUrl)。 */
    val isReady: Boolean
        get() = resolvedUrl.isNotBlank()

    val provider: McpServerPreset
        get() = McpServerPresets.byId(providerId)
}

/** 空列表的 JSON 表示(避免在 DataStore 里写入时多一个 null check)。 */
val EmptyMcpServerListJson: String = "[]"
