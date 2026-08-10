package com.xiaoqi.companion.feature.chat

/**
 * Room 保存最终回复后可能先于 Complete 事件刷新。用时间边界识别本轮新落库消息，
 * 避免依赖尚未完整的流式正文做 contains 匹配而短暂渲染两个气泡。
 */
internal fun mergePersistedMessagesDuringStreaming(
    dbMessages: List<ChatMessage>,
    streamingTail: ChatMessage,
): List<ChatMessage> {
    val persistedCurrentReply = dbMessages.lastOrNull { message ->
        message.role == "ASSISTANT" && message.timestamp >= streamingTail.timestamp
    } ?: return dbMessages + streamingTail

    return dbMessages.map { message ->
        if (message.id != persistedCurrentReply.id) {
            message
        } else {
            message.copy(
                isStreaming = true,
                performanceInfo = streamingTail.performanceInfo,
                toolStatus = streamingTail.toolStatus,
                toolStatusType = streamingTail.toolStatusType,
                toolCallIds = streamingTail.toolCallIds,
                completionState = streamingTail.completionState,
            )
        }
    }
}
