package com.xiaoqi.companion.core.logging

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CrashLoggerTest {

    private lateinit var provider: LogFileProvider
    private var previousHandler: Thread.UncaughtExceptionHandler? = null
    private var previousCalled = false

    @Before
    fun setUp() {
        provider = LogFileProvider(ApplicationProvider.getApplicationContext())
        provider.clearLog()
        provider.logFile.writeText("")
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        previousCalled = false
    }

    @After
    fun tearDown() {
        // 恢复测试前的 handler,避免污染其他测试
        Thread.setDefaultUncaughtExceptionHandler(previousHandler)
    }

    @Test
    fun `crash generates dump file and calls previous handler`() {
        val fakePrevious = Thread.UncaughtExceptionHandler { _, _ -> previousCalled = true }
        Thread.setDefaultUncaughtExceptionHandler(fakePrevious)

        val crashLogger = CrashLogger.install(provider)
        val error = IllegalStateException("crash-boom")
        crashLogger.uncaughtException(Thread.currentThread(), error)

        val dumps = provider.crashDumps()
        assertEquals("应生成 1 个 dump", 1, dumps.size)
        val content = dumps[0].readText()
        assertTrue("应含 stack trace: $content", content.contains("IllegalStateException"))
        assertTrue("应含 crash message: $content", content.contains("crash-boom"))
        assertTrue("应含 CRASH 分隔: $content", content.contains("CRASH"))
        assertTrue("previous handler 必须被链式调用", previousCalled)
    }

    @Test
    fun `crash dump includes ring buffer content from before crash`() {
        provider.logFile.writeText("log-before-crash-line\n")

        Thread.setDefaultUncaughtExceptionHandler(null)
        val crashLogger = CrashLogger.install(provider)
        crashLogger.uncaughtException(Thread.currentThread(), RuntimeException("oops"))

        val content = provider.crashDumps()[0].readText()
        assertTrue(
            "crash 前的日志应出现在 dump 里: $content",
            content.contains("log-before-crash-line")
        )
    }

    @Test
    fun `crash handler captures thread name`() {
        Thread.setDefaultUncaughtExceptionHandler(null)
        val crashLogger = CrashLogger.install(provider)

        val namedThread = Thread({ }, "test-crash-thread")
        crashLogger.uncaughtException(namedThread, RuntimeException("x"))

        val content = provider.crashDumps()[0].readText()
        assertTrue("应记录线程名: $content", content.contains("test-crash-thread"))
    }

    @Test
    fun `install returns handler and sets default`() {
        val before = Thread.getDefaultUncaughtExceptionHandler()
        val handler = CrashLogger.install(provider)
        val after = Thread.getDefaultUncaughtExceptionHandler()

        assertEquals(handler, after)
        assertTrue("install 应改变 default handler", before !== after)
    }
}
