package com.xiaoqi.companion.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 聊天域的状态语义色常量。
 *
 * 命名按"使用场景"而非"色相"组织:
 * - SuccessDot / SuccessText:两个绿都用以表达"成功",但 Dot 偏亮用于
 *   Canvas 状态点 (8.dp 圆点),Text 偏深用于正文/标签 (WCAG AA 对比度更好)。
 * - Warning:琥珀色,既用于"警告/未就绪",也用于"进行中/重要"。具体语义
 *   由调用方按上下文赋予,色值统一。
 * - Unknown:中性灰,用于"未知状态/禁用/0 个工具"等"信息不足"场景。
 *
 * 与 [ChatColors] 同样不进 MaterialTheme.colorScheme —— 状态色是 hard
 * semantic,跟 M3 primary/secondary 没联动关系,放进 ColorScheme 反而污染主题契约。
 */
object ChatStatusColors {
    /** 成功状态点 —— 8dp Canvas 圆点、ToolStatusPill SUCCEEDED 等。 */
    val SuccessDot: Color = Color(0xFF3FA86B)

    /** 成功文字/标签 —— 比 Dot 更深,WCAG AA 文字对比度 OK。 */
    val SuccessText: Color = Color(0xFF2E7D32)

    /** 警告/进行中/未就绪 —— 琥珀色,语义由调用方按上下文赋予。 */
    val Warning: Color = Color(0xFFE5A100)

    /** 未知/禁用/空状态 —— 中性灰,信号"信息不足,中性等待"。 */
    val Unknown: Color = Color(0xFFB7B0A4)
}
