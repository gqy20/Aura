package com.xiaoqi.companion.core.logging

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LogFileProviderTest {

    private lateinit var provider: LogFileProvider

    @Before
    fun setUp() {
        // 用小容量方便触发 trim
        provider = LogFileProvider(
            ApplicationProvider.getApplicationContext(),
            maxBytes = 512L,
        )
        provider.clearLog()
        provider.logFile.writeText("") // 确保起始为空
    }

    @Test
    fun `logFile lives under diagnostics dir`() {
        val path = provider.logFile.absolutePath
        assertTrue("expected diagnostics path, got $path", path.contains("diagnostics"))
        assertTrue("expected app.log, got $path", path.endsWith("app.log"))
    }

    @Test
    fun `trimIfNeeded keeps file under max bytes`() {
        // 写入远超容量的内容
        val big = "x".repeat(2000)
        provider.logFile.writeText(big)

        provider.trimIfNeeded()

        assertTrue(
            "trim 后应 <= maxBytes,实际 ${provider.logFile.length()}",
            provider.logFile.length() <= 512
        )
    }

    @Test
    fun `trim keeps most recent content not oldest`() {
        provider.logFile.writeText("OLD_HEADER\n")
        provider.logFile.appendText("line1\n")
        provider.logFile.appendText("line2\n")
        // 总长度 > 512
        repeat(50) { provider.logFile.appendText("padding-$it\n") }
        // 真正的最新内容放在最后
        provider.logFile.appendText("RECENT_TAIL\n")

        provider.trimIfNeeded()

        val content = provider.logFile.readText()
        assertFalse("最旧内容应被滚出: $content", content.contains("OLD_HEADER"))
        assertTrue("最新内容应保留: $content", content.contains("RECENT_TAIL"))
    }

    @Test
    fun `trim does not truncate mid-line`() {
        // 写入若干完整行,使 trim 后首行是完整的
        val line = "each-line-has-fixed-length-xxxxxxxxxxxxxx\n"
        provider.logFile.writeText(line.repeat(100))

        provider.trimIfNeeded()

        val content = provider.logFile.readText()
        assertTrue("trim 后首行不应是半行: [$content]", content.startsWith("each-line") || content.isEmpty())
    }

    @Test
    fun `trimIfNeeded is no-op when under capacity`() {
        provider.logFile.writeText("small")
        provider.trimIfNeeded()
        assertEquals("small", provider.logFile.readText())
    }

    @Test
    fun `snapshotForCrash copies ring buffer and appends stack trace`() {
        provider.logFile.writeText("recent log line\n")

        val dump = provider.snapshotForCrash(
            timestamp = 1_700_000_000_000L,
            stackTrace = "Thread: main\njava.lang.IllegalStateException: boom\n\tat Foo.bar",
        )

        assertTrue(dump.exists())
        val content = dump.readText()
        assertTrue("应包含 ring buffer 内容: $content", content.contains("recent log line"))
        assertTrue("应包含 CRASH 分隔: $content", content.contains("CRASH"))
        assertTrue("应包含 stack trace: $content", content.contains("IllegalStateException"))
        assertTrue("文件名含 crash 前缀: ${dump.name}", dump.name.startsWith("crash-"))
        assertTrue("文件名以 .log 结尾: ${dump.name}", dump.name.endsWith(".log"))
    }

    @Test
    fun `crashDumps returns files newest first`() {
        provider.snapshotForCrash(1_700_000_000_000L, "first")
        Thread.sleep(1100) // 确保时间戳不同
        provider.snapshotForCrash(1_700_000_001_000L, "second")

        val dumps = provider.crashDumps()
        assertEquals(2, dumps.size)
        // newest first
        assertTrue(dumps[0].lastModified() >= dumps[1].lastModified())
    }

    @Test
    fun `crashDumps is empty when no crashes`() {
        assertEquals(emptyList<Any>(), provider.crashDumps())
    }

    @Test
    fun `clearLog empties ring buffer but keeps crashes`() {
        provider.logFile.writeText("data")
        provider.snapshotForCrash(1_700_000_000_000L, "trace")

        provider.clearLog()

        assertEquals("", provider.logFile.readText())
        assertEquals(1, provider.crashDumps().size)
    }
}
