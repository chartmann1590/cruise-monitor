package com.cruisewatch.app.i18n

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.mlkit.nl.translate.TranslateLanguage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class LanguagePrefsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = LanguagePrefs(context)
    private val originalLocale: Locale = Locale.getDefault()

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `no language is selected until the user picks one`() {
        assertNull(prefs.getSelectedLanguage())
        assertFalse(prefs.hasSelectedLanguage())
    }

    @Test
    fun `device locale is never silently treated as the user's choice`() {
        Locale.setDefault(Locale("es"))

        assertNull(prefs.getSelectedLanguage())
        // defaultLanguageCode still reflects the locale — it's a picker hint, not an auto-applied choice.
        assertEquals(TranslateLanguage.SPANISH, prefs.defaultLanguageCode())
    }

    @Test
    fun `set and get selected language round-trips`() {
        prefs.setSelectedLanguage(TranslateLanguage.SPANISH)
        assertEquals(TranslateLanguage.SPANISH, prefs.getSelectedLanguage())
        assertTrue(prefs.hasSelectedLanguage())
    }

    @Test
    fun `cached translations round-trip as a map`() {
        val fingerprint = fingerprintOf(mapOf(101 to "Hello", 102 to "Goodbye"))
        assertNull(prefs.getCachedTranslations(TranslateLanguage.FRENCH, fingerprint))
        val map = mapOf(101 to "Bonjour", 102 to "Au revoir")
        prefs.setCachedTranslations(TranslateLanguage.FRENCH, map, fingerprint)
        assertEquals(map, prefs.getCachedTranslations(TranslateLanguage.FRENCH, fingerprint))
    }

    @Test
    fun `cached translations are ignored when the fingerprint no longer matches`() {
        val oldFingerprint = fingerprintOf(mapOf(101 to "Hello"))
        prefs.setCachedTranslations(TranslateLanguage.FRENCH, mapOf(101 to "Bonjour"), oldFingerprint)

        val newFingerprint = fingerprintOf(mapOf(101 to "Hello there"))
        assertNotEquals(oldFingerprint, newFingerprint)
        assertNull(prefs.getCachedTranslations(TranslateLanguage.FRENCH, newFingerprint))
    }

    @Test
    fun `fingerprint is stable regardless of map iteration order`() {
        val a = fingerprintOf(linkedMapOf(1 to "Hello", 2 to "Bye"))
        val b = fingerprintOf(linkedMapOf(2 to "Bye", 1 to "Hello"))
        assertEquals(a, b)
    }
}
