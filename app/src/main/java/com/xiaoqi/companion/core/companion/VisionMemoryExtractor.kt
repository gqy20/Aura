package com.xiaoqi.companion.core.companion

import com.xiaoqi.companion.core.companion.model.UserInput
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.data.db.converter.MemoryType
import com.xiaoqi.companion.data.repository.MemoryRepository
import com.xiaoqi.companion.data.repository.SaveMemoryRequest
import javax.inject.Inject

class VisionMemoryExtractor @Inject constructor(
    private val memoryRepository: MemoryRepository,
) {

    suspend fun extractAndSave(
        input: UserInput.Vision,
        assistantReply: String,
        sourceMessageIds: List<String> = emptyList(),
    ): Boolean {
        val text = input.text.trim()
        if (!shouldCapture(text)) return false

        val memoryText = buildMemoryText(text, assistantReply)
        if (memoryText.isBlank()) return false

        val result = memoryRepository.saveMemory(
            SaveMemoryRequest(
                content = memoryText,
                type = inferType(text),
                importance = inferImportance(text),
                confidence = inferConfidence(text),
                source = "vision:post_response",
                sourceMessageIds = sourceMessageIds,
                sensitivity = inferSensitivity(text),
            )
        )
        AppLogger.info(
            LogTags.Runtime,
            "vision_memory_saved",
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

    private fun buildMemoryText(text: String, assistantReply: String): String {
        val cleanText = text.replace(Regex("\\s+"), " ").trim()
        val visualContext = assistantReply
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_REPLY_CONTEXT_CHARS)
        return if (visualContext.isBlank()) {
            "From a shared image: $cleanText"
        } else {
            "From a shared image: $cleanText. Visual context: $visualContext"
        }
    }

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
            0.75f
        } else {
            0.55f
        }

    private fun inferConfidence(text: String): Float =
        if (text.length >= 8) 0.72f else 0.6f

    private fun inferSensitivity(text: String): String =
        if (sensitiveMarkers.any { text.lowercase().contains(it) } || zhSensitiveMarkers.any { text.contains(it) }) {
            "private"
        } else {
            "normal"
        }

    private companion object {
        const val MAX_REPLY_CONTEXT_CHARS = 160

        val captureMarkers = listOf("this is", "this was", "my ", "our ", "remember", "save this", "remind me")
        val zhCaptureMarkers = listOf("这是", "這是", "这个是", "這個是", "我的", "我们", "我們", "记住", "記住", "提醒我", "帮我记")

        val proceduralMarkers = listOf("remind me", "remember to", "help me")
        val zhProceduralMarkers = listOf("提醒我", "记得帮我", "記得幫我", "帮我")

        val factMarkers = listOf("my ", "our ", "called", "named")
        val zhFactMarkers = listOf("我的", "我们", "我們", "叫", "名字")

        val importantMarkers = listOf("important", "remember", "remind me")
        val zhImportantMarkers = listOf("重要", "记住", "記住", "提醒我")

        val sensitiveMarkers = listOf("private", "secret", "medical", "health", "id card", "passport")
        val zhSensitiveMarkers = listOf("隐私", "隱私", "秘密", "病历", "病歷", "身份证", "身份證", "护照", "護照")
        val questionMarkers = listOf("?", "？", "什么", "什麼", "吗", "嗎", "是不是")
    }
}
