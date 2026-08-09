package com.xiaoqi.companion.core.mcp

/** 根据本轮用户输入只选择可能相关的 MCP server，避免每轮探测全部远端服务。 */
internal object McpServerRouter {
    fun select(query: String, servers: List<McpServerConfig>): List<McpServerConfig> {
        val normalized = query.lowercase()
        if (normalized.isBlank()) return emptyList()
        if (normalized.contains("mcp__") || normalized.contains("所有mcp") || normalized.contains("all mcp")) {
            return servers
        }

        val needsMap = normalized.containsAny(
            "maps_", "map tool", "map search", "amap", "高德", "地图", "导航", "路线", "坐标", "地理编码",
            "geocode", "direction", "route", "walk", "walking", "driving", "transit", "navigation",
            "nearby", "around search", "poi",
            "附近", "周边",
        )
        val needsRail = normalized.containsAny("12306", "火车", "高铁", "车次", "余票", "railway", "train ticket")
        val needsWeb = normalized.containsAny("bing", "必应", "web search", "网页搜索", "搜索网页", "新闻", "latest news")
        val needsMeal = normalized.containsAny("今天吃什么", "吃什么", "随机菜", "推荐吃")
        val needsMealEnglish = normalized.containsAny(
            "what to eat", "meal recommendation", "recommend food", "recipe", "howtocook",
        )
        val needsMcDonalds = normalized.containsAny("麦当劳", "mcdonald")
        val needsLuckin = normalized.containsAny("瑞幸", "luckin")

        return servers.filter { server ->
            val identity = "${server.providerId} ${server.resolvedName} ${server.resolvedUrl}".lowercase()
            (needsMap && identity.containsAny("amap", "高德", "map")) ||
                (needsRail && identity.containsAny("12306", "铁路")) ||
                (needsWeb && identity.containsAny("bing", "必应")) ||
                ((needsMeal || needsMealEnglish) && identity.containsAny("吃什么", "food", "meal", "howtocook")) ||
                (needsMcDonalds && identity.containsAny("麦当劳", "mcdonald")) ||
                (needsLuckin && identity.containsAny("瑞幸", "luckin"))
        }
    }

    private fun String.containsAny(vararg candidates: String): Boolean = candidates.any(::contains)
}
