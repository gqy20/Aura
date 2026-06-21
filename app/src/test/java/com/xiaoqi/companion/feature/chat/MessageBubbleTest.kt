package com.xiaoqi.companion.feature.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.xiaoqi.companion.core.companion.model.ToolCallStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class MessageBubbleTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun userMessage_displaysContent() {
        composeTestRule.setContent {
            MessageBubble(message = ChatMessage(id = "1", role = "USER", content = "Hello"))
        }

        composeTestRule.onNodeWithText("Hello").assertIsDisplayed()
    }

    @Test
    fun assistantMessage_displaysContent() {
        composeTestRule.setContent {
            MessageBubble(message = ChatMessage(id = "1", role = "ASSISTANT", content = "Hi there!"))
        }

        composeTestRule.onNodeWithText("Hi there!").assertIsDisplayed()
    }

    @Test
    fun streamingMessage_showsIndicator() {
        composeTestRule.setContent {
            MessageBubble(
                message = ChatMessage(id = "1", role = "ASSISTANT", content = "Thinking...", isStreaming = true)
            )
        }

        composeTestRule.onNodeWithText("Thinking...").assertIsDisplayed()
    }

    @Test
    fun streamingMessage_withoutContent_showsThinkingHint() {
        composeTestRule.setContent {
            MessageBubble(
                message = ChatMessage(id = "1", role = "ASSISTANT", content = "", isStreaming = true)
            )
        }

        // 无 tool chip 时走 ThinkingHintCarousel，首条文案为档0 第 0 条
        composeTestRule.onNodeWithText(ThinkingHints.hintFor(0L, 0)).assertIsDisplayed()
    }

    @Test
    fun streamingMessage_withoutContent_butWithToolStatus_showsSpinner() {
        composeTestRule.setContent {
            MessageBubble(
                message = ChatMessage(
                    id = "1",
                    role = "ASSISTANT",
                    content = "",
                    isStreaming = true,
                    toolStatus = "查找记忆",
                    // 真实工具调用一定带 type，这时才回退老 spinner，避免 chip 与安抚文案打架
                    toolStatusType = ToolCallStatus.STARTED,
                )
            )
        }

        // 真实工具调用（type!=null）时不应叠安抚文案，回退到老 spinner
        composeTestRule.onNodeWithContentDescription("Aura 正在回复").assertIsDisplayed()
    }

    @Test
    fun streamingMessage_localLoadingPlaceholder_showsHintNotPill() {
        composeTestRule.setContent {
            MessageBubble(
                message = ChatMessage(
                    id = "1",
                    role = "ASSISTANT",
                    content = "",
                    isStreaming = true,
                    // 本地模型预填的加载占位：有 text 但无 type
                    toolStatus = "本地模型加载并生成中",
                    toolStatusType = null,
                )
            )
        }

        // 本地加载占位最需要安抚，应走 carousel 而非静态 pill
        composeTestRule.onNodeWithText(ThinkingHints.hintFor(0L, 0)).assertIsDisplayed()
        composeTestRule.onAllNodesWithText("本地模型加载并生成中").assertCountEquals(0)
    }

    @Test
    fun assistantMessage_displaysToolStatus() {
        composeTestRule.setContent {
            MessageBubble(
                message = ChatMessage(
                    id = "1",
                    role = "ASSISTANT",
                    content = "I will remember that.",
                    toolStatus = "已保存记忆",
                    // 真实数据流里 toolStatus 与 type 总是成对赋值（见 SendMessageUseCase）
                    toolStatusType = ToolCallStatus.SUCCEEDED,
                )
            )
        }

        composeTestRule.onNodeWithText("已保存记忆").assertIsDisplayed()
    }

    @Test
    fun assistantMessage_rendersMarkdownInlineContent() {
        composeTestRule.setContent {
            MessageBubble(
                message = ChatMessage(
                    id = "1",
                    role = "ASSISTANT",
                    content = "This is **important** and `local`.",
                )
            )
        }

        composeTestRule.onNodeWithText("This is important and local.").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("This is **important** and `local`.").assertCountEquals(0)
    }

    @Test
    fun assistantMessage_rendersMarkdownCodeBlock() {
        composeTestRule.setContent {
            MessageBubble(
                message = ChatMessage(
                    id = "1",
                    role = "ASSISTANT",
                    content = "Try this:\n\n```kotlin\nval aura = true\n```",
                )
            )
        }

        composeTestRule.onNodeWithText("Try this:").assertIsDisplayed()
        composeTestRule.onNodeWithText("val aura = true").assertIsDisplayed()
    }
}
