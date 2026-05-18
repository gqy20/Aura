package com.xiaoqi.companion.core.companion

import com.xiaoqi.companion.core.companion.model.UserInput
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.data.db.converter.MemoryType
import com.xiaoqi.companion.data.repository.MemoryRepository
import com.xiaoqi.companion.data.repository.SaveMemoryRequest
import javax.inject.Inject

class TextMemoryExtractor @Inject constructor(
    private val memoryRepository: MemoryRepository,
) {

    suspend fun extractAndSave(
        input: UserInput,
        sourceMessageIds: List<String> = emptyList(),
    ): Boolean {
        if (input is UserInput.Vision) return false

        val text = input.content.replace(Regex("\\s+"), " ").trim()
        if (!shouldCapture(text)) return false

        val result = memoryRepository.saveMemory(
            SaveMemoryRequest(
                content = buildMemoryText(text),
                type = inferType(text),
                importance = inferImportance(text),
                confidence = inferConfidence(text),
                source = "text:post_response",
                sourceMessageIds = sourceMessageIds,
                sensitivity = inferSensitivity(text),
            )
        )
        AppLogger.info(
            LogTags.Runtime,
            "text_memory_saved",
            "memoryId" to result.memory.id,
            "merged" to result.merged,
        )
        return true
    }

    private fun shouldCapture(text: String): Boolean {
        if (text.isBlank()) return false
        if (questionMarkers.any { text.contains(it) }) return false

        val normalized = text.lowercase()
        return captureMarkers.any { normalized.contains(it) } ||
            zhCaptureMarkers.any { text.contains(it) }
    }

    private fun buildMemoryText(text: String): String =
        "User said: ${text.take(MAX_MEMORY_CHARS)}"

    private fun inferType(text: String): MemoryType {
        val normalized = text.lowercase()
        return when {
            proceduralMarkers.any { normalized.contains(it) } ||
                zhProceduralMarkers.any { text.contains(it) } -> MemoryType.PROCEDURAL
            factMarkers.any { normalized.contains(it) } ||
                zhFactMarkers.any { text.contains(it) } -> MemoryType.FACT
            else -> MemoryType.EPISODE
        }
    }

    private fun inferImportance(text: String): Float =
        if (importantMarkers.any { text.lowercase().contains(it) } || zhImportantMarkers.any { text.contains(it) }) {
            0.85f
        } else {
            0.65f
        }

    private fun inferConfidence(text: String): Float =
        if (text.length >= 8) 0.74f else 0.62f

    private fun inferSensitivity(text: String): String =
        if (sensitiveMarkers.any { text.lowercase().contains(it) } || zhSensitiveMarkers.any { text.contains(it) }) {
            "private"
        } else {
            "normal"
        }

    private companion object {
        const val MAX_MEMORY_CHARS = 240

        val captureMarkers = listOf(
            "remember that",
            "remember this",
            "please remember",
            "remind me",
            "my name is",
            "my birthday",
            "i live in",
            "i like",
            "i love",
            "i prefer",
            "i dislike",
            "i hate",
        )
        val zhCaptureMarkers = listOf(
            "记住",
            "帮我记",
            "记得我",
            "提醒我",
            "我的名字",
            "我叫",
            "我的生日",
            "我生日",
            "我住在",
            "我喜欢",
            "我爱",
            "我偏好",
            "我讨厌",
            "我不喜欢",
            "我习惯",
        )

        val proceduralMarkers = listOf("remind me", "remember to")
        val zhProceduralMarkers = listOf("提醒我", "记得帮我", "帮我记得")

        val factMarkers = listOf("my name is", "my birthday", "i live in", "i like", "i love", "i prefer")
        val zhFactMarkers = listOf("我的名字", "我叫", "我的生日", "我生日", "我住在", "我喜欢", "我爱", "我偏好")

        val importantMarkers = listOf("remember", "remind me", "important")
        val zhImportantMarkers = listOf("记住", "提醒我", "重要")

        val sensitiveMarkers = listOf("private", "secret", "medical", "health", "id card", "passport")
        val zhSensitiveMarkers = listOf("隐私", "秘密", "病历", "身份证", "护照")
        val questionMarkers = listOf("?", "？", "吗", "么", "什么")
    }
}
