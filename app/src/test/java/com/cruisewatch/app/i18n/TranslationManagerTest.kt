package com.cruisewatch.app.i18n

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.mlkit.nl.translate.TranslateLanguage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    private class FailingTranslator : TranslatorClient {
        override suspend fun ensureModelDownloaded(allowCellular: Boolean) = throw IllegalStateException("no network")
        override suspend fun translate(text: String): String = text
        override fun close() {}
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

    @Test
    fun `the resource catalog covers this app's ui copy and nothing else`() {
        // R.string is the MERGED resource table, so the catalog filters by this app's own naming
        // families rather than translating every library string linked into the APK.
        listOf(
            "alerts_title", "language_picker_title", "nav_cruises", "cruises_empty_title",
            "settings_language_row", "onboarding_skip", "sign_in_email", "assistant_download_button",
            "claims_title", "price_history_title", "add_cruise_line", "phone_call_button",
            "price_drop_channel_name",
        ).forEach { assertTrue(it, ResourceStringCatalog.isTranslatableAppString(it)) }

        listOf(
            // Library-owned copy is never rendered through tr(), so translating it is pure waste.
            "abc_action_bar_home_description", "androidx_startup", "common_google_play_services_notification_channel_name",
            "status_bar_notification_info_overflow",
            // App-owned, but not translatable UI copy.
            "app_name", "price_drop_channel_id",
        ).forEach { assertFalse(it, ResourceStringCatalog.isTranslatableAppString(it)) }
    }

    @Test
    fun `restore with no saved choice resolves to english without touching the translator`() = runTest {
        val prefs = LanguagePrefs(context)
        val fake = FakeTranslator("SHOULD_NOT_APPEAR")
        val manager = TranslationManager(
            context = context,
            prefs = prefs,
            translatorFactory = { fake },
            catalog = FakeCatalog(mapOf(1 to "Hello")),
        )

        manager.restoreSavedLanguage()

        val state = manager.state.value as TranslationState.Ready
        assertEquals(SupportedLanguages.ENGLISH.code, state.language.code)
        assertEquals(0, fake.downloadCalls)
        // Nothing was chosen on the user's behalf, so the picker still has a decision to offer.
        assertNull(prefs.getSelectedLanguage())
    }

    @Test
    fun `restore applies a previously saved non-english choice`() = runTest {
        val prefs = LanguagePrefs(context)
        prefs.setSelectedLanguage(TranslateLanguage.SPANISH)
        val fake = FakeTranslator("ES")
        val manager = TranslationManager(
            context = context,
            prefs = prefs,
            translatorFactory = { fake },
            catalog = FakeCatalog(mapOf(1 to "Hello")),
        )

        manager.restoreSavedLanguage()

        val state = manager.state.value as TranslationState.Ready
        assertEquals(TranslateLanguage.SPANISH, state.language.code)
        assertEquals(1, fake.downloadCalls)
    }

    @Test
    fun `a cache built from a different string set is not reused`() = runTest {
        val prefs = LanguagePrefs(context)
        val first = FakeTranslator("DE")
        TranslationManager(
            context = context,
            prefs = prefs,
            translatorFactory = { first },
            catalog = FakeCatalog(mapOf(1 to "Hello")),
        ).selectLanguage(TranslateLanguage.GERMAN)
        assertEquals(1, first.downloadCalls)

        // Simulates an app update that edited the English copy behind key 1 and added key 2.
        val second = FakeTranslator("DE2")
        val updated = TranslationManager(
            context = context,
            prefs = prefs,
            translatorFactory = { second },
            catalog = FakeCatalog(mapOf(1 to "Hello there", 2 to "New copy")),
        )

        updated.selectLanguage(TranslateLanguage.GERMAN)

        assertEquals(1, second.downloadCalls)
        val state = updated.state.value as TranslationState.Ready
        assertEquals(mapOf(1 to "DE2:Hello there", 2 to "DE2:New copy"), state.strings)
    }

    @Test
    fun `a failed switch keeps the last successful language active for the app body`() = runTest {
        val prefs = LanguagePrefs(context)
        val manager = TranslationManager(
            context = context,
            prefs = prefs,
            translatorFactory = { code ->
                if (code == TranslateLanguage.SPANISH) FakeTranslator("ES") else FailingTranslator()
            },
            catalog = FakeCatalog(mapOf(1 to "Hello")),
        )

        manager.selectLanguage(TranslateLanguage.SPANISH)
        manager.selectLanguage(TranslateLanguage.FRENCH)

        assertTrue(manager.state.value is TranslationState.Failed)
        assertEquals(mapOf(1 to "ES:Hello"), manager.activeStrings.value)
    }

    @Test
    fun `a stale cached translation is shown immediately while a fingerprint-mismatched retranslate is in progress`() = runTest {
        val prefs = LanguagePrefs(context)
        // Simulates a cache written before an app update edited the English copy behind key 1.
        val staleFingerprint = fingerprintOf(mapOf(1 to "Hello"))
        prefs.setCachedTranslations(TranslateLanguage.SPANISH, mapOf(1 to "ES-STALE:Hello"), staleFingerprint)

        val gate = CompletableDeferred<Unit>()
        val slowTranslator = object : TranslatorClient {
            override suspend fun ensureModelDownloaded(allowCellular: Boolean) {}
            override suspend fun translate(text: String): String {
                gate.await()
                return "ES-FRESH:$text"
            }
            override fun close() {}
        }
        val manager = TranslationManager(
            context = context,
            prefs = prefs,
            translatorFactory = { slowTranslator },
            // Different copy than staleFingerprint was built from, so the fingerprinted cache
            // lookup misses and a retranslate is required.
            catalog = FakeCatalog(mapOf(1 to "Hello there")),
        )

        val job = launch { manager.selectLanguage(TranslateLanguage.SPANISH) }
        runCurrent() // Run the coroutine up to where it suspends inside translate() on the gate.

        // While the retranslate is in flight, the app body should keep showing the stale-but-known
        // translation rather than dropping to English.
        assertEquals(mapOf(1 to "ES-STALE:Hello"), manager.activeStrings.value)
        assertTrue(manager.state.value is TranslationState.Translating)

        gate.complete(Unit)
        job.join()

        // Once the retranslate succeeds, the fresh, fingerprint-matching map takes over.
        assertEquals(mapOf(1 to "ES-FRESH:Hello there"), manager.activeStrings.value)
        val state = manager.state.value
        assertTrue(state is TranslationState.Ready)
        assertEquals(mapOf(1 to "ES-FRESH:Hello there"), (state as TranslationState.Ready).strings)
    }

    @Test
    fun `a first-ever selection of a language does not fabricate a stale translation`() = runTest {
        val prefs = LanguagePrefs(context)
        // No cache exists for this language code at all.
        val gate = CompletableDeferred<Unit>()
        val slowTranslator = object : TranslatorClient {
            override suspend fun ensureModelDownloaded(allowCellular: Boolean) {}
            override suspend fun translate(text: String): String {
                gate.await()
                return "IT:$text"
            }
            override fun close() {}
        }
        val manager = TranslationManager(
            context = context,
            prefs = prefs,
            translatorFactory = { slowTranslator },
            catalog = FakeCatalog(mapOf(1 to "Hello")),
        )

        val job = launch { manager.selectLanguage(TranslateLanguage.ITALIAN) }
        runCurrent()

        // Nothing to seed from — activeStrings must stay exactly as it was before this call.
        assertEquals(emptyMap<Int, String>(), manager.activeStrings.value)
        assertTrue(manager.state.value is TranslationState.Translating)

        gate.complete(Unit)
        job.join()

        assertEquals(mapOf(1 to "IT:Hello"), manager.activeStrings.value)
    }

    @Test
    fun `switching from a working language does not show a different language's stale cache mid-flight`() = runTest {
        val prefs = LanguagePrefs(context)
        // French has a stale cached blob left over from before an app update — its fingerprint
        // won't match the current catalog.
        val staleFrenchFingerprint = fingerprintOf(mapOf(1 to "Old copy"))
        prefs.setCachedTranslations(TranslateLanguage.FRENCH, mapOf(1 to "FR-STALE:Old"), staleFrenchFingerprint)

        val gate = CompletableDeferred<Unit>()
        val manager = TranslationManager(
            context = context,
            prefs = prefs,
            translatorFactory = { code ->
                if (code == TranslateLanguage.SPANISH) {
                    FakeTranslator("ES")
                } else {
                    object : TranslatorClient {
                        override suspend fun ensureModelDownloaded(allowCellular: Boolean) {}
                        override suspend fun translate(text: String): String {
                            gate.await()
                            return "FR-FRESH:$text"
                        }
                        override fun close() {}
                    }
                }
            },
            catalog = FakeCatalog(mapOf(1 to "Hello")),
        )

        // The user already has a working, active language.
        manager.selectLanguage(TranslateLanguage.SPANISH)
        assertEquals(mapOf(1 to "ES:Hello"), manager.activeStrings.value)

        // Now they switch to French, whose cache is stale and must be retranslated.
        val job = launch { manager.selectLanguage(TranslateLanguage.FRENCH) }
        runCurrent()

        // While French is downloading/translating, the app body must keep showing the WORKING
        // Spanish translation — never French's stale (and possibly wrong-content) cache.
        assertEquals(mapOf(1 to "ES:Hello"), manager.activeStrings.value)

        gate.complete(Unit)
        job.join()

        // Once French succeeds, it takes over normally.
        assertEquals(mapOf(1 to "FR-FRESH:Hello"), manager.activeStrings.value)
    }

    @Test
    fun `a single entry's translate failure falls back to english without aborting the rest`() = runTest {
        val prefs = LanguagePrefs(context)
        val translator = object : TranslatorClient {
            override suspend fun ensureModelDownloaded(allowCellular: Boolean) {}
            override suspend fun translate(text: String): String {
                if (text == "Boom") throw IllegalStateException("ML Kit hiccup")
                return "ES:$text"
            }
            override fun close() {}
        }
        val manager = TranslationManager(
            context = context,
            prefs = prefs,
            translatorFactory = { translator },
            catalog = FakeCatalog(mapOf(1 to "Hello", 2 to "Boom", 3 to "Bye")),
        )

        manager.selectLanguage(TranslateLanguage.SPANISH)

        val state = manager.state.value as TranslationState.Ready
        assertEquals(mapOf(1 to "ES:Hello", 2 to "Boom", 3 to "ES:Bye"), state.strings)
    }

    @Test
    fun `hasValidCache is true for english and a fingerprint-matching cache, false otherwise`() = runTest {
        val prefs = LanguagePrefs(context)
        val manager = TranslationManager(
            context = context,
            prefs = prefs,
            translatorFactory = { FakeTranslator("ES") },
            catalog = FakeCatalog(mapOf(1 to "Hello")),
        )

        assertTrue(manager.hasValidCache(TranslateLanguage.ENGLISH))
        assertFalse(manager.hasValidCache(TranslateLanguage.SPANISH))

        manager.selectLanguage(TranslateLanguage.SPANISH)

        assertTrue(manager.hasValidCache(TranslateLanguage.SPANISH))
    }

    @Test
    fun `overlapping language selections are serialized`() = runTest {
        val prefs = LanguagePrefs(context)
        val manager = TranslationManager(
            context = context,
            prefs = prefs,
            translatorFactory = { code -> FakeTranslator(code.uppercase()) },
            catalog = FakeCatalog(mapOf(1 to "Hello")),
        )

        val first = launch { manager.selectLanguage(TranslateLanguage.SPANISH) }
        val second = launch { manager.selectLanguage(TranslateLanguage.FRENCH) }
        first.join()
        second.join()

        // Whichever ran second owns the final state; neither can leave a half-applied mix behind.
        val state = manager.state.value as TranslationState.Ready
        assertEquals(mapOf(1 to "${state.language.code.uppercase()}:Hello"), state.strings)
        assertEquals(state.language.code, prefs.getSelectedLanguage())
    }
}
