package com.cruisewatch.app.i18n

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.lang.reflect.Modifier as ReflectModifier

sealed interface TranslationState {
    data object Idle : TranslationState
    data class Downloading(val language: SupportedLanguage) : TranslationState
    data class Translating(val language: SupportedLanguage) : TranslationState
    data class Ready(val language: SupportedLanguage, val strings: Map<Int, String>) : TranslationState
    data class Failed(val language: SupportedLanguage, val message: String) : TranslationState
}

/** Seam over ML Kit's Translator so tests can fake translation without Google Play Services. */
interface TranslatorClient {
    suspend fun ensureModelDownloaded(allowCellular: Boolean)
    suspend fun translate(text: String): String
    fun close()
}

class MlKitTranslatorClient(targetLanguage: String) : TranslatorClient {
    private val translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(targetLanguage)
            .build(),
    )

    override suspend fun ensureModelDownloaded(allowCellular: Boolean) {
        val conditions = DownloadConditions.Builder().apply {
            if (!allowCellular) requireWifi()
        }.build()
        translator.downloadModelIfNeeded(conditions).await()
    }

    override suspend fun translate(text: String): String = translator.translate(text).await()

    override fun close() = translator.close()
}

/** Reflects over generated `R.string` fields to build the full set of translatable keys. */
interface StringCatalog {
    fun allEntries(): Map<Int, String>
}

class ResourceStringCatalog(private val context: Context) : StringCatalog {

    override fun allEntries(): Map<Int, String> {
        val stringClass = Class.forName("${context.packageName}.R\$string")
        val result = mutableMapOf<Int, String>()
        stringClass.fields
            .filter { ReflectModifier.isStatic(it.modifiers) && it.type == Int::class.javaPrimitiveType }
            .forEach { field ->
                val id = field.getInt(null)
                val name = runCatching { context.resources.getResourceEntryName(id) }.getOrNull() ?: return@forEach
                if (!isTranslatableAppString(name)) return@forEach
                result[id] = context.getString(id)
            }
        return result
    }

    companion object {
        /**
         * `R.string` is the MERGED resource table — it also carries AppCompat/Material/Play Services
         * copy (`abc_*`, `androidx_*`, `common_google_play_services_*`, …) that this app never looks
         * up through [tr]. Translating those roughly 2.5x'd the ML Kit call count for no benefit, so
         * restrict the catalog to this app's own `<screen>_<slug>` naming families.
         */
        private val APP_STRING_PREFIXES = listOf(
            "add_cruise_",
            "alerts_",
            "assistant_",
            "claims_",
            "cruises_",
            "language_picker_",
            "nav_",
            "onboarding_",
            "phone_",
            "price_drop_channel_",
            "price_history_",
            "settings_",
            "sign_in_",
        )

        /** App-owned resources that are not translatable UI copy. */
        private val NON_UI_STRING_NAMES = setOf(
            "app_name",
            "price_drop_channel_id",
        )

        fun isTranslatableAppString(name: String): Boolean =
            name !in NON_UI_STRING_NAMES && APP_STRING_PREFIXES.any { name.startsWith(it) }
    }
}

/** Marker so a translated value that lost a `%1$s`-style placeholder falls back to English rather than shipping a broken string. */
internal fun isSafeTranslation(original: String, translated: String): Boolean {
    // Deliberately wider than the `%N$[sd]` forms currently in strings.xml, so a stray `%` the
    // translator invents anywhere in the output is caught as a placeholder mismatch.
    val placeholderPattern = Regex("%(?:\\d+\\\$)?[a-zA-Z%]")
    val originalPlaceholders = placeholderPattern.findAll(original).map { it.value }.toList()
    val translatedPlaceholders = placeholderPattern.findAll(translated).map { it.value }.toList()
    return originalPlaceholders.toSet() == translatedPlaceholders.toSet()
}

class TranslationManager(
    private val context: Context,
    private val prefs: LanguagePrefs = LanguagePrefs(context),
    private val translatorFactory: (String) -> TranslatorClient = { MlKitTranslatorClient(it) },
    private val catalog: StringCatalog = ResourceStringCatalog(context),
) {
    private val _state = MutableStateFlow<TranslationState>(TranslationState.Idle)
    val state: StateFlow<TranslationState> = _state.asStateFlow()

    /**
     * The strings the app body should render right now: the last successfully applied language.
     *
     * Unlike [state], this does NOT collapse to English while a switch is downloading, translating,
     * or after one fails — a failed switch to French must not silently drop a working Spanish UI
     * back to English for the rest of the process.
     */
    private val _activeStrings = MutableStateFlow<Map<Int, String>>(emptyMap())
    val activeStrings: StateFlow<Map<Int, String>> = _activeStrings.asStateFlow()

    /** Serializes [selectLanguage] so two overlapping calls can't interleave and let a stale one win. */
    private val mutex = Mutex()

    /**
     * Whether selecting [code] right now would apply instantly with no ML Kit call — English
     * always qualifies; any other language needs a fingerprint-matching cache. Used by the picker
     * to decide whether a Wi-Fi-required download is actually about to happen before prompting for
     * cellular permission.
     */
    fun hasValidCache(code: String): Boolean {
        if (code == SupportedLanguages.ENGLISH.code) return true
        val fingerprint = fingerprintOf(catalog.allEntries())
        return prefs.getCachedTranslations(code, fingerprint) != null
    }

    /**
     * Applies the user's previously chosen language, if any.
     *
     * If they never chose one, resolve straight to English WITHOUT touching ML Kit — auto-applying
     * the device locale here would start an unconsented model download (and, on a cellular-only
     * device, hang forever behind `requireWifi()`) before the picker ever renders.
     */
    suspend fun restoreSavedLanguage() {
        val saved = prefs.getSelectedLanguage()
        if (saved == null) {
            setReady(SupportedLanguages.ENGLISH, catalog.allEntries())
            return
        }
        selectLanguage(saved)
    }

    suspend fun selectLanguage(code: String, allowCellular: Boolean = false) = mutex.withLock {
        val language = SupportedLanguages.byCode(code) ?: SupportedLanguages.ENGLISH
        val entries = catalog.allEntries()

        if (language.code == SupportedLanguages.ENGLISH.code) {
            prefs.setSelectedLanguage(language.code)
            setReady(language, entries)
            return@withLock
        }

        val fingerprint = fingerprintOf(entries)
        prefs.getCachedTranslations(language.code, fingerprint)?.let { cached ->
            prefs.setSelectedLanguage(language.code)
            setReady(language, cached)
            return@withLock
        }

        // The fingerprinted lookup above missed — either there's no cache at all, or there's a
        // stale one from before the last content change. Only seed `_activeStrings` from it when
        // nothing is currently active (a cold launch, where the alternative is flashing English
        // for the retranslate's duration) — never when the user already has a WORKING language on
        // screen and is switching to a different one, since that stale blob belongs to the NEW
        // language and would replace a good UI with stale/wrong content if this switch then fails.
        // `setReady` below replaces this with the fresh, fingerprint-matching map on success; on
        // failure `_activeStrings` is simply left as whatever it already was.
        if (_activeStrings.value.isEmpty()) {
            prefs.getStaleCachedTranslations(language.code)?.let { stale ->
                _activeStrings.value = stale
            }
        }

        _state.value = TranslationState.Downloading(language)
        val translator = translatorFactory(language.code)
        try {
            translator.ensureModelDownloaded(allowCellular)
            _state.value = TranslationState.Translating(language)
            // Each entry falls back to English independently on its own translate failure, the
            // same as an unsafe translation does below — one bad/failing string must not abort
            // every other label in the language.
            val translated = entries.mapValues { (_, english) ->
                val result = try {
                    translator.translate(english)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                if (result != null && isSafeTranslation(english, result)) result else english
            }
            prefs.setCachedTranslations(language.code, translated, fingerprint)
            prefs.setSelectedLanguage(language.code)
            setReady(language, translated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = TranslationState.Failed(language, e.message ?: "Translation failed")
        } finally {
            translator.close()
        }
    }

    private fun setReady(language: SupportedLanguage, strings: Map<Int, String>) {
        _activeStrings.value = strings
        _state.value = TranslationState.Ready(language, strings)
    }
}
