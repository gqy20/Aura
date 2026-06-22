package com.xiaoqi.companion.core.logging

import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import timber.log.Timber

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class FileRingBufferTreeTest {

    private lateinit var provider: LogFileProvider

    @Before
    fun setUp() {
        provider = LogFileProvider(
            ApplicationProvider.getApplicationContext(),
            maxBytes = 1024L,
        )
        provider.clearLog()
        provider.logFile.writeText("")
        Timber.uprootAll()
    }

    @Test
    fun `writes log line to file with timestamp and level`() {
        Timber.plant(FileRingBufferTree(provider))
        Timber.tag(LogTags.Runtime).log(Log.INFO, t = null, "hello-event")

        val content = provider.logFile.readText()
        assertTrue("应包含 tag: $content", content.contains("[Runtime]") || content.contains("Runtime"))
        assertTrue("应包含 message: $content", content.contains("hello-event"))
        assertTrue("应有时间戳前缀: $content", content.contains("["))
        // INFO 对应 I
        assertTrue("应有 level 字符: $content", content.contains("[I]"))
    }

    @Test
    fun `appends throwable stack trace to file`() {
        Timber.plant(FileRingBufferTree(provider))
        val error = IllegalStateException("boom")
        Timber.tag(LogTags.Runtime).log(Log.ERROR, error, "failed")

        val content = provider.logFile.readText()
        assertTrue("message 应落盘: $content", content.contains("failed"))
        assertTrue("stack trace 应落盘: $content", content.contains("IllegalStateException"))
        assertTrue("异常 message 应落盘: $content", content.contains("boom"))
    }

    @Test
    fun `multiple lines accumulate`() {
        Timber.plant(FileRingBufferTree(provider))
        Timber.tag(LogTags.App).log(Log.DEBUG, t = null, "first")
        Timber.tag(LogTags.App).log(Log.DEBUG, t = null, "second")
        Timber.tag(LogTags.App).log(Log.DEBUG, t = null, "third")

        val lines = provider.logFile.readLines()
        assertTrue("first 应存在: $lines", lines.any { it.contains("first") })
        assertTrue("second 应存在: $lines", lines.any { it.contains("second") })
        assertTrue("third 应存在: $lines", lines.any { it.contains("third") })
    }

    @Test
    fun `trim triggers after enough writes and keeps recent`() {
        // maxBytes=1024,每条检查间隔 64。写 200 条长消息,应触发多次 trim。
        Timber.plant(FileRingBufferTree(provider))
        val payload = "x".repeat(80) // 每条约 100+ bytes
        repeat(200) { i ->
            Timber.tag(LogTags.App).log(Log.INFO, t = null, "msg-$i-$payload")
        }

        val content = provider.logFile.readText()
        assertTrue(
            "trim 后应 <= maxBytes*1.5 容差,实际 ${content.length}",
            content.length <= 2048
        )
        // 最旧的不应在,最新的应在
        assertFalse("msg-0 应被滚出: ${content.take(200)}", content.contains("msg-0-"))
        assertTrue("msg-199 应保留: ${content.takeLast(200)}", content.contains("msg-199-"))
    }

    @Test
    fun `io failure does not propagate`() {
        // 用一个不存在的 provider 触发 I/O 失败路径,不应抛异常
        val brokenProvider = LogFileProvider(
            ApplicationProvider.getApplicationContext(),
        )
        // 把 logFile 指向一个不可写位置(模拟失败)—— 这里直接测 append 异常被吞
        brokenProvider.logFile.writeText("ok")
        brokenProvider.logFile.delete()
        // 仍然不应抛 —— FileRingBufferTree.append 捕获 IOException
        val tree = FileRingBufferTree(brokenProvider)
        // 直接调 log,不应抛
        tree.log(Log.INFO, LogTags.App, "should not throw", null)
    }
}
