package com.xiaoqi.companion.feature.chat

/**
 * Room 保存最终回复后可能先于 Complete 事件刷新。用时间边界识别本轮新落库消息，
 * 避免依赖尚未完整的流式正文做 contains 匹配而短暂渲染两个气泡。
 *
 * id 处理：落库行接管内容，但保留 streamingTail 的 id 作 LazyColumn key
 * （记录行 id 到 persistedId），完成瞬间不触发整条消息的重淡入。
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
                id = streamingTail.id,
                persistedId = message.id,
                isStreaming = true,
                intentText = streamingTail.intentText,
                toolSteps = streamingTail.toolSteps,
                performanceInfo = streamingTail.performanceInfo,
                toolStatus = streamingTail.toolStatus,
                toolStatusType = streamingTail.toolStatusType,
                toolCallIds = streamingTail.toolCallIds,
                completionState = streamingTail.completionState,
            )
        }
    }
}

/**
 * DB 每次全量刷新时，让同一逻辑消息延续之前的 UI id 与瞬态字段
 * （performanceInfo/toolSteps/intentText 等只存在于内存，DB 行里没有）。
 * 匹配优先级：persistedId 精确匹配 → role+content 匹配；命中即从候选中消费，
 * 防止两条内容相同的消息继承同一个 id。
 */
internal fun stabilizePersistedMessages(
    dbMessages: List<ChatMessage>,
    previous: List<ChatMessage>,
): List<ChatMessage> {
    val candidates = previous.toMutableList()
    return dbMessages.map { dbMsg ->
        val matched = candidates.indexOfFirst { prev ->
            prev.persistedId != null && prev.persistedId == dbMsg.id
        }.takeIf { it >= 0 } ?: candidates.indexOfFirst { prev ->
            prev.role == dbMsg.role && prev.content == dbMsg.content
        }.takeIf { it >= 0 }

        if (matched == null) {
            dbMsg
        } else {
            val prev = candidates.removeAt(matched)
            dbMsg.copy(
                id = prev.id,
                persistedId = dbMsg.id,
                intentText = prev.intentText,
                toolSteps = prev.toolSteps,
                performanceInfo = prev.performanceInfo,
                toolStatus = prev.toolStatus,
                toolStatusType = prev.toolStatusType,
                toolCallIds = prev.toolCallIds,
                completionState = prev.completionState,
            )
        }
    }
}
