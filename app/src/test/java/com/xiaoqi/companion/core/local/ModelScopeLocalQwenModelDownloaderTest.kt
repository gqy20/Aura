package com.xiaoqi.companion.core.local

import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ModelScopeLocalQwenModelDownloaderTest {

    private lateinit var context: android.content.Context
    private lateinit var modelsDir: File
    private lateinit var fakeInterceptor: FakeDownloadInterceptor
    private lateinit var downloader: ModelScopeLocalQwenModelDownloader

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        modelsDir = File(context.filesDir, "models")
        modelsDir.deleteRecursively()
        fakeInterceptor = FakeDownloadInterceptor()
        val client = OkHttpClient.Builder()
            .addInterceptor(fakeInterceptor)
            .build()
        downloader = ModelScopeLocalQwenModelDownloader(context, client)
    }

    @Test
    fun findAnyInstalledModel_returnsNullWhenEmpty() {
        assertNull(downloader.findAnyInstalledModel())
    }

    @Test
    fun findAnyInstalledModel_returnsNullForZeroLengthFile() {
        val dir = File(modelsDir, "Qwen3.5-0.8B-MNN")
        dir.mkdirs()
        LocalQwenModelCatalog.requiredFiles.forEach { name ->
            File(dir, name).createNewFile() // 0 bytes
        }
        assertNull(downloader.findAnyInstalledModel())
    }

    @Test
    fun findAnyInstalledModel_returnsModelWhenAllFilesPresent() {
        val dir = File(modelsDir, "Qwen3.5-0.8B-MNN")
        dir.mkdirs()
        LocalQwenModelCatalog.requiredFiles.forEach { name ->
            File(dir, name).writeText("data-$name")
        }
        assertEquals("Qwen3.5-0.8B-MNN", downloader.findAnyInstalledModel())
    }

    @Test
    fun observeStatus_reportsInstalledWhenComplete() = runTest {
        val dir = File(modelsDir, "Qwen3.5-0.8B-MNN")
        dir.mkdirs()
        LocalQwenModelCatalog.requiredFiles.forEach { name ->
            File(dir, name).writeText("data-$name")
        }

        downloader.observeStatus("Qwen3.5-0.8B-MNN").test {
            val state = awaitItem()
            assertTrue(state.isInstalled)
            assertEquals(1f, state.progress, 0.001f)
            awaitComplete()
        }
    }

    @Test
    fun observeStatus_reportsNotInstalledWhenMissing() = runTest {
        downloader.observeStatus("Qwen3.5-0.8B-MNN").test {
            val state = awaitItem()
            assertFalse(state.isInstalled)
            assertEquals(0f, state.progress, 0.001f)
            awaitComplete()
        }
    }

    @Test
    fun download_sendsRangeHeaderWhenPartialFileExists() = runTest {
        val partialDir = File(modelsDir, ".Qwen3.5-0.8B-MNN.partial")
        partialDir.mkdirs()
        val partialFile = File(partialDir, "config.json")
        partialFile.writeText("partial-data")
        val partialSize = partialFile.length()

        val allFileData = LocalQwenModelCatalog.requiredFiles.associateWith { "content-of-$it" }
        fakeInterceptor.responses = allFileData.mapValues { (name, content) ->
            if (name == "config.json") {
                // Return 206 for Range request on partial file
                FakeResponse(code = 206, body = content.substring(partialSize.toInt()))
            } else {
                FakeResponse(code = 200, body = content)
            }
        }

        val states = downloader.download("Qwen3.5-0.8B-MNN").toList()

        // Verify Range header was sent for the partial file
        val configRequest = fakeInterceptor.requests.firstOrNull {
            it.url.toString().contains("config.json")
        }
        assertNotNull(configRequest)
        assertEquals("bytes=$partialSize-", configRequest!!.header("Range"))

        // Verify final state is installed
        val finalState = states.last()
        assertTrue(finalState.isInstalled)
    }

    @Test
    fun download_doesNotDeletePartialDirOnStart() = runTest {
        val partialDir = File(modelsDir, ".Qwen3.5-0.8B-MNN.partial")
        partialDir.mkdirs()
        val existingPartial = File(partialDir, "config.json")
        existingPartial.writeText("already-downloaded-bytes")

        val allFileData = LocalQwenModelCatalog.requiredFiles.associateWith { "content-of-$it" }
        fakeInterceptor.responses = allFileData.mapValues { (name, content) ->
            if (name == "config.json") {
                FakeResponse(code = 206, body = "remaining-bytes")
            } else {
                FakeResponse(code = 200, body = content)
            }
        }

        downloader.download("Qwen3.5-0.8B-MNN").toList()

        // Verify the partial file content was preserved and appended
        val installedConfig = File(modelsDir, "Qwen3.5-0.8B-MNN/config.json")
        assertTrue(installedConfig.exists())
        val content = installedConfig.readText()
        assertTrue("Should contain original partial data", content.startsWith("already-downloaded-bytes"))
        assertTrue("Should contain appended data", content.endsWith("remaining-bytes"))
    }

    @Test
    fun download_handles416ResponseAsAlreadyComplete() = runTest {
        val partialDir = File(modelsDir, ".Qwen3.5-0.8B-MNN.partial")
        partialDir.mkdirs()
        File(partialDir, "config.json").writeText("complete-content")

        val allFileData = LocalQwenModelCatalog.requiredFiles.associateWith { "content-of-$it" }
        fakeInterceptor.responses = allFileData.mapValues { (name, content) ->
            if (name == "config.json") {
                FakeResponse(code = 416, body = "")
            } else {
                FakeResponse(code = 200, body = content)
            }
        }

        val states = downloader.download("Qwen3.5-0.8B-MNN").toList()

        val finalState = states.last()
        assertTrue(finalState.isInstalled)
    }

    @Test
    fun download_handles200ResponseByRestartingFile() = runTest {
        val partialDir = File(modelsDir, ".Qwen3.5-0.8B-MNN.partial")
        partialDir.mkdirs()
        File(partialDir, "config.json").writeText("stale-partial")

        val allFileData = LocalQwenModelCatalog.requiredFiles.associateWith { "content-of-$it" }
        fakeInterceptor.responses = allFileData.mapValues { (_, content) ->
            // Server returns 200 (no Range support), not 206
            FakeResponse(code = 200, body = content)
        }

        val states = downloader.download("Qwen3.5-0.8B-MNN").toList()

        // Verify file was overwritten (not appended)
        val installedConfig = File(modelsDir, "Qwen3.5-0.8B-MNN/config.json")
        assertEquals("content-of-config.json", installedConfig.readText())
        assertTrue(states.last().isInstalled)
    }

    @Test
    fun download_emitsProgressStates() = runTest {
        val allFileData = LocalQwenModelCatalog.requiredFiles.associateWith { "content-of-$it" }
        fakeInterceptor.responses = allFileData.mapValues { (_, content) ->
            FakeResponse(code = 200, body = content)
        }

        val states = downloader.download("Qwen3.5-0.8B-MNN").toList()

        assertTrue("Should emit multiple states", states.size > 1)
        assertTrue("First state should be downloading", states.first().isDownloading)
        assertTrue("Last state should be installed", states.last().isInstalled)
        assertTrue("Progress should increase", states.dropLast(1).any { it.progress > 0f })
    }

    @Test
    fun download_reusesExistingCompleteFiles() = runTest {
        val modelDir = File(modelsDir, "Qwen3.5-0.8B-MNN")
        modelDir.mkdirs()
        // Pre-install config.json and llm_config.json
        File(modelDir, "config.json").writeText("existing-config")
        File(modelDir, "llm_config.json").writeText("existing-llm-config")

        val allFileData = LocalQwenModelCatalog.requiredFiles.associateWith { "content-of-$it" }
        fakeInterceptor.responses = allFileData.mapValues { (_, content) ->
            FakeResponse(code = 200, body = content)
        }

        val states = downloader.download("Qwen3.5-0.8B-MNN").toList()

        // Verify reused files were not re-downloaded
        val downloadRequests = fakeInterceptor.requests.map { it.url.toString() }
        assertFalse("config.json should not be re-downloaded",
            downloadRequests.any { it.endsWith("config.json") })
        assertFalse("llm_config.json should not be re-downloaded",
            downloadRequests.any { it.endsWith("llm_config.json") })

        // Verify reused content was preserved
        val installedConfig = File(modelsDir, "Qwen3.5-0.8B-MNN/config.json")
        assertEquals("existing-config", installedConfig.readText())

        assertTrue(states.last().isInstalled)
    }

    @Test
    fun download_forceRedownloadsAllFilesEvenWhenInstalled() = runTest {
        val modelDir = File(modelsDir, "Qwen3.5-0.8B-MNN")
        modelDir.mkdirs()
        // Pre-install all files
        LocalQwenModelCatalog.requiredFiles.forEach { name ->
            File(modelDir, name).writeText("old-$name")
        }

        val allFileData = LocalQwenModelCatalog.requiredFiles.associateWith { "new-content-of-$it" }
        fakeInterceptor.responses = allFileData.mapValues { (_, content) ->
            FakeResponse(code = 200, body = content)
        }

        val states = downloader.download("Qwen3.5-0.8B-MNN", force = true).toList()

        // Verify ALL files were re-downloaded (not reused)
        val downloadRequests = fakeInterceptor.requests.map { it.url.toString() }
        LocalQwenModelCatalog.requiredFiles.forEach { name ->
            assertTrue("$name should be re-downloaded with force=true",
                downloadRequests.any { it.endsWith(name) })
        }

        // Verify content was replaced
        val installedConfig = File(modelsDir, "Qwen3.5-0.8B-MNN/config.json")
        assertEquals("new-content-of-config.json", installedConfig.readText())

        assertTrue(states.last().isInstalled)
    }

    @Test
    fun download_withoutForce_reusesInstalledFiles() = runTest {
        val modelDir = File(modelsDir, "Qwen3.5-0.8B-MNN")
        modelDir.mkdirs()
        LocalQwenModelCatalog.requiredFiles.forEach { name ->
            File(modelDir, name).writeText("existing-$name")
        }

        val allFileData = LocalQwenModelCatalog.requiredFiles.associateWith { "new-$it" }
        fakeInterceptor.responses = allFileData.mapValues { (_, content) ->
            FakeResponse(code = 200, body = content)
        }

        val states = downloader.download("Qwen3.5-0.8B-MNN").toList()

        // Verify no network requests were made
        assertTrue("No files should be downloaded when all exist", fakeInterceptor.requests.isEmpty())

        // Verify original content was preserved
        val installedConfig = File(modelsDir, "Qwen3.5-0.8B-MNN/config.json")
        assertEquals("existing-config.json", installedConfig.readText())

        assertTrue(states.last().isInstalled)
    }

    @Test
    fun validateInstall_catchesEmptyFiles() = runTest {
        val allFileData = LocalQwenModelCatalog.requiredFiles.associateWith { "content-of-$it" }
        // Return empty body for one file
        fakeInterceptor.responses = allFileData.mapValues { (name, content) ->
            if (name == "tokenizer.txt") {
                FakeResponse(code = 200, body = "")
            } else {
                FakeResponse(code = 200, body = content)
            }
        }

        try {
            downloader.download("Qwen3.5-0.8B-MNN").toList()
            assertTrue("Should have thrown", false)
        } catch (e: Exception) {
            assertTrue(e.message.orEmpty().contains("incomplete"))
            assertTrue(e.message.orEmpty().contains("tokenizer.txt"))
        }
    }

    @Test
    fun download_mutexBlocksConcurrentDownloads() = runTest {
        val allFileData = LocalQwenModelCatalog.requiredFiles.associateWith { "content-of-$it" }
        fakeInterceptor.responses = allFileData.mapValues { (_, content) ->
            FakeResponse(code = 200, body = content, delayMs = 50)
        }

        val job1 = async { downloader.download("Qwen3.5-0.8B-MNN").toList() }
        delay(10) // Let first download acquire mutex
        val job2 = async { downloader.download("Qwen3.5-0.8B-MNN").toList() }

        val result1 = job1.await()
        val result2 = job2.await()

        // Both should eventually complete (serialized, not corrupted)
        assertTrue(result1.last().isInstalled)
        assertTrue(result2.last().isInstalled)
    }

    private data class FakeResponse(
        val code: Int,
        val body: String,
        val delayMs: Long = 0,
    )

    private class FakeDownloadInterceptor : Interceptor {
        val requests = mutableListOf<Request>()
        var responses: Map<String, FakeResponse> = emptyMap()

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            requests.add(request)

            val fileName = request.url.pathSegments.last()
            val fakeResponse = responses[fileName]
                ?: throw IllegalStateException("No fake response for: $fileName")

            if (fakeResponse.delayMs > 0) {
                Thread.sleep(fakeResponse.delayMs)
            }

            val responseBody = fakeResponse.body.toResponseBody("application/octet-stream".toMediaType())
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(fakeResponse.code)
                .message(if (fakeResponse.code == 206) "Partial Content" else if (fakeResponse.code == 416) "Range Not Satisfiable" else "OK")
                .body(responseBody)
                .build()
        }
    }
}
