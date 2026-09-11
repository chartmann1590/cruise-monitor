package com.cruisewatch.app.i18n

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
                result[id] = context.getString(id)
            }
        return result
    }
}

/** Marker so a translated value that lost a `%1$s`-style placeholder falls back to English rather than shipping a broken string. */
private fun isSafeTranslation(original: String, translated: String): Boolean {
    val placeholderPattern = Regex("%\\d\\\$[sd]")
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

    suspend fun restoreSavedLanguage() {
        selectLanguage(prefs.getSelectedLanguage())
    }

    suspend fun selectLanguage(code: String, allowCellular: Boolean = false) {
        val language = SupportedLanguages.byCode(code) ?: SupportedLanguages.ENGLISH
        val entries = catalog.allEntries()

        if (language.code == SupportedLanguages.ENGLISH.code) {
            prefs.setSelectedLanguage(language.code)
            _state.value = TranslationState.Ready(language, entries)
            return
        }

        prefs.getCachedTranslations(language.code)?.let { cached ->
            prefs.setSelectedLanguage(language.code)
            _state.value = TranslationState.Ready(language, cached)
            return
        }

        _state.value = TranslationState.Downloading(language)
        val translator = translatorFactory(language.code)
        try {
            translator.ensureModelDownloaded(allowCellular)
            _state.value = TranslationState.Translating(language)
            val translated = entries.mapValues { (_, english) ->
                val result = translator.translate(english)
                if (isSafeTranslation(english, result)) result else english
            }
            prefs.setCachedTranslations(language.code, translated)
            prefs.setSelectedLanguage(language.code)
            _state.value = TranslationState.Ready(language, translated)
        } catch (e: Exception) {
            _state.value = TranslationState.Failed(language, e.message ?: "Translation failed")
        } finally {
            translator.close()
        }
    }
}
