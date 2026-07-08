package com.xiaoqi.companion.core.task

enum class AgentLongTaskStatus {
    QUEUED,
    RUNNING,
    WAITING_FOR_USER,
    SUCCEEDED,
    FAILED,
    CANCELED,
}

data class AgentLongTask(
    val id: String,
    val title: String,
    val status: AgentLongTaskStatus,
    val progress: Float = 0f,
    val updatedAtMillis: Long = 0L,
    val summary: String = "",
) {
    val isActive: Boolean
        get() = status == AgentLongTaskStatus.QUEUED ||
            status == AgentLongTaskStatus.RUNNING ||
            status == AgentLongTaskStatus.WAITING_FOR_USER

    val clampedProgress: Float
        get() = progress.coerceIn(0f, 1f)
}

data class AgentLongTaskSummary(
    val activeCount: Int,
    val latestTitle: String?,
) {
    val hasActiveTasks: Boolean get() = activeCount > 0
}

fun List<AgentLongTask>.activeSummary(): AgentLongTaskSummary {
    val active = filter { it.isActive }
    return AgentLongTaskSummary(
        activeCount = active.size,
        latestTitle = active.maxByOrNull { it.updatedAtMillis }?.title,
    )
}
