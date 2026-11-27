package com.cruisewatch.app.ai

/**
 * Public, no-login LiteRT-community models (Hugging Face) — https://huggingface.co/litert-community
 *
 * Download URLs are PINNED to immutable Hugging Face commits (never the
 * mutable `resolve/main` ref, where the served file can change at any time),
 * and every download is verified against [LlmModel.expectedSha256] and
 * [LlmModel.expectedSizeBytes] before it is accepted — see ModelDownloadManager.
 * If Hugging Face publishes a newer model build, update the commit, size and
 * hash here together (values come from the repo's file metadata on
 * huggingface.co, which publishes the LFS SHA-256 for each file).
 */
data class LlmModel(
    val id: String,
    val displayName: String,
    val tier: DeviceTier,
    val approxSizeMb: Int,
    val downloadUrl: String,
    val fileName: String,
    /** Exact byte size of the pinned model file. */
    val expectedSizeBytes: Long,
    /** Lowercase hex SHA-256 of the pinned model file. */
    val expectedSha256: String,
)

object LlmModelCatalog {
    val QWEN_0_5B = LlmModel(
        id = "qwen2.5-0.5b-instruct-q8",
        displayName = "Qwen2.5 0.5B (fast, lower quality)",
        tier = DeviceTier.LOW,
        approxSizeMb = 547,
        downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/49bfd323c25c9dae2118906e1f3ffd16e0729b13/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        fileName = "qwen2.5-0.5b-instruct-q8.task",
        expectedSizeBytes = 546_660_344L,
        expectedSha256 = "e608953f169aeb1bd7b9155fec2559825e08453fc209b84eda3a781ed0452fd2",
    )

    val QWEN_1_5B = LlmModel(
        id = "qwen2.5-1.5b-instruct-q8",
        displayName = "Qwen2.5 1.5B (smarter, needs more RAM)",
        tier = DeviceTier.HIGH,
        approxSizeMb = 1567,
        downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/60257f9f1d00ca1f24cc5ce3b829b4139be1ca2b/Qwen2.5-1.5B-Instruct_seq128_q8_ekv1280.task",
        fileName = "qwen2.5-1.5b-instruct-q8.task",
        expectedSizeBytes = 1_567_364_648L,
        expectedSha256 = "8771564e61c908c2199bcaa28b0ff9c5f55afb2ae73fbe263142a067113968df",
    )

    val all = listOf(QWEN_0_5B, QWEN_1_5B)

    fun recommended(tier: DeviceTier): LlmModel = when (tier) {
        DeviceTier.LOW -> QWEN_0_5B
        DeviceTier.HIGH -> QWEN_1_5B
    }

    fun byId(id: String): LlmModel? = all.find { it.id == id }
}
