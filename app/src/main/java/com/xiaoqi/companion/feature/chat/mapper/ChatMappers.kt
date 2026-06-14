package com.xiaoqi.companion.feature.chat.mapper

import com.xiaoqi.companion.core.companion.model.ToolCallStatus
import com.xiaoqi.companion.core.local.LocalQwenModelDownloadState
import com.xiaoqi.companion.core.tools.ToolDisplayRegistry
import com.xiaoqi.companion.data.db.converter.LlmProvider
import com.xiaoqi.companion.data.db.converter.MessageRole
import com.xiaoqi.companion.data.db.entity.MemoryEntity
import com.xiaoqi.companion.data.db.entity.MessageEntity
import com.xiaoqi.companion.data.db.entity.ReminderEntity
import com.xiaoqi.companion.data.repository.DefaultLlmValues
import com.xiaoqi.companion.data.repository.LlmConfigStatus
import com.xiaoqi.companion.data.repository.ToolCallSnapshot
import com.xiaoqi.companion.feature.chat.ChatConfigStatus
import com.xiaoqi.companion.feature.chat.ChatImageAttachment
import com.xiaoqi.companion.feature.chat.ChatMemory
import com.xiaoqi.companion.feature.chat.ChatMessage
import com.xiaoqi.companion.feature.chat.ChatReminder
import com.xiaoqi.companion.feature.chat.ChatToolCall
import com.xiaoqi.companion.feature.chat.CompanionStatus
import com.xiaoqi.companion.feature.chat.LocalQwenDownloadUiState

/**
 * Chat 模块所有 Entity / Config / DownloadState → Chat* 模型的映射。
 *
 * 这些函数:
 * - **纯函数**:无副作用,不持有状态
 * - 集中在 mapper 包内,便于在 VM 与 UseCase 间共享,也便于未来替换为 Room TypeConverter
 * - 不做单位测试——这些映射的覆盖度由 VM/UseCase 集成测试间接保证
 */

internal fun MessageEntity.toChatMessage(): ChatMessage =
    ChatMessage(
        id = id,
        role = when (role) {
            MessageRole.USER -> "USER"
            MessageRole.ASSISTANT -> "ASSISTANT"
            MessageRole.SYSTEM -> "SYSTEM"
        },
        content = content,
        timestamp = timestamp,
        imageUri = imageBase64?.let { "data:image/jpeg;base64,$it" },
    )

internal fun MemoryEntity.toChatMemory(): ChatMemory =
    ChatMemory(
        id = id,
        content = content,
        type = type.name,
        importance = importance,
        source = source,
        timestamp = timestamp,
    )

internal fun ReminderEntity.toChatReminder(): ChatReminder =
    ChatReminder(
        id = id,
        title = title,
        message = message,
        triggerAtMillis = triggerAtMillis,
        exact = exact,
        status = status,
    )

internal fun ToolCallSnapshot.toChatToolCall(displayLabel: String): ChatToolCall =
    ChatToolCall(
        id = id,
        toolName = toolName,
        toolStatus = status,
        label = displayLabel,
        status = when (status) {
            ToolCallStatus.STARTED -> "Running"
            ToolCallStatus.SUCCEEDED -> "Done"
            ToolCallStatus.FAILED -> "Failed"
        },
        durationMs = durationMs,
        errorMessage = errorMessage,
    )

internal fun LlmConfigStatus.toChatConfigStatus(): ChatConfigStatus =
    if (provider == LlmProvider.LOCAL_QWEN && isReady) {
        ChatConfigStatus(
            label = "${provider.name} · $modelName",
            isReady = false,
            detail = "正在检查本地模型",
            provider = provider,
            modelName = modelName,
            baseUrl = baseUrl,
        )
    } else if (isReady) {
        ChatConfigStatus(
            label = "${provider.name} · $modelName",
            isReady = true,
            detail = "模型已就绪",
            provider = provider,
            modelName = modelName,
            baseUrl = baseUrl,
        )
    } else {
        ChatConfigStatus(
            label = "${provider.name} · ${modelName.ifBlank { "未选择模型" }}",
            isReady = false,
            detail = missingReason ?: "模型配置未完成",
            provider = provider,
            modelName = modelName,
            baseUrl = baseUrl,
        )
    }

internal fun ChatConfigStatus.withLocalQwenDownloadState(
    downloadState: LocalQwenModelDownloadState,
): ChatConfigStatus {
    if (provider != LlmProvider.LOCAL_QWEN || modelName != downloadState.modelName) {
        return this
    }
    return when {
        downloadState.isInstalled -> copy(
            isReady = true,
            detail = "本地模型已安装",
        )
        downloadState.isDownloading -> copy(
            isReady = false,
            detail = "本地模型下载中",
        )
        else -> copy(
            isReady = false,
            detail = downloadState.error ?: "请先下载本地模型",
        )
    }
}

internal fun LocalQwenModelDownloadState.toUiState(): LocalQwenDownloadUiState =
    LocalQwenDownloadUiState(
        modelName = modelName,
        isInstalled = isInstalled,
        isDownloading = isDownloading,
        progress = progress,
        downloadedBytes = downloadedBytes,
        totalBytes = totalBytes,
        message = message,
        error = error,
    )

internal fun defaultBaseUrl(provider: LlmProvider): String =
    DefaultLlmValues.defaultBaseUrl(provider)

internal fun ToolDisplayRegistry.displayLabel(toolName: String, status: ToolCallStatus): String =
    label(toolName, status)

internal fun CompanionStatus.after(
    mood: String,
    intensity: Float,
    affinityDelta: Float,
): CompanionStatus =
    copy(
        mood = mood.ifBlank { this.mood },
        intensity = intensity.coerceIn(0f, 1f),
        relationshipLevel = (relationshipLevel + affinityDelta).coerceIn(0f, 1f),
    )

/**
 * 从存于 [com.xiaoqi.companion.data.db.entity.AgentStateEntity.emotionVector] 的 JSON 字符串中
 * 提取 intensity 浮点值;若解析失败则回退到 0.5f 中性强度。
 */
internal fun String.extractIntensity(): Float {
    val value = Regex("\"intensity\"\\s*:\\s*([\\d.]+)")
        .find(this)
        ?.groupValues
        ?.getOrNull(1)
        ?.toFloatOrNull()
    return value?.coerceIn(0f, 1f) ?: 0.5f
}
