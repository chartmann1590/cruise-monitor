package com.cruisewatch.app.i18n

import android.content.Context
import org.json.JSONObject
import java.util.Locale

private const val PREFS_NAME = "cruisewatch_prefs"
private const val KEY_LANGUAGE = "selected_language"
private const val KEY_TRANSLATIONS_PREFIX = "translations_"

/** Persists the user's chosen UI language and per-language translated-string caches, in the app's existing shared prefs file. */
class LanguagePrefs(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSelectedLanguage(): String =
        prefs.getString(KEY_LANGUAGE, null) ?: defaultLanguageCode()

    fun setSelectedLanguage(code: String) {
        prefs.edit().putString(KEY_LANGUAGE, code).apply()
    }

    fun getCachedTranslations(code: String): Map<Int, String>? {
        val json = prefs.getString(KEY_TRANSLATIONS_PREFIX + code, null) ?: return null
        val obj = JSONObject(json)
        val result = mutableMapOf<Int, String>()
        obj.keys().forEach { key -> result[key.toInt()] = obj.getString(key) }
        return result
    }

    fun setCachedTranslations(code: String, translations: Map<Int, String>) {
        val obj = JSONObject()
        translations.forEach { (id, text) -> obj.put(id.toString(), text) }
        prefs.edit().putString(KEY_TRANSLATIONS_PREFIX + code, obj.toString()).apply()
    }

    /** Device locale's ISO 639-1 language, if ML Kit supports it; otherwise English. */
    fun defaultLanguageCode(): String {
        val deviceLanguage = Locale.getDefault().language
        return SupportedLanguages.byCode(deviceLanguage)?.code ?: SupportedLanguages.ENGLISH.code
    }
}
