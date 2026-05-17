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

class VisionMemoryExtractorTest {

    private val memoryRepository: MemoryRepository = mockk(relaxed = true)

    @Test
    fun extractAndSave_savesExplicitVisionMemory() = runTest {
        coEvery { memoryRepository.saveMemory(any()) } returns SaveMemoryResult(
            memory = MemoryEntity(
                id = "memory-1",
                type = MemoryType.FACT,
                content = "From a shared image: 这是我的猫，叫奶茶",
                source = "vision:post_response",
                timestamp = 1_000L,
            ),
            merged = false,
        )
        val extractor = VisionMemoryExtractor(memoryRepository)

        val saved = extractor.extractAndSave(
            input = UserInput.Vision(
                text = "这是我的猫，叫奶茶",
                imageBase64 = "base64",
            ),
            assistantReply = "我看到了这只猫。",
            sourceMessageIds = listOf("user-1", "assistant-1"),
        )

        assertTrue(saved)
        coVerify {
            memoryRepository.saveMemory(match<SaveMemoryRequest> {
                it.type == MemoryType.FACT &&
                    it.source == "vision:post_response" &&
                    it.sourceMessageIds == listOf("user-1", "assistant-1") &&
                    it.content.contains("奶茶")
            })
        }
    }

    @Test
    fun extractAndSave_skipsGenericVisionQuestion() = runTest {
        val extractor = VisionMemoryExtractor(memoryRepository)

        val saved = extractor.extractAndSave(
            input = UserInput.Vision(
                text = "这是什么菜？",
                imageBase64 = "base64",
            ),
            assistantReply = "这看起来像一份沙拉。",
        )

        assertFalse(saved)
        coVerify(exactly = 0) { memoryRepository.saveMemory(any()) }
    }
}
