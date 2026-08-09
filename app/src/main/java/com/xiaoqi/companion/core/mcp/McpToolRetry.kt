package com.xiaoqi.companion.core.mcp

import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.core.tools.ToolCategory
import com.xiaoqi.companion.core.tools.ToolMetadataRegistry
import java.io.IOException
import kotlin.random.Random
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay

internal data class McpRetryProgress(
    val toolName: String,
    val nextAttempt: Int,
    val maxAttempts: Int,
)

internal class McpRetryProgressContext(
    val onRetry: (McpRetryProgress) -> Unit,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<McpRetryProgressContext>
}

internal enum class ToolFailureKind {
    NETWORK,
    RATE_LIMITED,
    SERVER,
    AUTH,
    ARGUMENT,
    PERMANENT,
}

internal data class McpToolRetryPolicy(
    val maxAttempts: Int,
    val initialDelayMs: Long = 300,
    val maxDelayMs: Long = 2_000,
    val maxElapsedMs: Long = 30_000,
) {
    companion object {
        fun forTool(toolName: String): McpToolRetryPolicy {
            val metadata = ToolMetadataRegistry.remoteMcp(toolName)
            return if (metadata.category == ToolCategory.REMOTE_READ) {
                McpToolRetryPolicy(maxAttempts = 3)
            } else {
                McpToolRetryPolicy(maxAttempts = 1)
            }
        }
    }
}

internal suspend fun <T> withMcpToolRetry(
    serverUrl: String,
    toolName: String,
    policy: McpToolRetryPolicy = McpToolRetryPolicy.forTool(toolName),
    block: suspend () -> T,
): T {
    val startedAt = System.currentTimeMillis()
    var attempt = 1
    while (true) {
        try {
            return block()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            val failureKind = error.toToolFailureKind()
            val delayMs = retryDelayMs(attempt, error, policy)
            val elapsedMs = System.currentTimeMillis() - startedAt
            val shouldRetry = attempt < policy.maxAttempts &&
                failureKind.isRetryable &&
                elapsedMs + delayMs <= policy.maxElapsedMs
            if (!shouldRetry) {
                if (attempt > 1) {
                    AppLogger.warn(
                        LogTags.Tools,
                        "mcp_tool_retry_exhausted",
                        "serverHost" to serverUrl.hostForLog(),
                        "toolName" to toolName,
                        "attempts" to attempt,
                        "failureKind" to failureKind.name,
                        "elapsedMs" to elapsedMs,
                    )
                }
                throw error
            }

            AppLogger.warn(
                LogTags.Tools,
                "mcp_tool_retry_scheduled",
                "serverHost" to serverUrl.hostForLog(),
                "toolName" to toolName,
                "attempt" to attempt,
                "nextAttempt" to (attempt + 1),
                "delayMs" to delayMs,
                "failureKind" to failureKind.name,
                "statusCode" to (error as? McpHttpException)?.statusCode,
            )
            currentCoroutineContext()[McpRetryProgressContext]?.onRetry(
                McpRetryProgress(
                    toolName = toolName,
                    nextAttempt = attempt + 1,
                    maxAttempts = policy.maxAttempts,
                )
            )
            delay(delayMs)
            attempt += 1
        }
    }
}

private val ToolFailureKind.isRetryable: Boolean
    get() = this == ToolFailureKind.NETWORK ||
        this == ToolFailureKind.RATE_LIMITED ||
        this == ToolFailureKind.SERVER

private fun Exception.toToolFailureKind(): ToolFailureKind = when (this) {
    is McpHttpException -> when {
        statusCode == 408 -> ToolFailureKind.NETWORK
        statusCode == 429 -> ToolFailureKind.RATE_LIMITED
        statusCode in 500..599 -> ToolFailureKind.SERVER
        statusCode == 401 || statusCode == 403 -> ToolFailureKind.AUTH
        statusCode in 400..499 -> ToolFailureKind.ARGUMENT
        else -> ToolFailureKind.PERMANENT
    }
    is McpRpcException -> if (code == -32602) ToolFailureKind.ARGUMENT else ToolFailureKind.PERMANENT
    is McpToolResultException -> ToolFailureKind.PERMANENT
    is IOException -> ToolFailureKind.NETWORK
    else -> ToolFailureKind.PERMANENT
}

private fun retryDelayMs(
    attempt: Int,
    error: Exception,
    policy: McpToolRetryPolicy,
): Long {
    (error as? McpHttpException)?.retryAfterMs?.let {
        return it.coerceIn(0, policy.maxDelayMs)
    }
    var base = policy.initialDelayMs
    repeat((attempt - 1).coerceAtLeast(0)) {
        base = (base * 2).coerceAtMost(policy.maxDelayMs)
    }
    if (base == 0L) return 0
    val jitter = (base / 5).coerceAtLeast(1)
    return (base + Random.nextLong(-jitter, jitter + 1)).coerceIn(0, policy.maxDelayMs)
}
