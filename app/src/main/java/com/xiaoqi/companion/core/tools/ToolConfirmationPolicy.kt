package com.xiaoqi.companion.core.tools

data class ToolConfirmationRequirement(
    val required: Boolean,
    val title: String = "",
    val message: String = "",
)

object ToolConfirmationPolicy {
    fun requirement(metadata: ToolMetadata): ToolConfirmationRequirement {
        if (metadata.riskLevel == ToolRiskLevel.BLOCKED) {
            return ToolConfirmationRequirement(
                required = true,
                title = "需要确认",
                message = "这个操作当前被标记为不可直接执行。",
            )
        }
        return when (metadata.category) {
            ToolCategory.LOCAL_WRITE -> ToolConfirmationRequirement(
                required = true,
                title = "确认修改本地数据",
                message = "Aura 将在本机保存或修改与你有关的数据。",
            )
            ToolCategory.REMOTE_WRITE,
            ToolCategory.HIGH_RISK_ACTION -> ToolConfirmationRequirement(
                required = true,
                title = "确认执行操作",
                message = "这个操作可能影响外部服务或产生不可自动撤销的结果。",
            )
            ToolCategory.READ_CONTEXT,
            ToolCategory.REMOTE_READ -> ToolConfirmationRequirement(required = false)
        }
    }
}
