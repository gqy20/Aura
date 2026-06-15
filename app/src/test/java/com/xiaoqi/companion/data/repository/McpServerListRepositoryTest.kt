package com.xiaoqi.companion.data.repository

import app.cash.turbine.test
import com.xiaoqi.companion.core.mcp.McpServerConfig
import com.xiaoqi.companion.data.datastore.AppPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpServerListRepositoryTest {

    private val mcpServersJsonFlow = MutableStateFlow("[]")
    private val mcpProviderIdFlow = MutableStateFlow("")
    private val mcpApiKeyFlow = MutableStateFlow("")
    private val mcpServerNameFlow = MutableStateFlow("")
    private val mcpHttpUrlFlow = MutableStateFlow("")

    private val appPreferences: AppPreferences = mockk(relaxed = true) {
        every { this@mockk.mcpServersJson } returns mcpServersJsonFlow
        every { this@mockk.mcpProviderId } returns mcpProviderIdFlow
        every { this@mockk.mcpApiKey } returns mcpApiKeyFlow
        every { this@mockk.mcpServerName } returns mcpServerNameFlow
        every { this@mockk.mcpHttpUrl } returns mcpHttpUrlFlow
        coEvery { setMcpServersJson(any()) } coAnswers {
            mcpServersJsonFlow.value = firstArg()
        }
    }

    private val repo = McpServerListRepository(appPreferences)

    @Test
    fun `readAll returns empty list when nothing stored`() = runTest {
        assertEquals(emptyList<McpServerConfig>(), repo.readAll())
    }

    @Test
    fun `add appends a server and persists JSON`() = runTest {
        val server = McpServerConfig(displayName = "Amap #1", providerId = "amap", apiKey = "k1")
        val result = repo.add(server)
        assertEquals(1, result.size)
        assertEquals("k1", result[0].apiKey)
        assertTrue("persisted json contains the id", mcpServersJsonFlow.value.contains("\"id\""))
    }

    @Test
    fun `update replaces server with same id`() = runTest {
        mcpServersJsonFlow.value =
            """[{"id":"id-1","apiKey":"old"},{"id":"id-2","apiKey":"other"}]"""
        val updated = repo.update(McpServerConfig(id = "id-1", apiKey = "new"))
        assertEquals(2, updated.size)
        assertEquals("new", updated.first { it.id == "id-1" }.apiKey)
        assertEquals("other", updated.first { it.id == "id-2" }.apiKey)
    }

    @Test
    fun `remove deletes server with matching id`() = runTest {
        mcpServersJsonFlow.value = """[{"id":"id-1"},{"id":"id-2"}]"""
        val result = repo.remove("id-1")
        assertEquals(1, result.size)
        assertEquals("id-2", result[0].id)
    }

    @Test
    fun `toggleEnabled flips the flag for matching id`() = runTest {
        mcpServersJsonFlow.value =
            """[{"id":"id-1","enabled":true},{"id":"id-2","enabled":false}]"""
        val result = repo.toggleEnabled("id-1")
        assertFalse(result.first { it.id == "id-1" }.enabled)
        assertFalse(result.first { it.id == "id-2" }.enabled)
    }

    @Test
    fun `readAll migrates legacy amap url into one server`() = runTest {
        mcpServerNameFlow.value = "OldAmap"
        mcpHttpUrlFlow.value = "https://mcp.amap.com/mcp?key=abc123"
        val result = repo.readAll()
        assertEquals(1, result.size)
        assertEquals("amap", result[0].providerId)
        assertEquals("abc123", result[0].apiKey)
        assertEquals("OldAmap", result[0].displayName)
    }

    @Test
    fun `readAll migrates legacy custom url into one server`() = runTest {
        mcpHttpUrlFlow.value = "https://my-mcp.example.com/sse"
        val result = repo.readAll()
        assertEquals(1, result.size)
        assertEquals("custom", result[0].providerId)
        assertEquals("https://my-mcp.example.com/sse", result[0].customUrl)
    }

    @Test
    fun `readAll does not re-migrate when list already has items`() = runTest {
        mcpServersJsonFlow.value = """[{"id":"existing","apiKey":"k"}]"""
        mcpHttpUrlFlow.value = "https://mcp.amap.com/mcp?key=abc"
        val result = repo.readAll()
        // 不应触发迁移 — 老 url 直接忽略
        assertEquals(1, result.size)
        assertEquals("existing", result[0].id)
        coVerify(exactly = 0) { appPreferences.setMcpServersJson(any()) }
    }

    @Test
    fun `observeAll emits when underlying json changes`() = runTest {
        repo.observeAll.test {
            assertEquals(emptyList<McpServerConfig>(), awaitItem())
            mcpServersJsonFlow.value = """[{"id":"x","apiKey":"k"}]"""
            val next = awaitItem()
            assertEquals(1, next.size)
            assertEquals("x", next[0].id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `parseOrEmpty tolerates malformed JSON`() = runTest {
        mcpServersJsonFlow.value = "not valid json {"
        val result = repo.readAll()
        assertEquals(emptyList<McpServerConfig>(), result)
    }
}
