package com.xiaoqi.companion.core.tools

import org.junit.Assert.assertTrue
import org.junit.Test

class ToolResultPromptComposerTest {

    @Test
    fun followupInstruction_whenHasErrors_requestsNaturalFallback() {
        val text = ToolResultPromptComposer.followupInstruction(hasErrors = true)

        assertTrue(text.contains("failed", ignoreCase = true))
        assertTrue(text.contains("Do not invent", ignoreCase = true))
    }

    @Test
    fun followupInstruction_whenNoErrors_requestsNaturalAnswer() {
        val text = ToolResultPromptComposer.followupInstruction(hasErrors = false)

        assertTrue(text.contains("answer the user naturally", ignoreCase = true))
        assertTrue(text.contains("every explicit part", ignoreCase = true))
        assertTrue(text.contains("Do not repeat raw tool JSON", ignoreCase = true))
    }
}
