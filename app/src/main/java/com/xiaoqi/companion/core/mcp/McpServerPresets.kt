package com.xiaoqi.companion.core.mcp

/**
 * 描述一个可接入的 MCP 服务商。让"用户填 key"和"自定义 URL"两种模式共用同一份 UI。
 *
 * - [TemplatedMcpServerPreset]: 模板型。`urlTemplate` 里有 `{key}` 占位符,系统从 key 自动拼 URL。
 * - [CustomMcpServerPreset]: 自定义型。用户填任意 URL,适合高级用户接入第三方 MCP server。
 *
 * 新增一个服务商时只需往 [McpServerPresets.all] 里加一项,UI / 迁移逻辑无需改动。
 */
sealed class McpServerPreset {
    abstract val id: String
    abstract val displayName: String
    abstract val description: String

    /**
     * 拼出实际接入的 URL。
     * - 模板型: 把 [apiKey] 替换进 `urlTemplate` 的 `{key}` 占位符
     * - 自定义型: 直接返回 [customUrl] (已 trim)
     */
    abstract fun resolveUrl(apiKey: String, customUrl: String): String

    /** 解析展示名。用户没填就用 [displayName]。 */
    fun resolveName(customName: String): String = customName.ifBlank { displayName }
}

/**
 * 模板型 preset — 用户只需提供 API Key,URL 由系统按 [urlTemplate] 拼出。
 *
 * @param urlTemplate 必须包含且只包含一个 `{key}` 占位符,会被替换为 trim 后的 apiKey
 * @param keyHint  UI 上 key 输入框的 label (例如"高德 API Key")
 * @param keyPlaceholder 输入框的 placeholder (含"去哪儿申请"提示)
 */
data class TemplatedMcpServerPreset(
    override val id: String,
    override val displayName: String,
    override val description: String,
    val urlTemplate: String,
    val keyHint: String,
    val keyPlaceholder: String,
) : McpServerPreset() {
    /**
     * 空 key 返回空 URL — 表达"未配置"。这样上层 (Snapshot / UI) 可以用 `url.isBlank()`
     * 一致地判定 templated preset 是否已就绪,不用额外分支处理"有 preset 但没 key"的状态。
     */
    override fun resolveUrl(apiKey: String, customUrl: String): String {
        val trimmed = apiKey.trim()
        return if (trimmed.isEmpty()) "" else urlTemplate.replace("{key}", trimmed)
    }
}

/** 自定义型 — 用户填什么 URL 就用什么,适合接任意 streamable HTTP MCP 端点。 */
data class CustomMcpServerPreset(
    override val id: String = "custom",
    override val displayName: String = "自定义 URL",
    override val description: String = "任意 streamable HTTP MCP 端点",
) : McpServerPreset() {
    override fun resolveUrl(apiKey: String, customUrl: String): String = customUrl.trim()
}

/**
 * 内置服务商注册表。增加 provider 时:
 * 1. 在这里加一个 [TemplatedMcpServerPreset] (或 [CustomMcpServerPreset])
 * 2. UI 段 [McpServerPresets.all] 会自动出现在选择器里
 * 3. 老数据迁移 (URL → preset 反推) 自动生效
 */
object McpServerPresets {
    val Amap: TemplatedMcpServerPreset = TemplatedMcpServerPreset(
        id = "amap",
        displayName = "高德地图",
        description = "POI 周边搜索 · 路径规划 · 天气 · 地理编码 · IP 定位 · 15 个工具",
        urlTemplate = "https://mcp.amap.com/mcp?key={key}",
        keyHint = "高德 API Key",
        keyPlaceholder = "在 lbs.amap.com 申请 · 32 位",
    )
    val Custom: CustomMcpServerPreset = CustomMcpServerPreset()

    val all: List<McpServerPreset> = listOf(Amap, Custom)

    /** id → preset;未知 id / null 一律 fallback 到 Custom。 */
    fun byId(id: String?): McpServerPreset =
        all.firstOrNull { it.id == id } ?: Custom

    /**
     * 从已保存的 URL 反推它属于哪个 preset。匹配策略:取 `urlTemplate` 中 `{key}` 之前的前缀。
     * 用于老数据迁移:旧版本只存了 `mcpHttpUrl`,没有 `mcpProviderId` 时调用。
     */
    fun detectFromUrl(url: String?): McpServerPreset {
        if (url.isNullOrBlank()) return Custom
        return all.firstOrNull { p ->
            p is TemplatedMcpServerPreset &&
                url.startsWith(p.urlTemplate.substringBefore("{key}"))
        } ?: Custom
    }

    /**
     * 从属于 templated preset 的完整 URL 抠出 key。custom URL 或模板不匹配返回 null。
     * 老数据迁移的配套函数 — 把 `https://mcp.amap.com/mcp?key=abc` 还原成 `abc`。
     *
     * 用 `&` / `#` 切尾,所以 `?key=abc&extra=foo` 也能正确抠出 `abc`。
     */
    fun extractApiKey(preset: McpServerPreset, fullUrl: String?): String? {
        if (preset !is TemplatedMcpServerPreset) return null
        if (fullUrl.isNullOrBlank()) return null
        val prefix = preset.urlTemplate.substringBefore("{key}")
        if (!fullUrl.startsWith(prefix)) return null
        val after = fullUrl.removePrefix(prefix)
        return after
            .substringBefore('&')
            .substringBefore('#')
            .takeIf { it.isNotBlank() }
    }
}
