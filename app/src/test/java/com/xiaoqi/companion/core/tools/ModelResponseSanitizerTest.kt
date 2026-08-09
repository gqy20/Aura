package com.xiaoqi.companion.core.tools

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelResponseSanitizerTest {
    @Test
    fun removesHallucinatedToolProtocolBlock() {
        val response = """Useful answer.

            <tool_result>
            {"tool_call_id":"fake","tool":"update_state"}
            </tool_result>
        """.trimIndent()

        assertEquals("Useful answer.", response.withoutToolProtocolArtifacts())
    }

    @Test
    fun preservesOrdinaryAnswer() {
        assertEquals("Useful answer.", "Useful answer.".withoutToolProtocolArtifacts())
    }
}
