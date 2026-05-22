package com.xiaoqi.companion.core.companion

import com.xiaoqi.companion.core.companion.model.UserInput
import com.xiaoqi.companion.core.prompt.BuiltPrompt
import com.xiaoqi.companion.data.db.converter.LlmProvider
import com.xiaoqi.companion.data.db.converter.MemoryType
import com.xiaoqi.companion.data.db.entity.MemoryEntity
import com.xiaoqi.companion.data.repository.LlmConfig
import com.xiaoqi.companion.data.repository.MemoryRepository
import com.xiaoqi.companion.data.repository.SaveMemoryResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmConversationReflectionTest {

    private val memoryRepository: MemoryRepository = mockk(relaxed = true) {
        coEvery { saveMemory(any()) } returns SaveMemoryResult(
            memory = MemoryEntity(
                id = "memory-1",
                type = MemoryType.EPISODE,
                content = "memory",
                timestamp = 1_000L,
            ),
            merged = false,
        )
    }

    @Test
    fun reflectAndSave_whenModelSelectsMemory_savesItWithoutTools() = runTest {
        val agent = FakeKoogAgent.structured(
            """{"memories":[{"shouldSave":true,"content":"User has a team meeting tomorrow and needs to write a weekly report this afternoon.","type":"EPISODE","importance":0.8,"confidence":0.9,"sensitivity":"private","reason":"clear plan"}]}"""
        )
        val reflection = LlmConversationReflection(memoryRepository)

        val result = reflection.reflectAndSave(
            input = ConversationReflectionInput(
                userInput = UserInput.Text("记一下，我明天开组会，今天下午要写周报"),
                assistantReply = "我记住了。",
                sourceMessageIds = listOf("user-1", "assistant-1"),
                nowMillis = 1_000L,
            ),
            config = testConfig,
            agent = agent,
        )

        assertEquals(1, result.savedMemoryCount)
        assertFalse(agent.lastStructuredPrompt?.allowTools ?: true)
        coVerify {
            memoryRepository.saveMemory(match {
                it.content.contains("team meeting tomorrow") &&
                    it.type == MemoryType.EPISODE &&
                    it.importance == 0.8f &&
                    it.confidence == 0.9f &&
                    it.source == "reflection:glm" &&
                    it.sourceMessageIds == listOf("user-1", "assistant-1") &&
                    it.sensitivity == "private"
            })
        }
    }

    @Test
    fun reflectAndSave_whenModelRejectsMemory_doesNotSave() = runTest {
        val agent = FakeKoogAgent.structured(
            """{"memories":[{"shouldSave":false,"content":"hello","reason":"chit-chat"}]}"""
        )
        val reflection = LlmConversationReflection(memoryRepository)

        val result = reflection.reflectAndSave(
            input = ConversationReflectionInput(
                userInput = UserInput.Text("你好"),
                assistantReply = "你好呀。",
                sourceMessageIds = listOf("user-1", "assistant-1"),
            ),
            config = testConfig,
            agent = agent,
        )

        assertEquals(0, result.savedMemoryCount)
        coVerify(exactly = 0) { memoryRepository.saveMemory(any()) }
    }

    @Test
    fun reflectAndSave_limitsSavedMemoriesPerTurn() = runTest {
        val agent = FakeKoogAgent.structured(
            """
            {"memories":[
              {"shouldSave":true,"content":"one","type":"FACT"},
              {"shouldSave":true,"content":"two","type":"FACT"},
              {"shouldSave":true,"content":"three","type":"FACT"},
              {"shouldSave":true,"content":"four","type":"FACT"}
            ]}
            """.trimIndent()
        )
        val reflection = LlmConversationReflection(memoryRepository)

        val result = reflection.reflectAndSave(
            input = ConversationReflectionInput(
                userInput = UserInput.Text("remember several things"),
                assistantReply = "ok",
                sourceMessageIds = emptyList(),
            ),
            config = testConfig,
            agent = agent,
        )

        assertEquals(3, result.savedMemoryCount)
        coVerify(exactly = 3) { memoryRepository.saveMemory(any()) }
    }

    private class FakeKoogAgent(
        private val structuredResponseJson: String,
    ) : KoogAgentWrapper {
        var lastStructuredPrompt: BuiltPrompt? = null

        override suspend fun run(prompt: BuiltPrompt): String = error("run should not be used by reflection")

        override suspend fun <T> runStructured(
            prompt: BuiltPrompt,
            serializer: KSerializer<T>,
            examples: List<T>,
        ): T {
            lastStructuredPrompt = prompt
            return json.decodeFromString(serializer, structuredResponseJson)
        }

        override fun runStreaming(prompt: BuiltPrompt): Flow<String> = emptyFlow()

        companion object {
            fun structured(responseJson: String) = FakeKoogAgent(responseJson)
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val testConfig = LlmConfig(
            provider = LlmProvider.GLM,
            baseUrl = "https://example.test",
            apiKey = "test-key",
            modelName = "test-model",
        )
    }
}
