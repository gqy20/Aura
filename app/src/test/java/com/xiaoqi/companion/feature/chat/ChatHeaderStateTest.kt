package com.xiaoqi.companion.feature.chat

import com.xiaoqi.companion.core.mcp.McpServerConfig
import com.xiaoqi.companion.core.presence.PresenceMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatHeaderStateTest {

    private val readyServer = McpServerConfig(
        id = "ready-server",
        providerId = "custom",
        customUrl = "https://example.com/mcp",
    )

    @Test
    fun resolveHeaderMcpState_whenMasterDisabled_returnsDisabled() {
        val settings = ChatToolCapabilitySettings(
            mcpEnabled = false,
            mcpServers = listOf(readyServer),
        )

        assertEquals(HeaderMcpState.DISABLED, resolveHeaderMcpState(settings, emptyMap()))
    }

    @Test
    fun resolveHeaderMcpState_whenEnabledServerNeedsConfiguration_returnsNeedsSetup() {
        val settings = ChatToolCapabilitySettings(
            mcpServers = listOf(McpServerConfig(id = "missing-config", providerId = "custom")),
        )

        assertEquals(HeaderMcpState.NEEDS_SETUP, resolveHeaderMcpState(settings, emptyMap()))
    }

    @Test
    fun resolveHeaderMcpState_beforeConnectionCheck_returnsActive() {
        val settings = ChatToolCapabilitySettings(mcpServers = listOf(readyServer))

        assertEquals(HeaderMcpState.ACTIVE, resolveHeaderMcpState(settings, emptyMap()))
    }

    @Test
    fun resolveHeaderMcpState_whenConnectionReturnsNoTools_returnsNeedsSetup() {
        val settings = ChatToolCapabilitySettings(mcpServers = listOf(readyServer))

        assertEquals(
            HeaderMcpState.NEEDS_SETUP,
            resolveHeaderMcpState(settings, mapOf(readyServer.id to emptyList())),
        )
    }

    @Test
    fun resolveHeaderMcpState_whenToolsAreAvailable_returnsReady() {
        val settings = ChatToolCapabilitySettings(mcpServers = listOf(readyServer))

        assertEquals(
            HeaderMcpState.READY,
            resolveHeaderMcpState(settings, mapOf(readyServer.id to listOf("search"))),
        )
    }

    @Test
    fun resolveCompanionHeaderStatus_duringToolCall_showsActualActivity() {
        assertEquals(
            "正在查询地图",
            resolveCompanionHeaderStatus(
                isConfigReady = true,
                isLoading = true,
                latestActivity = "正在查询地图",
                hasError = false,
                presenceMode = PresenceMode.THINKING,
                presenceLabel = "Aura 思考中",
            ),
        )
    }

    @Test
    fun resolveCompanionHeaderStatus_afterFailure_showsActionableState() {
        assertEquals(
            "连接异常",
            resolveCompanionHeaderStatus(
                isConfigReady = true,
                isLoading = false,
                latestActivity = null,
                hasError = true,
                presenceMode = PresenceMode.ERROR,
                presenceLabel = "Aura 恢复中",
            ),
        )
    }
}
