package com.cruisewatch.app.i18n

import com.google.mlkit.nl.translate.TranslateLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedLanguagesTest {

    @Test
    fun `every language code is a valid ML Kit TranslateLanguage constant`() {
        val validCodes = TranslateLanguage.getAllLanguages().toSet()
        SupportedLanguages.ALL.forEach { lang ->
            assertTrue("${lang.code} is not a valid ML Kit language", validCodes.contains(lang.code))
        }
    }

    @Test
    fun `has exactly 59 languages with no duplicates`() {
        assertEquals(59, SupportedLanguages.ALL.size)
        assertEquals(59, SupportedLanguages.ALL.map { it.code }.toSet().size)
    }

    @Test
    fun `english is first and byCode resolves it`() {
        assertEquals(TranslateLanguage.ENGLISH, SupportedLanguages.ALL.first().code)
        assertEquals(SupportedLanguages.ENGLISH, SupportedLanguages.byCode(TranslateLanguage.ENGLISH))
        assertNotNull(SupportedLanguages.byCode(TranslateLanguage.SPANISH))
    }

    @Test
    fun `byCode returns null for unknown code`() {
        assertEquals(null, SupportedLanguages.byCode("xx-not-real"))
    }
}
