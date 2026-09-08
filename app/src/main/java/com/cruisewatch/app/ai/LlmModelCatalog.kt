package com.cruisewatch.app.ai

/** Public, no-login LiteRT-community models (Hugging Face) — https://huggingface.co/litert-community */
data class LlmModel(
    val id: String,
    val displayName: String,
    val tier: DeviceTier,
    val approxSizeMb: Int,
    val downloadUrl: String,
    val fileName: String,
)

object LlmModelCatalog {
    val QWEN_0_5B = LlmModel(
        id = "qwen2.5-0.5b-instruct-q8",
        displayName = "Qwen2.5 0.5B (fast, lower quality)",
        tier = DeviceTier.LOW,
        approxSizeMb = 547,
        downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        fileName = "qwen2.5-0.5b-instruct-q8.task",
    )

    val QWEN_1_5B = LlmModel(
        id = "qwen2.5-1.5b-instruct-q8",
        displayName = "Qwen2.5 1.5B (smarter, needs more RAM)",
        tier = DeviceTier.HIGH,
        approxSizeMb = 1600,
        downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_seq128_q8_ekv1280.task",
        fileName = "qwen2.5-1.5b-instruct-q8.task",
    )

    val all = listOf(QWEN_0_5B, QWEN_1_5B)

    fun recommended(tier: DeviceTier): LlmModel = when (tier) {
        DeviceTier.LOW -> QWEN_0_5B
        DeviceTier.HIGH -> QWEN_1_5B
    }

    fun byId(id: String): LlmModel? = all.find { it.id == id }
}
