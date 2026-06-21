package com.xiaoqi.companion.core.tools.parser

import com.xiaoqi.companion.core.companion.model.ToolCallStatus
import com.xiaoqi.companion.core.tools.ToolDisplayRegistry
import com.xiaoqi.companion.core.tools.ToolEnvelopeFactory
import com.xiaoqi.companion.core.tools.encode
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolDisplayFormatterTest {

    private val parser = ToolCallResultParser()
    private val registry = ToolDisplayRegistry(parser)

    @Test
    fun formatter_succeededListHits_returnsCountedLabel() {
        val summary = ToolResultSummary.ListHits(title = "记忆搜索", count = 3, items = listOf("a", "b", "c"))

        val label = ToolDisplayFormatter.format(summary, ToolCallStatus.SUCCEEDED)

        assertEquals("已记忆搜索 3 条", label)
    }

    @Test
    fun formatter_succeededSavedOne_returnsSubjectLabel() {
        val summary = ToolResultSummary.SavedOne(title = "保存记忆", subject = "记忆 mem-1")

        val label = ToolDisplayFormatter.format(summary, ToolCallStatus.SUCCEEDED)

        assertEquals("已保存记忆 · 记忆 mem-1", label)
    }

    @Test
    fun formatter_succeededScheduled_returnsTitleAndSubject() {
        val summary = ToolResultSummary.Scheduled(
            title = "创建提醒",
            subject = "Stand up",
            triggerAtMillis = 100L,
            exact = false,
        )

        val label = ToolDisplayFormatter.format(summary, ToolCallStatus.SUCCEEDED)

        assertEquals("已创建提醒 · Stand up", label)
    }

    @Test
    fun formatter_succeededKeyValueReport_returnsStaticTitle() {
        val summary = ToolResultSummary.KeyValueReport(
            title = "设备状态",
            pairs = listOf("电量" to "42%"),
        )

        val label = ToolDisplayFormatter.format(summary, ToolCallStatus.SUCCEEDED)

        assertEquals("已设备状态", label)
    }

    @Test
    fun formatter_succeededEmpty_returnsNoResultLabel() {
        val summary = ToolResultSummary.Empty(title = "记忆搜索")

        val label = ToolDisplayFormatter.format(summary, ToolCallStatus.SUCCEEDED)

        assertEquals("记忆搜索 · 无结果", label)
    }

    @Test
    fun formatter_failedWithFailedSummary_returnsReason() {
        val summary = ToolResultSummary.Failed(
            title = "创建提醒",
            reason = "permission_missing",
            hint = "需要 SCHEDULE_EXACT_ALARM 权限",
        )

        val label = ToolDisplayFormatter.format(summary, ToolCallStatus.FAILED)

        assertEquals("创建提醒失败 · permission_missing", label)
    }

    @Test
    fun formatter_started_returnsNull() {
        val summary = ToolResultSummary.SavedOne(title = "保存", subject = "x")

        assertNull(ToolDisplayFormatter.format(summary, ToolCallStatus.STARTED))
    }

    @Test
    fun formatter_nullSummary_returnsNull() {
        assertNull(ToolDisplayFormatter.format(null, ToolCallStatus.SUCCEEDED))
    }

    @Test
    fun registry_resolveLabel_successWithEnvelope_returnsDynamicLabel() {
        val env = encode(ToolEnvelopeFactory.ok(buildJsonObject {
            put("count", JsonPrimitive(3))
            put("query", JsonPrimitive(""))
            put("results", kotlinx.serialization.json.JsonArray(listOf(
                buildJsonObject { put("content", JsonPrimitive("a")) },
                buildJsonObject { put("content", JsonPrimitive("b")) },
                buildJsonObject { put("content", JsonPrimitive("c")) },
            )))
        }))

        val label = registry.resolveLabel("search_memory", ToolCallStatus.SUCCEEDED, env)

        // 动态:"已记忆搜索 3 条";静态是"已搜索"
        assertEquals("已记忆搜索 3 条", label)
    }

    @Test
    fun registry_resolveLabel_failureWithEnvelope_returnsReason() {
        val env = encode(ToolEnvelopeFactory.permissionMissing(
            permission = "POST_NOTIFICATIONS",
            hint = "需要通知权限才能提醒",
        ))

        val label = registry.resolveLabel("create_local_reminder", ToolCallStatus.FAILED, env)

        assertTrue("got: $label", label.contains("创建提醒失败"))
        assertTrue(label.contains("permission_missing"))
    }

    @Test
    fun registry_resolveLabel_blankJson_fallsBackToStaticLabel() {
        // P0 修复:null resultJson → parser returns Unknown → formatter 现在返回 null
        // 让 resolveLabel 走 staticLabel(toolName) 回退("已搜索"),而不是通用"已完成"。
        // 这样 chip 文案保留工具身份信息,而不是变成与具体工具无关的兜底词。
        val label = registry.resolveLabel("search_memory", ToolCallStatus.SUCCEEDED, null)

        assertEquals("已搜索", label)
    }

    @Test
    fun registry_resolveLabel_started_ignoresResultJson() {
        val label = registry.resolveLabel(
            "search_memory",
            ToolCallStatus.STARTED,
            """{"count":3}""",
        )

        // STARTED 不解析,直接走静态
        assertEquals("搜索中", label)
    }

    @Test
    fun registry_resolveLabel_failureWithOnlyErrorMessage_synthesizesEnvelope() {
        val label = registry.resolveLabel(
            "create_local_reminder",
            ToolCallStatus.FAILED,
            resultJson = null,
            errorMessage = "permission_denied",
        )

        assertTrue("got: $label", label.contains("创建提醒失败"))
        assertTrue(label.contains("permission_denied"))
    }
}
