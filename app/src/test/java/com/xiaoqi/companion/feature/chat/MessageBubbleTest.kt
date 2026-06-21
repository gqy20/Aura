package com.xiaoqi.companion.feature.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
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
                )
            )
        }

        // 有 tool chip 时不应叠安抚文案，回退到老 spinner
        composeTestRule.onNodeWithContentDescription("Aura 正在回复").assertIsDisplayed()
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
