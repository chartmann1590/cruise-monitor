package com.cruisewatch.app.i18n

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.mlkit.nl.translate.TranslateLanguage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TranslationManagerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private class FakeTranslator(private val prefix: String) : TranslatorClient {
        var downloadCalls = 0
        override suspend fun ensureModelDownloaded(allowCellular: Boolean) { downloadCalls++ }
        override suspend fun translate(text: String): String = "$prefix:$text"
        override fun close() {}
    }

    private class FakeCatalog(private val entries: Map<Int, String>) : StringCatalog {
        override fun allEntries(): Map<Int, String> = entries
    }

    @Test
    fun `selecting a language downloads once, translates every key, and reaches Ready`() = runTest {
        val prefs = LanguagePrefs(context)
        val fake = FakeTranslator("ES")
        val manager = TranslationManager(
            context = context,
            prefs = prefs,
            translatorFactory = { fake },
            catalog = FakeCatalog(mapOf(1 to "Hello", 2 to "Bye")),
        )

        manager.selectLanguage(TranslateLanguage.SPANISH)

        val state = manager.state.value
        assertTrue(state is TranslationState.Ready)
        state as TranslationState.Ready
        assertEquals(mapOf(1 to "ES:Hello", 2 to "ES:Bye"), state.strings)
        assertEquals(1, fake.downloadCalls)
        assertEquals(TranslateLanguage.SPANISH, prefs.getSelectedLanguage())
    }

    @Test
    fun `second selection of the same language uses the cache and does not re-download`() = runTest {
        val prefs = LanguagePrefs(context)
        val fake = FakeTranslator("FR")
        val manager = TranslationManager(
            context = context,
            prefs = prefs,
            translatorFactory = { fake },
            catalog = FakeCatalog(mapOf(1 to "Hi")),
        )

        manager.selectLanguage(TranslateLanguage.FRENCH)
        manager.selectLanguage(TranslateLanguage.FRENCH)

        assertEquals(1, fake.downloadCalls)
    }

    @Test
    fun `selecting english never calls the translator`() = runTest {
        val prefs = LanguagePrefs(context)
        val fake = FakeTranslator("SHOULD_NOT_APPEAR")
        val manager = TranslationManager(
            context = context,
            prefs = prefs,
            translatorFactory = { fake },
            catalog = FakeCatalog(mapOf(1 to "Hello")),
        )

        manager.selectLanguage(TranslateLanguage.ENGLISH)

        val state = manager.state.value as TranslationState.Ready
        assertEquals(mapOf(1 to "Hello"), state.strings)
        assertEquals(0, fake.downloadCalls)
    }
}
