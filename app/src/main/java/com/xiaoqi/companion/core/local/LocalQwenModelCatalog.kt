package com.xiaoqi.companion.core.local

object LocalQwenModelCatalog {
    val models: List<LocalQwenModelSpec> = listOf(
        LocalQwenModelSpec(
            modelName = "Qwen3.5-0.8B-MNN",
            repository = "MNN/Qwen3.5-0.8B-MNN",
            displayName = "Qwen3.5 0.8B",
            sizeLabel = "0.8B",
            recommended = true,
        ),
        LocalQwenModelSpec(
            modelName = "Qwen3.5-2B-MNN",
            repository = "MNN/Qwen3.5-2B-MNN",
            displayName = "Qwen3.5 2B",
            sizeLabel = "2B",
        ),
        LocalQwenModelSpec(
            modelName = "Qwen3.5-4B-MNN",
            repository = "MNN/Qwen3.5-4B-MNN",
            displayName = "Qwen3.5 4B",
            sizeLabel = "4B",
        ),
    )

    val requiredFiles: List<String> = listOf(
        "config.json",
        "llm.mnn",
        "llm.mnn.weight",
        "tokenizer.txt",
    )

    fun requireSpec(modelName: String): LocalQwenModelSpec =
        models.firstOrNull { it.modelName == modelName }
            ?: error("Unsupported local Qwen model: $modelName")
}

data class LocalQwenModelSpec(
    val modelName: String,
    val repository: String,
    val displayName: String,
    val sizeLabel: String,
    val recommended: Boolean = false,
) {
    fun downloadUrl(fileName: String): String =
        "https://www.modelscope.cn/models/$repository/resolve/master/$fileName"
}
