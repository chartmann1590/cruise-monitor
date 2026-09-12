package com.cruisewatch.app.ai

import com.google.mlkit.nl.translate.TranslateLanguage
import org.junit.Assert.assertTrue
import org.junit.Test

class RefundAssistantContextTest {

    @Test
    fun `system prompt instructs the model to respond in the selected language`() {
        val prompt = RefundAssistantContext.buildSystemPrompt(
            cruises = emptyList(),
            alerts = emptyList(),
            policyFor = { null },
            focusCruiseId = null,
            languageCode = TranslateLanguage.SPANISH,
        )
        assertTrue(prompt.contains("Respond ONLY in Español"))
    }

    @Test
    fun `automated kickoff adds a caveat so its english trigger text isn't mistaken for the user writing in English`() {
        val prompt = RefundAssistantContext.buildSystemPrompt(
            cruises = emptyList(),
            alerts = emptyList(),
            policyFor = { null },
            focusCruiseId = null,
            languageCode = TranslateLanguage.SPANISH,
            isAutomatedKickoff = true,
        )
        assertTrue(prompt.contains("Respond ONLY in Español"))
        assertTrue(prompt.contains("automated"))
    }

    @Test
    fun `a regular (non-kickoff) prompt does not include the automated caveat`() {
        val prompt = RefundAssistantContext.buildSystemPrompt(
            cruises = emptyList(),
            alerts = emptyList(),
            policyFor = { null },
            focusCruiseId = null,
            languageCode = TranslateLanguage.SPANISH,
        )
        assertTrue(!prompt.contains("automated"))
    }

    @Test
    fun `english selection adds no extra instruction`() {
        val prompt = RefundAssistantContext.buildSystemPrompt(
            cruises = emptyList(),
            alerts = emptyList(),
            policyFor = { null },
            focusCruiseId = null,
            languageCode = TranslateLanguage.ENGLISH,
        )
        assertTrue(!prompt.contains("Respond ONLY in"))
    }
}
