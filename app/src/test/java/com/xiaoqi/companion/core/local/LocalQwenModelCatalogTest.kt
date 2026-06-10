package com.xiaoqi.companion.core.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalQwenModelCatalogTest {

    @Test
    fun models_includeOfficialModelScopeQwen35Range() {
        val names = LocalQwenModelCatalog.models.map { it.modelName }

        assertEquals(
            listOf("Qwen3.5-0.8B-MNN", "Qwen3.5-2B-MNN", "Qwen3.5-4B-MNN"),
            names,
        )
        assertTrue(LocalQwenModelCatalog.models.first().recommended)
    }

    @Test
    fun downloadUrl_usesModelScopeOfficialRepository() {
        val spec = LocalQwenModelCatalog.requireSpec("Qwen3.5-0.8B-MNN")

        assertEquals(
            "https://www.modelscope.cn/models/MNN/Qwen3.5-0.8B-MNN/resolve/master/config.json",
            spec.downloadUrl("config.json"),
        )
    }

    @Test
    fun requiredFiles_includeMnnLlmMetadata() {
        assertEquals(
            listOf(
                "config.json",
                "llm_config.json",
                "llm.mnn",
                "llm.mnn.json",
                "llm.mnn.weight",
                "tokenizer.txt",
            ),
            LocalQwenModelCatalog.requiredFiles,
        )
    }
}
