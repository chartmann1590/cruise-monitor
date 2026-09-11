package com.cruisewatch.app.i18n

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.mlkit.nl.translate.TranslateLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LanguagePrefsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = LanguagePrefs(context)

    @Test
    fun `selected language defaults to english`() {
        assertEquals(TranslateLanguage.ENGLISH, prefs.getSelectedLanguage())
    }

    @Test
    fun `set and get selected language round-trips`() {
        prefs.setSelectedLanguage(TranslateLanguage.SPANISH)
        assertEquals(TranslateLanguage.SPANISH, prefs.getSelectedLanguage())
    }

    @Test
    fun `cached translations round-trip as a map`() {
        assertNull(prefs.getCachedTranslations(TranslateLanguage.FRENCH))
        val map = mapOf(101 to "Bonjour", 102 to "Au revoir")
        prefs.setCachedTranslations(TranslateLanguage.FRENCH, map)
        assertEquals(map, prefs.getCachedTranslations(TranslateLanguage.FRENCH))
    }
}
