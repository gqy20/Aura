package com.xiaoqi.companion.core.tools

import com.xiaoqi.companion.core.companion.EmotionStateMachine
import com.xiaoqi.companion.core.companion.RelationshipModel
import com.xiaoqi.companion.core.context.ContextPermissionReader
import com.xiaoqi.companion.core.context.CurrentLocationProvider
import com.xiaoqi.companion.core.context.DeviceStatusProvider
import com.xiaoqi.companion.core.mcp.McpToolSpec
import com.xiaoqi.companion.core.mcp.McpServerConfig
import com.xiaoqi.companion.core.mcp.RemoteMcpClient
import com.xiaoqi.companion.core.weather.WeatherProvider
import com.xiaoqi.companion.data.datastore.AppPreferences
import com.xiaoqi.companion.data.db.dao.HealthSnapshotDao
import com.xiaoqi.companion.data.db.dao.MemorySummaryDao
import com.xiaoqi.companion.data.db.dao.MessageDao
import com.xiaoqi.companion.data.db.dao.MessageSearchDao
import com.xiaoqi.companion.data.repository.MemoryRepository
import com.xiaoqi.companion.data.repository.McpServerListRepository
import com.xiaoqi.companion.data.repository.ReminderRepository
import com.xiaoqi.companion.data.source.HealthConnectDataSource
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionToolRegistryTest {

    // 真实工具实例(各自依赖用 relaxed mock 填充),保证 11 个工具名唯一,
    // 避开 Koog ToolRegistry.builder() 的重名检查。
    private val searchMemoryTool = SearchMemoryTool(mockk(relaxed = true))
    private val searchRecordsTool = SearchRecordsTool(
        messageDao = mockk(relaxed = true),
        messageSearchDao = mockk(relaxed = true),
        appPreferences = mockk(relaxed = true),
    )
    private val searchSummariesTool = SearchSummariesTool(mockk(relaxed = true))
    private val getCurrentTimeTool = GetCurrentTimeTool()
    private val getRecentInteractionContextTool = GetRecentInteractionContextTool(
        messageDao = mockk(relaxed = true),
        appPreferences = mockk(relaxed = true),
    )
    private val getUserContextSettingsTool = GetUserContextSettingsTool(
        appPreferences = mockk(relaxed = true),
        permissionReader = mockk(relaxed = true),
    )
    private val getDeviceStatusTool = GetDeviceStatusTool(
        appPreferences = mockk(relaxed = true),
        deviceStatusProvider = mockk(relaxed = true),
    )
    private val getWeatherTool = GetWeatherTool(
        appPreferences = mockk(relaxed = true),
        weatherProvider = mockk(relaxed = true),
        locationProvider = mockk(relaxed = true),
    )
    private val createLocalReminderTool = CreateLocalReminderTool(
        appPreferences = mockk(relaxed = true),
        permissionReader = mockk(relaxed = true),
        reminderRepository = mockk(relaxed = true),
    )
    private val queryHealthDataTool = QueryHealthDataTool(
        healthSnapshotDao = mockk(relaxed = true),
        healthConnectDataSource = mockk(relaxed = true),
    )
    private val updateStateTool = UpdateStateTool(
        emotionMachine = mockk(relaxed = true),
        relationshipModel = mockk(relaxed = true),
        memoryRepository = mockk(relaxed = true),
    )

    private val readyMcpServer = McpServerConfig(
        id = "amap-1",
        displayName = "高德",
        providerId = "amap",
        apiKey = "k",
        enabled = true,
    )

    private fun newRegistry(
        appPreferences: AppPreferences,
        mcpServerListRepository: McpServerListRepository,
        remoteMcpClient: RemoteMcpClient,
    ): CompanionToolRegistry = CompanionToolRegistry(
        searchMemoryTool = searchMemoryTool,
        searchRecordsTool = searchRecordsTool,
        searchSummariesTool = searchSummariesTool,
        getCurrentTimeTool = getCurrentTimeTool,
        getRecentInteractionContextTool = getRecentInteractionContextTool,
        getUserContextSettingsTool = getUserContextSettingsTool,
        getDeviceStatusTool = getDeviceStatusTool,
        getWeatherTool = getWeatherTool,
        createLocalReminderTool = createLocalReminderTool,
        queryHealthDataTool = queryHealthDataTool,
        updateStateTool = updateStateTool,
        remoteMcpClient = remoteMcpClient,
        mcpServerListRepository = mcpServerListRepository,
        appPreferences = appPreferences,
    )

    private fun appPrefs(system: Boolean, mcp: Boolean): AppPreferences = mockk {
        every { systemToolsEnabled } returns flowOf(system)
        every { mcpEnabled } returns flowOf(mcp)
    }

    @Test
    fun create_systemOn_mcpOn_loadsBuiltinAndMcpTools() = runTest {
        val repo = mockk<McpServerListRepository> {
            coEvery { readAll() } returns listOf(readyMcpServer)
        }
        val client = mockk<RemoteMcpClient> {
            coEvery { listTools(any(), any()) } returns listOf(
                McpToolSpec("amap_search", "search", buildJsonObject {}),
            )
        }
        val registry = newRegistry(appPrefs(system = true, mcp = true), repo, client)

        val tools = registry.create(ToolScope.ALL)

        // 11 内置 + 1 MCP = 12
        assertEquals(12, tools.tools.size)
        assertTrue(tools.tools.any { it.name == "search_memory" })
        // MCP 工具名形如 mcp__<serverSlug>__amap_search
        assertTrue(tools.tools.any { it.name.endsWith("__amap_search") })
    }

    @Test
    fun create_systemOn_mcpOff_loadsOnlyBuiltinTools() = runTest {
        val repo = mockk<McpServerListRepository> {
            coEvery { readAll() } returns listOf(readyMcpServer)
        }
        val client = mockk<RemoteMcpClient>(relaxed = true)
        val registry = newRegistry(appPrefs(system = true, mcp = false), repo, client)

        val tools = registry.create(ToolScope.ALL)

        assertEquals(11, tools.tools.size)
        assertTrue(tools.tools.none { it.name.endsWith("__amap_search") })
    }

    @Test
    fun create_systemOff_loadsNoBuiltinAndNoMcpTools() = runTest {
        // systemToolsEnabled=false 时系统工具不注册;MCP 独立判断 mcpEnabled
        val repo = mockk<McpServerListRepository>(relaxed = true)
        val client = mockk<RemoteMcpClient>(relaxed = true)
        val registry = newRegistry(appPrefs(system = false, mcp = true), repo, client)

        val tools = registry.create(ToolScope.ALL)

        // 系统工具关 + 无 ready MCP server(relaxed mock 返回空列表) = 0
        assertEquals(0, tools.tools.size)
    }

    @Test
    fun create_systemOff_mcpOff_loadsNothing() = runTest {
        val repo = mockk<McpServerListRepository>(relaxed = true)
        val client = mockk<RemoteMcpClient>(relaxed = true)
        val registry = newRegistry(appPrefs(system = false, mcp = false), repo, client)

        val tools = registry.create(ToolScope.ALL)

        assertEquals(0, tools.tools.size)
    }

    @Test
    fun create_systemOnly_loadsBuiltinWithoutMcp() = runTest {
        val repo = mockk<McpServerListRepository> {
            coEvery { readAll() } returns listOf(readyMcpServer)
        }
        val client = mockk<RemoteMcpClient>(relaxed = true)
        val registry = newRegistry(appPrefs(system = true, mcp = true), repo, client)

        val tools = registry.create(ToolScope.SYSTEM_ONLY)

        assertEquals(11, tools.tools.size)
        assertTrue(tools.tools.none { it.name.endsWith("__amap_search") })
    }

    @Test
    fun create_mcpOnly_loadsOnlyMcpTools() = runTest {
        val repo = mockk<McpServerListRepository> {
            coEvery { readAll() } returns listOf(readyMcpServer)
        }
        val client = mockk<RemoteMcpClient> {
            coEvery { listTools(any(), any()) } returns listOf(
                McpToolSpec("amap_search", "search", buildJsonObject {}),
            )
        }
        val registry = newRegistry(appPrefs(system = true, mcp = true), repo, client)

        val tools = registry.create(ToolScope.MCP_ONLY)

        // MCP_ONLY: 不注册系统工具,只注册 MCP
        assertEquals(1, tools.tools.size)
        assertTrue(tools.tools.none { it.name == "search_memory" })
        assertTrue(tools.tools.any { it.name.endsWith("__amap_search") })
    }

    @Test
    fun create_mcpDecoupledFromSystemTools_systemOff_mcpOn_stillLoadsMcp() = runTest {
        // MCP 注册不应依赖 systemToolsEnabled
        val repo = mockk<McpServerListRepository> {
            coEvery { readAll() } returns listOf(readyMcpServer)
        }
        val client = mockk<RemoteMcpClient> {
            coEvery { listTools(any(), any()) } returns listOf(
                McpToolSpec("amap_search", "search", buildJsonObject {}),
            )
        }
        val registry = newRegistry(appPrefs(system = false, mcp = true), repo, client)

        val tools = registry.create(ToolScope.ALL)

        // systemToolsEnabled=false 但 MCP 独立生效
        assertEquals(1, tools.tools.size)
        assertTrue(tools.tools.any { it.name.endsWith("__amap_search") })
    }
}
