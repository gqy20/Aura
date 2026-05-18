package com.xiaoqi.companion.core.companion

import com.xiaoqi.companion.core.companion.model.UserInput
import com.xiaoqi.companion.data.db.converter.MemoryType
import com.xiaoqi.companion.data.db.entity.MemoryEntity
import com.xiaoqi.companion.data.repository.MemoryRepository
import com.xiaoqi.companion.data.repository.SaveMemoryRequest
import com.xiaoqi.companion.data.repository.SaveMemoryResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextMemoryExtractorTest {

    private val memoryRepository: MemoryRepository = mockk(relaxed = true)

    @Test
    fun extractAndSave_savesExplicitTextMemory() = runTest {
        coEvery { memoryRepository.saveMemory(any()) } returns SaveMemoryResult(
            memory = MemoryEntity(
                id = "memory-1",
                type = MemoryType.FACT,
                content = "User said: remember that I like jasmine tea",
                source = "text:post_response",
                timestamp = 1_000L,
            ),
            merged = false,
        )
        val extractor = TextMemoryExtractor(memoryRepository)

        val saved = extractor.extractAndSave(
            input = UserInput.Text("remember that I like jasmine tea"),
            sourceMessageIds = listOf("user-1", "assistant-1"),
        )

        assertTrue(saved)
        coVerify {
            memoryRepository.saveMemory(match<SaveMemoryRequest> {
                it.type == MemoryType.FACT &&
                    it.source == "text:post_response" &&
                    it.sourceMessageIds == listOf("user-1", "assistant-1") &&
                    it.content.contains("jasmine tea") &&
                    it.importance >= 0.8f
            })
        }
    }

    @Test
    fun extractAndSave_skipsGenericQuestion() = runTest {
        val extractor = TextMemoryExtractor(memoryRepository)

        val saved = extractor.extractAndSave(UserInput.Text("what should I eat tonight?"))

        assertFalse(saved)
        coVerify(exactly = 0) { memoryRepository.saveMemory(any()) }
    }
}
