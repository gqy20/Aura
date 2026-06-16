package com.xiaoqi.companion.core.mcp

/**
 * 一次性把 DataStore 里的 4 个 MCP 字段 (providerId / apiKey / name / url) 聚合成一个
 * 不可变 snapshot,顺便做老数据迁移。上层 (ViewModel / UseCase) 直接消费这个对象。
 *
 * 老数据迁移规则:
 * - 如果 [rawProviderId] 为空但 [rawUrl] 非空,根据 url 形状反推 [McpServerPreset] 并抠出 key
 *   (参见 [McpServerPresets.detectFromUrl] / [McpServerPresets.extractApiKey])
 * - 显式 providerId 永远优先 — 用户主动切到 custom 即便 url 是 amap 形状也按 custom 处理
 * - [name] 为空时 fallback 到 preset 的 displayName (老版本允许 mcpServerName 为空)
 *
 * 不做副作用 (不写回 DataStore),只读派生。写回由 [McpServerPreset.resolveUrl] 决定的
 * "final url" 应在 saveMcpSettings 阶段一次性写入,这样能避免"派生值被独立修改"的不一致。
 */
data class McpSettingsSnapshot(
    val providerId: String,
    val provider: McpServerPreset,
    val apiKey: String,
    val name: String,
    val url: String,
) {
    companion object {
        fun from(
            rawProviderId: String?,
            rawApiKey: String?,
            rawName: String?,
            rawUrl: String?,
        ): McpSettingsSnapshot {
            val url = rawUrl.orEmpty().trim()
            val provider: McpServerPreset
            val apiKey: String
            when {
                // 老数据迁移: providerId 未设置 + url 非空 → 按 url 形状反推
                rawProviderId.isNullOrBlank() && url.isNotBlank() -> {
                    provider = McpServerPresets.detectFromUrl(url)
                    apiKey = McpServerPresets.extractApiKey(provider, url).orEmpty()
                }
                // 新用户: 啥都没填 → 默认 Amap,让首屏直接展示 key 输入框
                rawProviderId.isNullOrBlank() -> {
                    provider = McpServerPresets.Amap
                    apiKey = rawApiKey.orEmpty()
                }
                // 显式选过 provider → 信任用户选择
                else -> {
                    provider = McpServerPresets.byId(rawProviderId)
                    apiKey = rawApiKey.orEmpty()
                }
            }
            return McpSettingsSnapshot(
                providerId = provider.id,
                provider = provider,
                apiKey = apiKey,
                name = provider.resolveName(rawName.orEmpty()),
                url = provider.resolveUrl(apiKey, url),
            )
        }
    }
}
