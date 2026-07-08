package com.xiaoqi.companion.core.remote

import com.xiaoqi.companion.core.companion.model.AgentEvent
import com.xiaoqi.companion.core.companion.model.ToolCallStatus
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteAgentServiceTest {

    @Test
    fun eventDto_mapsRemoteEventsToAgentEvents() {
        val event = RemoteAgentEventDto(
            type = "tool",
            toolName = "search_memory",
            toolStatus = "succeeded",
            callId = "call-1",
            resultJson = """{"count":1}""",
        ).toAgentEvent()

        val call = (event as AgentEvent.ToolCallUpdated).call
        assertEquals("search_memory", call.name)
        assertEquals(ToolCallStatus.SUCCEEDED, call.status)
        assertEquals("call-1", call.callId)
    }

    @Test
    fun runTurn_postsTurnAndMapsEvents() = runTest {
        val transport = FakeTransport(
            response = """
                [
                  {"type":"remote_status","runId":"run-1","status":"queued"},
                  {"type":"delta","text":"hi"},
                  {"type":"complete","text":"done"}
                ]
            """.trimIndent()
        )
        val events = RemoteAgentService(transport).runTurn(
            RemoteAgentTurnRequest(sessionId = "s1", inputText = "hello")
        ).toList()

        assertEquals("/v1/agent/turn", transport.lastPath)
        assertTrue(transport.lastBody.contains("readOnlyToolsOnly"))
        assertEquals(AgentEvent.RemoteStatus("run-1", "queued"), events[0])
        assertEquals(AgentEvent.Streaming("hi"), events[1])
        assertEquals(AgentEvent.Complete("done"), events[2])
    }

    @Test
    fun callReadOnlyTool_usesReadEndpoint() = runTest {
        val transport = FakeTransport("""{"toolName":"search_records","resultJson":"{\"count\":0}"}""")
        val result = RemoteAgentService(transport).callReadOnlyTool(
            RemoteReadOnlyToolRequest(
                sessionId = "s1",
                toolName = "search_records",
                arguments = buildJsonObject {},
            )
        )

        assertEquals("/v1/tools/read", transport.lastPath)
        assertEquals("search_records", result.toolName)
        assertEquals("""{"count":0}""", result.resultJson)
    }

    @Test
    fun browserWorkerBoundary_allowsOnlyReadActions() {
        assertTrue(BrowserWorkerBoundary.decide(BrowserWorkerCommand(BrowserWorkerAction.READ_TEXT)).allowed)
        assertTrue(BrowserWorkerBoundary.decide(BrowserWorkerCommand(BrowserWorkerAction.SCREENSHOT)).allowed)
        assertFalse(BrowserWorkerBoundary.decide(BrowserWorkerCommand(BrowserWorkerAction.SUBMIT_FORM)).allowed)
    }

    private class FakeTransport(
        private val response: String,
    ) : RemoteAgentTransport {
        var lastPath: String = ""
        var lastBody: String = ""

        override suspend fun postJson(path: String, bodyJson: String): String {
            lastPath = path
            lastBody = bodyJson
            return response
        }
    }
}
