package com.cruisewatch.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the supply-chain pinning of the on-device AI models: download URLs
 * must stay pinned to immutable Hugging Face commits (never resolve/main)
 * and every model must carry a well-formed SHA-256 + exact size so
 * ModelDownloadManager's fail-closed verification has something to check.
 */
class LlmModelCatalogTest {

    @Test
    fun `download urls are pinned to immutable commits, never resolve main`() {
        for (model in LlmModelCatalog.all) {
            assertFalse(
                "${model.id} must not use the mutable resolve/main ref",
                model.downloadUrl.contains("/resolve/main/"),
            )
            assertTrue(
                "${model.id} URL must pin a full 40-char commit hash",
                model.downloadUrl.contains(Regex("/resolve/[0-9a-f]{40}/")),
            )
        }
    }

    @Test
    fun `every model carries a sha256 pin and exact size`() {
        for (model in LlmModelCatalog.all) {
            assertTrue(
                "${model.id} expectedSha256 must be 64 lowercase hex chars",
                model.expectedSha256.matches(Regex("[0-9a-f]{64}")),
            )
            assertTrue("${model.id} expectedSizeBytes must be positive", model.expectedSizeBytes > 0)
        }
    }

    @Test
    fun `pinned hashes match the published hugging face lfs metadata`() {
        // Values published by Hugging Face for the pinned commits
        // (litert-community/Qwen2.5-0.5B-Instruct @ 49bfd32,
        //  litert-community/Qwen2.5-1.5B-Instruct @ 60257f9).
        assertEquals(
            "e608953f169aeb1bd7b9155fec2559825e08453fc209b84eda3a781ed0452fd2",
            LlmModelCatalog.QWEN_0_5B.expectedSha256,
        )
        assertEquals(546_660_344L, LlmModelCatalog.QWEN_0_5B.expectedSizeBytes)
        assertEquals(
            "8771564e61c908c2199bcaa28b0ff9c5f55afb2ae73fbe263142a067113968df",
            LlmModelCatalog.QWEN_1_5B.expectedSha256,
        )
        assertEquals(1_567_364_648L, LlmModelCatalog.QWEN_1_5B.expectedSizeBytes)
    }
}
