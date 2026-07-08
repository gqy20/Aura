package com.xiaoqi.companion.core.remote

enum class BrowserWorkerAction {
    OPEN_URL,
    READ_TEXT,
    SCREENSHOT,
    CLICK,
    TYPE_TEXT,
    SUBMIT_FORM,
    DOWNLOAD_FILE,
}

data class BrowserWorkerCommand(
    val action: BrowserWorkerAction,
    val url: String? = null,
    val selector: String? = null,
    val text: String? = null,
)

data class BrowserWorkerDecision(
    val allowed: Boolean,
    val reason: String,
)

object BrowserWorkerBoundary {
    fun decide(command: BrowserWorkerCommand): BrowserWorkerDecision =
        when (command.action) {
            BrowserWorkerAction.OPEN_URL,
            BrowserWorkerAction.READ_TEXT,
            BrowserWorkerAction.SCREENSHOT -> BrowserWorkerDecision(
                allowed = true,
                reason = "read_only_browser_action",
            )
            BrowserWorkerAction.CLICK,
            BrowserWorkerAction.TYPE_TEXT,
            BrowserWorkerAction.SUBMIT_FORM,
            BrowserWorkerAction.DOWNLOAD_FILE -> BrowserWorkerDecision(
                allowed = false,
                reason = "browser_worker_write_action_requires_explicit_user_confirmation",
            )
        }
}
