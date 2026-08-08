package com.xiaoqi.companion.core.tools

import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.ToolResultKind
import ai.koog.agents.core.feature.model.AIAgentError
import ai.koog.serialization.JSONObject
import io.mockk.mockk
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KoogResultKindPatcherTest {

    @Test
    fun isErrorResult_recognizesKoogValidationFailureWithoutEnvelope() {
        val validation = received(
            content = "Invalid tool arguments",
            resultKind = ToolResultKind.ValidationError(
                AIAgentError("missing required argument", "", null)
            ),
        )

        assertTrue(validation.isErrorResult())
    }

    @Test
    fun isErrorResult_acceptsSuccessfulPlainMcpPayload() {
        assertTrue(!received(content = "{\"pois\":[]}").isErrorResult())
    }

    @Test
    fun envelopeError_patchesToFailureWithReasonInMessageAndHintInCause() {
        val env = encode(ToolEnvelopeFactory.disabled(
            reason = "reminder_tool_disabled",
            hint = "用户关闭了提醒功能",
        ))
        val input = listOf(received(content = env, toolName = "create_local_reminder"))

        val patched = input.withErrorResultKind()

        assertEquals(1, patched.size)
        val kind = patched.single().resultKind
        assertTrue("should be Failure but was $kind", kind is ToolResultKind.Failure)
        val err = (kind as ToolResultKind.Failure).error
        assertNotNull(err)
        assertEquals("reminder_tool_disabled", err!!.message)
        assertEquals("用户关闭了提醒功能", err.cause)
    }

    @Test
    fun envelopeOk_keepsSuccess() {
        val env = encode(ToolEnvelopeFactory.ok(JsonObject(emptyMap())))
        val input = listOf(received(content = env, toolName = "search_memory"))

        val patched = input.withErrorResultKind()

        assertTrue(patched.single().resultKind is ToolResultKind.Success)
    }

    @Test
    fun plainJsonText_keepsSuccessSinceNotEnvelopeError() {
        // 旧 tool 的纯 JSON 文本(非 envelope)——保持 Success
        val input = listOf(received(content = """{"count":3,"results":[]}""", toolName = "search_records"))

        val patched = input.withErrorResultKind()

        assertTrue(patched.single().resultKind is ToolResultKind.Success)
    }

    @Test
    fun blankContent_keepsSuccess() {
        val input = listOf(received(content = "", toolName = "x"))

        val patched = input.withErrorResultKind()

        assertTrue(patched.single().resultKind is ToolResultKind.Success)
    }

    @Test
    fun mixedResults_patchOnlyErrorOnes() {
        val errorEnv = encode(ToolEnvelopeFactory.permissionMissing("POST_NOTIFICATIONS", "需要通知权限"))
        val okEnv = encode(ToolEnvelopeFactory.ok(JsonObject(emptyMap())))
        val plain = """{"foo":"bar"}"""

        val input = listOf(
            received(content = errorEnv, toolName = "create_local_reminder"),
            received(content = okEnv, toolName = "search_memory"),
            received(content = plain, toolName = "search_records"),
        )

        val patched = input.withErrorResultKind()

        assertTrue("err 应该是 Failure", patched[0].resultKind is ToolResultKind.Failure)
        assertTrue("ok 应该是 Success", patched[1].resultKind is ToolResultKind.Success)
        assertTrue("plain 应该是 Success", patched[2].resultKind is ToolResultKind.Success)
    }

    private fun received(
        content: String,
        toolName: String = "test_tool",
        resultKind: ToolResultKind = ToolResultKind.Success,
    ): ReceivedToolResult =
        ReceivedToolResult(
            id = "call-1",
            tool = toolName,
            toolArgs = mockk<JSONObject>(relaxed = true),
            toolDescription = null,
            content = content,
            resultKind = resultKind,
            result = null,
        )
}
