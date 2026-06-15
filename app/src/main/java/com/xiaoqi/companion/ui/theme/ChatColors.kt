package com.xiaoqi.companion.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 聊天域的容器色常量。
 *
 * 为什么不放在 Material3 ColorScheme:
 * - 这 4 个值都是静态米色,跟 M3 primary/secondary 没主题联动,Compose Preview 也不需要做 light/dark
 *   切换。把它们放进 MaterialTheme.colorScheme 反而会强制所有调用方接受
 *   `colorScheme.surfaceVariant = #F7F2EA` 之类的覆盖,污染 M3 主题契约。
 * - 后续如果要做暗色,这里改成 object 的两个工厂 (light/dark) 即可,不用改调用方。
 *
 * 与 [ChatInputBar] 内部的 InputBarContainerColor 等命名互不干扰 —— 那里是输入栏专属
 * 微调,这里是一般容器基线。
 */
object ChatColors {
    /** 卡片/分组底色 —— Settings / Memory / MCP / Health 等分组 Surface。 */
    val CardSurface: Color = Color(0xFFF7F2EA)

    /** 输入框/聊天气泡的非聚焦底色 —— 与 CardSurface 同色系但更白。 */
    val InputSurface: Color = Color(0xFFFFFCF6)

    /** AI 消息气泡底色 —— 暖米黄,提示"这是系统侧的卡片"。 */
    val BubbleAi: Color = Color(0xFFFFF8EA)

    /** 用户消息气泡底色 —— 浅鼠尾草绿,跟 AI 气泡形成对位。 */
    val BubbleUser: Color = Color(0xFFDDE8D9)
}
