package com.cruisewatch.app.i18n

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

private const val PREFS_NAME = "cruisewatch_prefs"
private const val KEY_LANGUAGE = "selected_language"
private const val KEY_TRANSLATIONS_PREFIX = "translations_"
private const val KEY_TRANSLATIONS_FINGERPRINT_PREFIX = "translations_fingerprint_"

/** Persists the user's chosen UI language and per-language translated-string caches, in the app's existing shared prefs file. */
class LanguagePrefs(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * The language the user explicitly chose, or `null` if they never chose one.
     *
     * Deliberately does NOT fall back to [defaultLanguageCode]: auto-applying the device locale
     * would kick off a model download the user never consented to, before the language picker
     * has even rendered.
     */
    fun getSelectedLanguage(): String? = prefs.getString(KEY_LANGUAGE, null)

    /** True once the user has made an explicit language choice. */
    fun hasSelectedLanguage(): Boolean = getSelectedLanguage() != null

    fun setSelectedLanguage(code: String) {
        prefs.edit().putString(KEY_LANGUAGE, code).apply()
    }

    /**
     * Cached translations for [code], but only if they were built from the same string set as
     * [fingerprint]. A mismatch (e.g. an app update edited or added English copy) reads as a
     * cache miss so the caller retranslates instead of showing stale copy forever.
     */
    fun getCachedTranslations(code: String, fingerprint: String): Map<Int, String>? {
        val cachedFingerprint = prefs.getString(KEY_TRANSLATIONS_FINGERPRINT_PREFIX + code, null)
        if (cachedFingerprint != fingerprint) return null
        val json = prefs.getString(KEY_TRANSLATIONS_PREFIX + code, null) ?: return null
        val obj = JSONObject(json)
        val result = mutableMapOf<Int, String>()
        obj.keys().forEach { key -> result[key.toInt()] = obj.getString(key) }
        return result
    }

    /**
     * Cached translations for [code] regardless of fingerprint — including a stale cache built
     * from an older string set.
     *
     * Only for seeding a "last known good" UI while a fingerprint-mismatched retranslate is in
     * flight; never use this to decide whether a retranslate is needed (use
     * [getCachedTranslations] for that).
     */
    fun getStaleCachedTranslations(code: String): Map<Int, String>? {
        val json = prefs.getString(KEY_TRANSLATIONS_PREFIX + code, null) ?: return null
        val obj = JSONObject(json)
        val result = mutableMapOf<Int, String>()
        obj.keys().forEach { key -> result[key.toInt()] = obj.getString(key) }
        return result
    }

    fun setCachedTranslations(code: String, translations: Map<Int, String>, fingerprint: String) {
        val obj = JSONObject()
        translations.forEach { (id, text) -> obj.put(id.toString(), text) }
        prefs.edit()
            .putString(KEY_TRANSLATIONS_PREFIX + code, obj.toString())
            .putString(KEY_TRANSLATIONS_FINGERPRINT_PREFIX + code, fingerprint)
            .apply()
    }

    /**
     * Device locale's ISO 639-1 language, if ML Kit supports it; otherwise English.
     *
     * Only for pre-highlighting a suggestion in the picker — never for auto-applying a language.
     */
    fun defaultLanguageCode(): String {
        val deviceLanguage = Locale.getDefault().language
        return SupportedLanguages.byCode(deviceLanguage)?.code ?: SupportedLanguages.ENGLISH.code
    }
}

/**
 * Stable content hash of a string catalog, used to invalidate translation caches whenever the
 * English copy they were derived from changes.
 */
fun fingerprintOf(entries: Map<Int, String>): String {
    val canonical = entries.entries
        .sortedBy { it.key }
        .joinToString(separator = "||") { "${it.key}=${it.value}" }
    val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}
