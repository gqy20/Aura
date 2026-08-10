package com.xiaoqi.companion.feature.chat

import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DebugDiagnosticArchiveWriterTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `archive contains manifest logs latest crash and readme`() {
        val appLog = temp.newFile("app.log").apply {
            writeText("event=request authorization=Bearer live-secret statusCode=401")
        }
        val crashLog = temp.newFile("crash.log").apply {
            writeText("x-api-key: crash-secret\njava.lang.IllegalStateException")
        }
        val destination = File(temp.root, "diagnostics.zip")

        val result = DebugDiagnosticArchiveWriter().write(
            destination = destination,
            appLog = appLog,
            crashLog = crashLog,
            metadata = linkedMapOf("versionName" to "1.2.3", "buildType" to "debug"),
        )

        assertTrue(result.includesAppLog)
        assertTrue(result.includesCrashLog)
        ZipFile(destination).use { zip ->
            assertEquals(
                setOf("README.txt", "manifest.json", "logs/app.log", "crashes/latest.log"),
                zip.entries().asSequence().map { it.name }.toSet(),
            )
            val manifest = zip.readText("manifest.json")
            val log = zip.readText("logs/app.log")
            val crash = zip.readText("crashes/latest.log")
            assertTrue(manifest.contains("\"versionName\": \"1.2.3\""))
            assertFalse(log.contains("live-secret"))
            assertTrue(log.contains("authorization=<redacted>"))
            assertFalse(crash.contains("crash-secret"))
            assertTrue(crash.contains("x-api-key=<redacted>"))
        }
    }

    @Test
    fun `archive omits unavailable optional logs`() {
        val destination = File(temp.root, "diagnostics-empty.zip")

        val result = DebugDiagnosticArchiveWriter().write(
            destination = destination,
            appLog = null,
            crashLog = null,
            metadata = mapOf("buildType" to "debug"),
        )

        assertFalse(result.includesAppLog)
        assertFalse(result.includesCrashLog)
        ZipFile(destination).use { zip ->
            assertEquals(
                setOf("README.txt", "manifest.json"),
                zip.entries().asSequence().map { it.name }.toSet(),
            )
        }
    }

    private fun ZipFile.readText(name: String): String =
        getInputStream(getEntry(name)).bufferedReader().use { it.readText() }
}
