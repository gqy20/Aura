package com.xiaoqi.companion.core.logging

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import timber.log.Timber

/**
 * 日志体系集成测试。
 *
 * `Timber.Tree.log(priority, tag, message, t)` 是 protected,外部无法直接调用;
 * 这里走真实链路 `AppLogger → Timber.tag(...).log(...) → Tree → Log.println`,
 * 用 Robolectric 的 ShadowLog 捕获最终输出,验证:
 *  1. release 模式过滤掉 < WARN 的日志(隐私/性能核心)
 *  2. debug 模式全量输出
 *  3. tag 正确传递
 *  4. throwable stack trace 被附加
 *  5. PII 经 LogFieldSanitizer 脱敏后进入 logcat(端到端)
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SafeLogTreeTest {

    @Before
    fun resetForest() {
        Timber.uprootAll()
        ShadowLog.reset()
    }

    @Test
    fun `debug mode writes all levels to logcat`() {
        AppLogger.initialize(isDebug = true)

        AppLogger.verbose(LogTags.App, "v_event")
        AppLogger.debug(LogTags.Llm, "d_event")
        AppLogger.info(LogTags.Runtime, "i_event")
        AppLogger.warn(LogTags.Tools, "w_event")
        AppLogger.error(LogTags.Runtime, IllegalStateException("boom"), "e_event")

        val logs = ShadowLog.getLogs()
        val messages = logs.map { it.msg }
        assertTrue("verbose 应写入", messages.any { it.contains("event=v_event") })
        assertTrue("debug 应写入", messages.any { it.contains("event=d_event") })
        assertTrue("info 应写入", messages.any { it.contains("event=i_event") })
        assertTrue("warn 应写入", messages.any { it.contains("event=w_event") })
        assertTrue("error 应写入", messages.any { it.contains("event=e_event") })
    }

    @Test
    fun `release mode drops verbose debug and info`() {
        AppLogger.initialize(isDebug = false)

        AppLogger.verbose(LogTags.App, "v_event")
        AppLogger.debug(LogTags.Llm, "d_event")
        AppLogger.info(LogTags.Runtime, "i_event")
        AppLogger.warn(LogTags.Tools, "w_event")
        AppLogger.error(LogTags.Runtime, IllegalStateException("boom"), "e_event")

        val logs = ShadowLog.getLogs()
        val messages = logs.map { it.msg }

        assertFalse("verbose 必须被过滤", messages.any { it.contains("v_event") })
        assertFalse("debug 必须被过滤", messages.any { it.contains("d_event") })
        assertFalse("info 必须被过滤", messages.any { it.contains("i_event") })
        assertTrue("warn 必须保留", messages.any { it.contains("w_event") })
        assertTrue("error 必须保留", messages.any { it.contains("e_event") })
    }

    @Test
    fun `warn and error carry correct priority`() {
        AppLogger.initialize(isDebug = true)

        AppLogger.warn(LogTags.Tools, "w")
        AppLogger.error(LogTags.Runtime, IllegalStateException("e"), "err")

        val logs = ShadowLog.getLogs()
        val warn = logs.first { it.msg.contains("event=w") }
        val error = logs.first { it.msg.contains("event=err") }
        assertEquals(Log.WARN, warn.type)
        assertEquals(Log.ERROR, error.type)
    }

    @Test
    fun `message includes type and event and fields`() {
        AppLogger.initialize(isDebug = true)

        AppLogger.info(LogTags.Runtime, "pipeline_completed", "durationMs" to 42, "replyLength" to 10)

        val msg = ShadowLog.getLogs().first { it.msg.contains("pipeline_completed") }.msg
        // type=Audit(info → Audit)、event 名、字段 k=v 都要出现
        assertTrue("type 缺失: $msg", msg.contains("type=Audit"))
        assertTrue("event 缺失: $msg", msg.contains("event=pipeline_completed"))
        assertTrue("durationMs 缺失: $msg", msg.contains("durationMs=42"))
        assertTrue("replyLength 缺失: $msg", msg.contains("replyLength=10"))
    }

    @Test
    fun `pii fields are redacted end to end`() {
        AppLogger.initialize(isDebug = true)

        AppLogger.debug(
            LogTags.Llm,
            "request_built",
            "apiKey" to "sk-LEAK-ME-12345",
            "prompt" to "用户的隐私输入",
            "contentLength" to 88,
            "requestHash" to LogFieldSanitizer.hash("session-xyz"),
        )

        val msg = ShadowLog.getLogs().first { it.msg.contains("request_built") }.msg

        assertFalse("apiKey 原文绝不能进 logcat: $msg", msg.contains("sk-LEAK-ME-12345"))
        assertFalse("prompt 原文绝不能进 logcat: $msg", msg.contains("用户的隐私输入"))
        assertTrue("apiKey 应脱敏: $msg", msg.contains("apiKey=redacted:length=16"))
        assertTrue("prompt 应脱敏: $msg", msg.contains("prompt=redacted:length=7"))
        // safe metric 即使含敏感子串也要保留原值
        assertTrue("contentLength 应保留: $msg", msg.contains("contentLength=88"))
    }

    @Test
    fun `error attaches throwable stack trace`() {
        AppLogger.initialize(isDebug = true)

        val error = IllegalStateException("boom-trace")
        AppLogger.error(LogTags.Runtime, error, "pipeline_failed")

        val logs = ShadowLog.getLogs()
        // SafeReleaseTree/SafeDebugTree 写两条:message + stack trace
        val stackTraceLog = logs.firstOrNull {
            it.msg.contains("IllegalStateException") && it.msg.contains("boom-trace")
        }
        assertTrue(
            "throwable stack trace 必须被附加,实际 logs: ${logs.map { it.msg }}",
            stackTraceLog != null
        )
    }

    @Test
    fun `null field values are omitted from message`() {
        AppLogger.initialize(isDebug = true)

        AppLogger.debug(LogTags.App, "null_fields_probe", "present" to 1, "absent" to null)

        val msg = ShadowLog.getLogs().first { it.msg.contains("null_fields_probe") }.msg
        assertTrue("present 应出现: $msg", msg.contains("present=1"))
        assertFalse("null 值不应出现: $msg", msg.contains("absent"))
    }

    @Test
    fun `initialize replaces existing forest`() {
        AppLogger.initialize(isDebug = true)
        AppLogger.initialize(isDebug = true)

        AppLogger.info(LogTags.App, "single")
        // 不应该因为重复 plant 出现重复日志(每次 initialize 都 uprootAll)
        val logs = ShadowLog.getLogs().filter { it.msg.contains("event=single") }
        assertEquals(1, logs.size)
    }

    @Test
    fun `release mode raises minPriority to WARN for short circuit`() {
        // 短路阈值决定 release 包里 verbose/debug/info 在 AppLogger.log 入口直接 return,
        // 不执行 LogFieldSanitizer.sanitize + buildMessage 的无谓开销。
        // 直接断言阈值状态,避免依赖测试执行顺序或 sanitize 副作用。
        AppLogger.initialize(isDebug = false)
        assertEquals(Log.WARN, AppLogger.minPriority())

        AppLogger.initialize(isDebug = true)
        assertEquals(Log.VERBOSE, AppLogger.minPriority())
    }

    @Test
    fun `release mode drops low priority logs at applogger layer`() {
        // 行为层面的补充验证:release 模式下 verbose 不应产生任何 logcat 输出
        AppLogger.initialize(isDebug = false)
        assertEquals(Log.WARN, AppLogger.minPriority())

        AppLogger.verbose(LogTags.App, "no_output_v")
        AppLogger.debug(LogTags.App, "no_output_d")
        AppLogger.info(LogTags.App, "no_output_i")

        val logs = ShadowLog.getLogs()
        assertFalse(logs.any { it.msg.contains("no_output_v") })
        assertFalse(logs.any { it.msg.contains("no_output_d") })
        assertFalse(logs.any { it.msg.contains("no_output_i") })
    }
}
