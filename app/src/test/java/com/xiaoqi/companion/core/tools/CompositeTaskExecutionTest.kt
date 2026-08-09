package com.xiaoqi.companion.core.tools

import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.ToolResultKind
import ai.koog.agents.core.feature.model.AIAgentError
import ai.koog.serialization.JSONObject
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositeTaskExecutionTest {

    @Test
    fun mapNearbyRoute_requiresNearbySearchBeforeRoute() {
        val execution = CompositeTaskPlanner.create(
            "Find coffee near West Lake and give a walking route from Hangzhou East Station"
        )!!

        execution.record(listOf(success("maps_direction_walking")))
        assertFalse(execution.isComplete)
        assertTrue(execution.nextStepInstruction().contains("maps_geo"))

        execution.record(listOf(success("maps_geo", "origin")))
        assertFalse(execution.isComplete)
        execution.record(listOf(success("maps_geo", "area")))
        assertTrue(execution.nextStepInstruction().contains("maps_around_search"))

        execution.record(listOf(success("maps_around_search")))
        assertFalse(execution.isComplete)
        assertTrue(execution.nextStepInstruction().contains("maps_search_detail"))

        execution.record(listOf(success("maps_search_detail")))
        assertFalse(execution.isComplete)
        assertTrue(execution.nextStepInstruction().contains("maps_direction"))

        execution.record(listOf(success("maps_direction_walking")))
        assertTrue(execution.isComplete)
    }

    @Test
    fun mapPlan_reservesRoundsForEveryRequiredEvidence() {
        val execution = CompositeTaskPlanner.create(
            "Find coffee near West Lake and give a walking route from Hangzhou East Station"
        )!!

        assertTrue(execution.requiredToolNameHints.contains("maps_search_detail"))
        assertTrue(execution.minimumToolRounds >= 5)
    }

    @Test
    fun failedOrBlankResult_doesNotCompleteStep() {
        val execution = CompositeTaskPlanner.create(
            "Use Bing web search for latest Android news"
        )!!

        execution.record(listOf(result("bing_search", "", ToolResultKind.Success)))
        execution.record(
            listOf(
                result(
                    "bing_search",
                    "failed",
                    ToolResultKind.ValidationError(AIAgentError("failed", "", null)),
                )
            )
        )

        assertFalse(execution.isComplete)
        assertTrue(execution.nextStepInstruction().contains("bing_search"))
    }

    @Test
    fun bingSearch_requiresSearchThenCrawl() {
        val execution = CompositeTaskPlanner.create(
            "Use Bing web search for latest Android news"
        )!!

        execution.record(listOf(success("mcp__tool__bing_search")))
        assertFalse(execution.isComplete)
        assertTrue(execution.nextStepInstruction().contains("crawl_webpage"))

        execution.record(listOf(success("mcp__tool__crawl_webpage")))
        assertTrue(execution.isComplete)
    }

    @Test
    fun simpleSingleGoalRequest_doesNotCreateCompositePlan() {
        assertNull(CompositeTaskPlanner.create("Find nearby coffee"))
    }

    private fun success(tool: String, value: String = "ok"): ReceivedToolResult =
        result(tool, "{\"status\":\"$value\"}", ToolResultKind.Success)

    private fun result(
        tool: String,
        content: String,
        kind: ToolResultKind,
    ): ReceivedToolResult = ReceivedToolResult(
        id = "call-$tool",
        tool = tool,
        toolArgs = mockk<JSONObject>(relaxed = true),
        toolDescription = null,
        content = content,
        resultKind = kind,
        result = null,
    )
}
