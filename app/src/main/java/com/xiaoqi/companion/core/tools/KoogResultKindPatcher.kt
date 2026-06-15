package com.xiaoqi.companion.core.tools

import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.ToolResultKind
import ai.koog.agents.core.feature.model.AIAgentError

/**
 * 在 Koog 把 tool result 喂给 LLM 前,把 envelope 失败的 `resultKind`
 * 从 `Success` 翻成 `Failure(AIAgentError(reason, hint))`,让 Koog 标准的
 * `Message.Tool.Result.isError = true` 信号被点亮。
 *
 * **两个独立通道协同**:
 * - 信封(envelope text):我们的协议,`{status:"error", reason, hint, details}` —— LLM 看
 * - Koog 标志位(`isError`):Koog 标准信号,provider 可能据此在 UI 上区分错误结果
 *
 * 一个返回 envelope 失败的 tool,这里同时点亮两路;返回 envelope 成功
 * 或纯字符串旧格式的,保持 `Success` 不动。
 *
 * **可单测** —— 纯函数,无副作用,接收/返回 `List<ReceivedToolResult>`。
 */
fun List<ReceivedToolResult>.withErrorResultKind(): List<ReceivedToolResult> = map { result ->
    if (!isError(result.content)) return@map result
    val reason = parseErrorReason(result.content) ?: "tool_error"
    val hint = parseErrorHint(result.content).orEmpty()
    result.copy(
        resultKind = ToolResultKind.Failure(
            error = AIAgentError(
                message = reason,
                stackTrace = "",
                cause = hint,
            )
        )
    )
}
