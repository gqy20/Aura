package com.xiaoqi.companion.feature.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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

        composeTestRule.onNodeWithText("Thinking...▌").assertIsDisplayed()
    }
}
