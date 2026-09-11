# On-device UI Translation (ML Kit) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user pick a native language during onboarding (and change it later from a new Settings screen), translating every static UI string in the phone app via on-device Google ML Kit Translate, and steer the on-device AI assistant to respond in that language.

**Architecture:** A `TranslationManager` singleton drives ML Kit's `Translator`: on language selection it downloads the language model (Wi-Fi by default), translates every key in `strings.xml` into a `Map<Int, String>`, caches that map to `SharedPreferences` as JSON, and publishes it through a `CompositionLocal` (`LocalStrings`) that a `tr(R.string.id)` composable helper reads. All ~104 hardcoded `Text("...")` literals move into `strings.xml` and call sites switch to `tr(...)`. The AI assistant's system prompt gets one extra instruction line naming the selected language.

**Tech Stack:** Kotlin, Jetpack Compose, `com.google.mlkit:translate`, existing `SharedPreferences`-based persistence (no new DI/persistence framework).

**Spec:** `docs/superpowers/specs/2026-09-11-on-device-translation-design.md`

## Global Constraints

- Scope is the phone app (`app/` module) only. `wear/` and the home-screen widget (`app/.../widget/`) are NOT touched — English-only, per spec Non-goals.
- No new DI framework (no Hilt/Dagger) and no DataStore — use plain `SharedPreferences` under the existing `cruisewatch_prefs` file, matching `CruiseWatchNavHost.kt` and `AssistantViewModel.kt`.
- Source-of-truth UI copy is English, authored in `app/src/main/res/values/strings.xml`. Translation direction is always English → target language.
- Model downloads default to Wi-Fi-only (`DownloadConditions().requireWifi()`); cellular is opt-in per download.
- Dynamic data (cruise/ship names, prices, dates) is never translated — only static UI copy moves to `strings.xml`.
- Every screen file must end each task with zero remaining hardcoded English `Text("...")` literals for translatable copy (verified via grep in that task's steps).

---

### Task 1: Add ML Kit Translate dependency

**Files:**
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Produces: `com.google.mlkit.nl.translate.*` classes available to the rest of the app.

- [ ] **Step 1: Add the dependency**

In `app/build.gradle.kts`, inside the `dependencies { ... }` block, add (near the other Google/ML dependency, after the `play-services-wearable` line):

```kotlin
    implementation("com.google.mlkit:translate:17.0.3")
```

- [ ] **Step 2: Sync and build**

Run: `./gradlew :app:assembleDebug` (Windows: `gradlew.bat :app:assembleDebug`)
Expected: BUILD SUCCESSFUL (no source changes yet, this only proves the dependency resolves).

- [ ] **Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "Add ML Kit Translate dependency"
```

---

### Task 2: Supported language catalog

**Files:**
- Create: `app/src/main/java/com/cruisewatch/app/i18n/SupportedLanguages.kt`
- Test: `app/src/test/java/com/cruisewatch/app/i18n/SupportedLanguagesTest.kt`

**Interfaces:**
- Produces: `data class SupportedLanguage(val code: String, val englishName: String, val nativeName: String)`, `object SupportedLanguages { val ALL: List<SupportedLanguage>; val ENGLISH: SupportedLanguage; fun byCode(code: String): SupportedLanguage? }`

- [ ] **Step 1: Write the failing test**

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.cruisewatch.app.i18n.SupportedLanguagesTest"`
Expected: FAIL — `SupportedLanguages` unresolved reference.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.cruisewatch.app.i18n

import com.google.mlkit.nl.translate.TranslateLanguage

/** One entry in the language picker. [nativeName] is shown to the user; [englishName] is shown as a secondary hint. */
data class SupportedLanguage(
    val code: String,
    val englishName: String,
    val nativeName: String,
)

/** All 59 languages ML Kit Translate supports on-device, for free. English is first and needs no model download. */
object SupportedLanguages {

    val ALL: List<SupportedLanguage> = listOf(
        SupportedLanguage(TranslateLanguage.ENGLISH, "English", "English"),
        SupportedLanguage(TranslateLanguage.AFRIKAANS, "Afrikaans", "Afrikaans"),
        SupportedLanguage(TranslateLanguage.ALBANIAN, "Albanian", "Shqip"),
        SupportedLanguage(TranslateLanguage.ARABIC, "Arabic", "العربية"),
        SupportedLanguage(TranslateLanguage.BELARUSIAN, "Belarusian", "Беларуская"),
        SupportedLanguage(TranslateLanguage.BENGALI, "Bengali", "বাংলা"),
        SupportedLanguage(TranslateLanguage.BULGARIAN, "Bulgarian", "Български"),
        SupportedLanguage(TranslateLanguage.CATALAN, "Catalan", "Català"),
        SupportedLanguage(TranslateLanguage.CHINESE, "Chinese", "中文"),
        SupportedLanguage(TranslateLanguage.CROATIAN, "Croatian", "Hrvatski"),
        SupportedLanguage(TranslateLanguage.CZECH, "Czech", "Čeština"),
        SupportedLanguage(TranslateLanguage.DANISH, "Danish", "Dansk"),
        SupportedLanguage(TranslateLanguage.DUTCH, "Dutch", "Nederlands"),
        SupportedLanguage(TranslateLanguage.ESPERANTO, "Esperanto", "Esperanto"),
        SupportedLanguage(TranslateLanguage.ESTONIAN, "Estonian", "Eesti"),
        SupportedLanguage(TranslateLanguage.FINNISH, "Finnish", "Suomi"),
        SupportedLanguage(TranslateLanguage.FRENCH, "French", "Français"),
        SupportedLanguage(TranslateLanguage.GALICIAN, "Galician", "Galego"),
        SupportedLanguage(TranslateLanguage.GEORGIAN, "Georgian", "ქართული"),
        SupportedLanguage(TranslateLanguage.GERMAN, "German", "Deutsch"),
        SupportedLanguage(TranslateLanguage.GREEK, "Greek", "Ελληνικά"),
        SupportedLanguage(TranslateLanguage.GUJARATI, "Gujarati", "ગુજરાતી"),
        SupportedLanguage(TranslateLanguage.HAITIAN_CREOLE, "Haitian Creole", "Kreyòl Ayisyen"),
        SupportedLanguage(TranslateLanguage.HEBREW, "Hebrew", "עברית"),
        SupportedLanguage(TranslateLanguage.HINDI, "Hindi", "हिन्दी"),
        SupportedLanguage(TranslateLanguage.HUNGARIAN, "Hungarian", "Magyar"),
        SupportedLanguage(TranslateLanguage.ICELANDIC, "Icelandic", "Íslenska"),
        SupportedLanguage(TranslateLanguage.INDONESIAN, "Indonesian", "Bahasa Indonesia"),
        SupportedLanguage(TranslateLanguage.IRISH, "Irish", "Gaeilge"),
        SupportedLanguage(TranslateLanguage.ITALIAN, "Italian", "Italiano"),
        SupportedLanguage(TranslateLanguage.JAPANESE, "Japanese", "日本語"),
        SupportedLanguage(TranslateLanguage.KANNADA, "Kannada", "ಕನ್ನಡ"),
        SupportedLanguage(TranslateLanguage.KOREAN, "Korean", "한국어"),
        SupportedLanguage(TranslateLanguage.LATVIAN, "Latvian", "Latviešu"),
        SupportedLanguage(TranslateLanguage.LITHUANIAN, "Lithuanian", "Lietuvių"),
        SupportedLanguage(TranslateLanguage.MACEDONIAN, "Macedonian", "Македонски"),
        SupportedLanguage(TranslateLanguage.MALAY, "Malay", "Bahasa Melayu"),
        SupportedLanguage(TranslateLanguage.MALTESE, "Maltese", "Malti"),
        SupportedLanguage(TranslateLanguage.MARATHI, "Marathi", "मराठी"),
        SupportedLanguage(TranslateLanguage.NORWEGIAN, "Norwegian", "Norsk"),
        SupportedLanguage(TranslateLanguage.PERSIAN, "Persian", "فارسی"),
        SupportedLanguage(TranslateLanguage.POLISH, "Polish", "Polski"),
        SupportedLanguage(TranslateLanguage.PORTUGUESE, "Portuguese", "Português"),
        SupportedLanguage(TranslateLanguage.ROMANIAN, "Romanian", "Română"),
        SupportedLanguage(TranslateLanguage.RUSSIAN, "Russian", "Русский"),
        SupportedLanguage(TranslateLanguage.SLOVAK, "Slovak", "Slovenčina"),
        SupportedLanguage(TranslateLanguage.SLOVENIAN, "Slovenian", "Slovenščina"),
        SupportedLanguage(TranslateLanguage.SPANISH, "Spanish", "Español"),
        SupportedLanguage(TranslateLanguage.SWAHILI, "Swahili", "Kiswahili"),
        SupportedLanguage(TranslateLanguage.SWEDISH, "Swedish", "Svenska"),
        SupportedLanguage(TranslateLanguage.TAGALOG, "Tagalog", "Tagalog"),
        SupportedLanguage(TranslateLanguage.TAMIL, "Tamil", "தமிழ்"),
        SupportedLanguage(TranslateLanguage.TELUGU, "Telugu", "తెలుగు"),
        SupportedLanguage(TranslateLanguage.THAI, "Thai", "ไทย"),
        SupportedLanguage(TranslateLanguage.TURKISH, "Turkish", "Türkçe"),
        SupportedLanguage(TranslateLanguage.UKRAINIAN, "Ukrainian", "Українська"),
        SupportedLanguage(TranslateLanguage.URDU, "Urdu", "اردو"),
        SupportedLanguage(TranslateLanguage.VIETNAMESE, "Vietnamese", "Tiếng Việt"),
        SupportedLanguage(TranslateLanguage.WELSH, "Welsh", "Cymraeg"),
    )

    val ENGLISH: SupportedLanguage = ALL.first()

    private val byCode: Map<String, SupportedLanguage> = ALL.associateBy { it.code }

    fun byCode(code: String): SupportedLanguage? = byCode[code]
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.cruisewatch.app.i18n.SupportedLanguagesTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cruisewatch/app/i18n/SupportedLanguages.kt app/src/test/java/com/cruisewatch/app/i18n/SupportedLanguagesTest.kt
git commit -m "Add ML Kit supported language catalog"
```

---

### Task 3: Language + translation-cache preference storage

**Files:**
- Create: `app/src/main/java/com/cruisewatch/app/i18n/LanguagePrefs.kt`
- Test: `app/src/test/java/com/cruisewatch/app/i18n/LanguagePrefsTest.kt`

**Interfaces:**
- Consumes: `android.content.SharedPreferences` (Robolectric-backed `Context.getSharedPreferences` in tests).
- Produces: `class LanguagePrefs(context: Context)` with `fun getSelectedLanguage(): String`, `fun setSelectedLanguage(code: String)`, `fun getCachedTranslations(code: String): Map<Int, String>?`, `fun setCachedTranslations(code: String, translations: Map<Int, String>)`, `fun defaultLanguageCode(): String` (device locale if supported, else `"en"`).

- [ ] **Step 1: Write the failing test**

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.cruisewatch.app.i18n.LanguagePrefsTest"`
Expected: FAIL — `LanguagePrefs` unresolved reference.

- [ ] **Step 3: Write the implementation**

```kotlin
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.cruisewatch.app.i18n.LanguagePrefsTest"`
Expected: PASS (3 tests). If Robolectric is not yet a test dependency, add `testImplementation("org.robolectric:robolectric:4.13")` and `testImplementation("androidx.test:core:1.6.1")` to `app/build.gradle.kts` first.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cruisewatch/app/i18n/LanguagePrefs.kt app/src/test/java/com/cruisewatch/app/i18n/LanguagePrefsTest.kt app/build.gradle.kts
git commit -m "Add language + translation-cache preference storage"
```

---

### Task 4: TranslationManager core

**Files:**
- Create: `app/src/main/java/com/cruisewatch/app/i18n/TranslationManager.kt`
- Test: `app/src/test/java/com/cruisewatch/app/i18n/TranslationManagerTest.kt`

**Interfaces:**
- Consumes: `LanguagePrefs` (Task 3), `SupportedLanguages` (Task 2).
- Produces:
  - `sealed interface TranslationState { data object Idle : TranslationState; data class Downloading(val language: SupportedLanguage) : TranslationState; data class Translating(val language: SupportedLanguage) : TranslationState; data class Ready(val language: SupportedLanguage, val strings: Map<Int, String>) : TranslationState; data class Failed(val language: SupportedLanguage, val message: String) : TranslationState }`
  - `class TranslationManager(private val context: Context, private val prefs: LanguagePrefs = LanguagePrefs(context), private val translatorFactory: (String) -> TranslatorClient = ::MlKitTranslatorClient)` with `val state: StateFlow<TranslationState>` and `suspend fun selectLanguage(code: String, allowCellular: Boolean = false)`.
  - `interface TranslatorClient { suspend fun ensureModelDownloaded(allowCellular: Boolean); suspend fun translate(text: String): String; fun close() }` — the seam that lets the test fake ML Kit instead of hitting Google Play Services.
  - Reads all translatable keys via a small injectable `StringCatalog` (`interface StringCatalog { fun allEntries(): Map<Int, String> }`, default impl reflects over `R.string` fields at runtime using `context.resources`).

- [ ] **Step 1: Write the failing test**

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.cruisewatch.app.i18n.TranslationManagerTest"`
Expected: FAIL — `TranslationManager`, `TranslatorClient`, `StringCatalog`, `TranslationState` unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.cruisewatch.app.i18n.TranslationManagerTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cruisewatch/app/i18n/TranslationManager.kt app/src/test/java/com/cruisewatch/app/i18n/TranslationManagerTest.kt
git commit -m "Add TranslationManager: model download, translate pass, caching"
```

---

### Task 5: `LocalStrings` composition local and `tr()` helper

**Files:**
- Create: `app/src/main/java/com/cruisewatch/app/i18n/LocalStrings.kt`
- Modify: `app/src/main/java/com/cruisewatch/app/ui/CruiseWatchNavHost.kt`

**Interfaces:**
- Consumes: `TranslationManager.state` (Task 4).
- Produces: `val LocalStrings: ProvidableCompositionLocal<Map<Int, String>>`, `@Composable fun tr(@StringRes id: Int): String`, `@Composable fun tr(@StringRes id: Int, vararg args: Any): String` (format-string variant), `@Composable fun ProvideTranslations(manager: TranslationManager, content: @Composable () -> Unit)`.

- [ ] **Step 1: Write `LocalStrings.kt`**

```kotlin
package com.cruisewatch.app.i18n

import androidx.annotation.StringRes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

/** Current language's translated strings, keyed by string-resource id. Falls back to the English resource for any missing key. */
val LocalStrings: ProvidableCompositionLocal<Map<Int, String>> = compositionLocalOf { emptyMap() }

/** Looks up the translated copy of [id] in the active language, falling back to the English resource string. */
@Composable
fun tr(@StringRes id: Int): String {
    val translated = LocalStrings.current[id]
    return translated ?: stringResource(id)
}

/** Format-string variant, for copy like "Downloading %1$s…" — args are substituted into whichever language's template is active. */
@Composable
fun tr(@StringRes id: Int, vararg args: Any): String {
    val context = LocalContext.current
    val template = LocalStrings.current[id] ?: context.getString(id)
    return String.format(template, *args)
}

/** Provides [LocalStrings] from the given [manager]'s current state; screens below this render in whatever language is active. */
@Composable
fun ProvideTranslations(manager: TranslationManager, content: @Composable () -> Unit) {
    val state by manager.state.collectAsState()
    val strings = (state as? TranslationState.Ready)?.strings ?: emptyMap()
    CompositionLocalProvider(LocalStrings provides strings, content = content)
}
```

- [ ] **Step 2: Wire it into `CruiseWatchNavHost.kt`**

In `app/src/main/java/com/cruisewatch/app/ui/CruiseWatchNavHost.kt`, add the import `import com.cruisewatch.app.i18n.ProvideTranslations` and `import com.cruisewatch.app.i18n.TranslationManager`, then inside `CruiseWatchNavHost` (right after the existing `val context = LocalContext.current` on line 81) add:

```kotlin
    val translationManager = remember { TranslationManager(context) }
    androidx.compose.runtime.LaunchedEffect(Unit) { translationManager.restoreSavedLanguage() }
```

Then wrap the function's entire existing body (from the `androidx.compose.runtime.LaunchedEffect(isSignedIn)` block through the end of the `Scaffold` call) in `ProvideTranslations(translationManager) { ... }` so every screen below it can call `tr()`. `translationManager` is also threaded through to `OnboardingScreen` (Task 15) and the new Settings screen (Task 16), so keep the `remember { TranslationManager(context) }` instance — don't create a second one.

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. No behavior change yet — `LocalStrings` is empty until a screen calls `tr()`, and every existing `Text("literal")` call still compiles as-is.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/cruisewatch/app/i18n/LocalStrings.kt app/src/main/java/com/cruisewatch/app/ui/CruiseWatchNavHost.kt
git commit -m "Add LocalStrings/tr() lookup and wire TranslationManager into the nav host"
```

---

### Task 6: Retrofit `TrackedCruisesScreen.kt` and bottom nav labels

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/com/cruisewatch/app/ui/screens/TrackedCruisesScreen.kt`
- Modify: `app/src/main/java/com/cruisewatch/app/ui/CruiseWatchNavHost.kt`

**Interfaces:**
- Consumes: `tr()` from Task 5.

This is the pattern every later per-screen task repeats: move each hardcoded literal into `strings.xml` with a key named `<screen>_<slug>`, replace the call site with `tr(R.string.<key>)`, leave non-copy content (prices, IDs, emoji) untouched.

- [ ] **Step 1: Add string resources**

In `app/src/main/res/values/strings.xml`, add inside `<resources>`:

```xml
    <string name="nav_cruises">Cruises</string>
    <string name="nav_alerts">Alerts</string>
    <string name="nav_policies">Policies</string>
    <string name="nav_assistant">Assistant</string>
    <string name="cruises_title">Your Cruises</string>
    <string name="cruises_add_cruise">Add cruise</string>
```

- [ ] **Step 2: Update `TrackedCruisesScreen.kt`**

Add `import com.cruisewatch.app.R` and `import com.cruisewatch.app.i18n.tr`. Replace:
- line 69: `text = { Text("Add cruise") },` → `text = { Text(tr(R.string.cruises_add_cruise)) },`
- line 80: `Text("Your Cruises", style = MaterialTheme.typography.headlineMedium, color = Color.White)` → `Text(tr(R.string.cruises_title), style = MaterialTheme.typography.headlineMedium, color = Color.White)`

- [ ] **Step 3: Update `CruiseWatchNavHost.kt` bottom nav labels**

Add `import com.cruisewatch.app.R` and `import com.cruisewatch.app.i18n.tr`. Replace lines 117, 124, 131, 138:
- `label = { Text("Cruises") },` → `label = { Text(tr(R.string.nav_cruises)) },`
- `label = { Text("Alerts") },` → `label = { Text(tr(R.string.nav_alerts)) },`
- `label = { Text("Policies") },` → `label = { Text(tr(R.string.nav_policies)) },`
- `label = { Text("Assistant") },` → `label = { Text(tr(R.string.nav_assistant)) },`

- [ ] **Step 4: Verify no translatable literals remain**

Run: `grep -n 'Text("' app/src/main/java/com/cruisewatch/app/ui/screens/TrackedCruisesScreen.kt`
Expected: no output (both matches converted).

- [ ] **Step 5: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/java/com/cruisewatch/app/ui/screens/TrackedCruisesScreen.kt app/src/main/java/com/cruisewatch/app/ui/CruiseWatchNavHost.kt
git commit -m "Retrofit TrackedCruisesScreen and bottom nav labels for translation"
```

---

### Task 7: Retrofit `AddCruiseScreen.kt`

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/com/cruisewatch/app/ui/screens/AddCruiseScreen.kt`

**Interfaces:**
- Consumes: `tr()` from Task 5. Follows the exact pattern from Task 6.

- [ ] **Step 1: Add string resources**

```xml
    <string name="add_cruise_line">Cruise line</string>
    <string name="add_cruise_ship_name">Ship name (as shown on the line's website)</string>
    <string name="add_cruise_sail_date">Sail date (YYYY-MM-DD)</string>
    <string name="add_cruise_cabin_category">Cabin category</string>
    <string name="add_cruise_guarantee">This is a Guarantee (GTY) stateroom</string>
    <string name="add_cruise_fare_paid">Fare paid (cruise fare only, no taxes/fees)</string>
    <string name="add_cruise_currency">Currency (e.g. USD)</string>
    <string name="add_cruise_final_payment_date">Final payment date (YYYY-MM-DD)</string>
    <string name="add_cruise_start_tracking">Start tracking</string>
```

- [ ] **Step 2: Update call sites**

Add `import com.cruisewatch.app.R` and `import com.cruisewatch.app.i18n.tr` to `AddCruiseScreen.kt`. Replace each literal on its line with the matching key, e.g. line 84 `label = { Text("Cruise line") },` → `label = { Text(tr(R.string.add_cruise_line)) },`; apply the same substitution for lines 104, 111, 120, 143, 155, 163, 170, 196 using the keys added in Step 1 in the same order they appear in the file.

- [ ] **Step 3: Verify no translatable literals remain**

Run: `grep -n 'Text("' app/src/main/java/com/cruisewatch/app/ui/screens/AddCruiseScreen.kt`
Expected: no output.

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/java/com/cruisewatch/app/ui/screens/AddCruiseScreen.kt
git commit -m "Retrofit AddCruiseScreen for translation"
```

---

### Task 8: Retrofit `AlertsScreen.kt`

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/com/cruisewatch/app/ui/screens/AlertsScreen.kt`

- [ ] **Step 1: Add string resources**

```xml
    <string name="alerts_title">Price Alerts</string>
    <string name="alerts_ask_assistant">Ask the assistant about this</string>
    <string name="alerts_mark_claimed">Mark as claimed</string>
```

(The `"🔭"` on line 79 is an emoji glyph, not translatable copy — leave it as a literal.)

- [ ] **Step 2: Update call sites**

Add `import com.cruisewatch.app.R` and `import com.cruisewatch.app.i18n.tr`. Replace lines 74 and 101 (both `Text("Price Alerts", ...)`) with `Text(tr(R.string.alerts_title), ...)`. Replace line 237 `Text(" Ask the assistant about this", modifier = Modifier.padding(start = 4.dp))` with `Text(" " + tr(R.string.alerts_ask_assistant), modifier = Modifier.padding(start = 4.dp))` (leading space preserved for the icon gap). Replace line 250 `Text("Mark as claimed")` with `Text(tr(R.string.alerts_mark_claimed))`.

- [ ] **Step 3: Verify remaining literals are non-translatable**

Run: `grep -n 'Text("' app/src/main/java/com/cruisewatch/app/ui/screens/AlertsScreen.kt`
Expected: only the `Text("🔭", ...)` line remains.

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/java/com/cruisewatch/app/ui/screens/AlertsScreen.kt
git commit -m "Retrofit AlertsScreen for translation"
```

---

### Task 9: Retrofit `AssistantScreen.kt` (including format strings)

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/com/cruisewatch/app/ui/screens/AssistantScreen.kt`

This screen has mixed literal+interpolated copy — use `tr(id, *args)` (the format-string overload from Task 5) with Android `%1$s`/`%1$d` placeholders in `strings.xml`.

- [ ] **Step 1: Add string resources**

```xml
    <string name="assistant_title">Refund Assistant</string>
    <string name="assistant_loading_model">Loading the model…</string>
    <string name="assistant_setup_error">Couldn\'t set up the assistant: %1$s</string>
    <string name="assistant_setup_title">Set up your assistant</string>
    <string name="assistant_download_size">%1$d MB one-time download, then runs fully offline on your device.</string>
    <string name="assistant_download_button">Download and set up</string>
    <string name="assistant_privacy_note">Everything you ask stays on this device — nothing is sent anywhere.</string>
    <string name="assistant_downloading">Downloading %1$s…</string>
    <string name="assistant_download_progress">%1$d MB / %2$d MB</string>
    <string name="assistant_thinking">Thinking…</string>
    <string name="assistant_input_placeholder">Ask how to get your refund…</string>
```

- [ ] **Step 2: Update call sites**

Add `import com.cruisewatch.app.R` and `import com.cruisewatch.app.i18n.tr`. Apply:
- line 61: `Text("Refund Assistant", ...)` → `Text(tr(R.string.assistant_title), ...)`
- line 77: `Text("Loading the model…", ...)` → `Text(tr(R.string.assistant_loading_model), ...)`
- line 81: `Text("Couldn't set up the assistant: ${s.message}", ...)` → `Text(tr(R.string.assistant_setup_error, s.message ?: ""), ...)`
- line 91: `Text("Set up your assistant", ...)` → `Text(tr(R.string.assistant_setup_title), ...)`
- line 101: `Text("${model.approxSizeMb} MB one-time download, then runs fully offline on your device.", ...)` → `Text(tr(R.string.assistant_download_size, model.approxSizeMb), ...)`
- line 115: `Text("Download and set up")` → `Text(tr(R.string.assistant_download_button))`
- line 116-120 area: `Text("Everything you ask stays on this device — nothing is sent anywhere.", ...)` → `Text(tr(R.string.assistant_privacy_note), ...)`
- line 129: `Text("Downloading $modelName…", ...)` → `Text(tr(R.string.assistant_downloading, modelName), ...)`
- line 132: `Text("$downloadedMb MB / $totalMb MB", ...)` → `Text(tr(R.string.assistant_download_progress, downloadedMb, totalMb), ...)`
- line 155: `Text("Thinking…", ...)` → `Text(tr(R.string.assistant_thinking), ...)`
- line 164: `placeholder = { Text("Ask how to get your refund…") },` → `placeholder = { Text(tr(R.string.assistant_input_placeholder)) },`

- [ ] **Step 3: Verify no translatable literals remain**

Run: `grep -n 'Text("' app/src/main/java/com/cruisewatch/app/ui/screens/AssistantScreen.kt`
Expected: no output.

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/java/com/cruisewatch/app/ui/screens/AssistantScreen.kt
git commit -m "Retrofit AssistantScreen for translation, including format strings"
```

---

### Task 10: Retrofit `ClaimsScreen.kt`

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/com/cruisewatch/app/ui/screens/ClaimsScreen.kt`

- [ ] **Step 1: Add string resources**

```xml
    <string name="claims_title">Policies</string>
    <string name="claims_how_to_refund">How to get your refund</string>
```

- [ ] **Step 2: Update call sites**

Add `import com.cruisewatch.app.R` and `import com.cruisewatch.app.i18n.tr`. Replace line 36 `Text("Policies", ...)` → `Text(tr(R.string.claims_title), ...)`. Replace line 79 `Text("How to get your refund", ...)` → `Text(tr(R.string.claims_how_to_refund), ...)`.

- [ ] **Step 3: Verify no translatable literals remain**

Run: `grep -n 'Text("' app/src/main/java/com/cruisewatch/app/ui/screens/ClaimsScreen.kt`
Expected: no output.

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/java/com/cruisewatch/app/ui/screens/ClaimsScreen.kt
git commit -m "Retrofit ClaimsScreen for translation"
```

---

### Task 11: Retrofit `PriceHistoryScreen.kt`

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/com/cruisewatch/app/ui/screens/PriceHistoryScreen.kt`

- [ ] **Step 1: Add string resources**

```xml
    <string name="price_history_title">Price history</string>
    <string name="price_history_empty">No price checks yet — the scraper runs every few hours.</string>
```

(Line 90's `Text("$${"%.2f".format(snapshot.fare)}", ...)` is a formatted currency amount, not UI copy — leave it as-is.)

- [ ] **Step 2: Update call sites**

Add `import com.cruisewatch.app.R` and `import com.cruisewatch.app.i18n.tr`. Replace line 56 `Text("Price history", ...)` → `Text(tr(R.string.price_history_title), ...)`. Replace line 70 `Text("No price checks yet — the scraper runs every few hours.")` → `Text(tr(R.string.price_history_empty))`.

- [ ] **Step 3: Verify only the currency literal remains**

Run: `grep -n 'Text("' app/src/main/java/com/cruisewatch/app/ui/screens/PriceHistoryScreen.kt`
Expected: only the `$${"%.2f"...}` line.

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/java/com/cruisewatch/app/ui/screens/PriceHistoryScreen.kt
git commit -m "Retrofit PriceHistoryScreen for translation"
```

---

### Task 12: Retrofit `SignInScreen.kt`

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/com/cruisewatch/app/ui/screens/SignInScreen.kt`

- [ ] **Step 1: Add string resources**

```xml
    <string name="sign_in_continue_google">Continue with Google</string>
    <string name="sign_in_email">Email</string>
    <string name="sign_in_password">Password</string>
```

- [ ] **Step 2: Update call sites**

Add `import com.cruisewatch.app.R` and `import com.cruisewatch.app.i18n.tr`. Replace line 119 `Text("Continue with Google")` → `Text(tr(R.string.sign_in_continue_google))`. Replace line 135 `label = { Text("Email") },` → `label = { Text(tr(R.string.sign_in_email)) },`. Replace line 145 `label = { Text("Password") },` → `label = { Text(tr(R.string.sign_in_password)) },`.

- [ ] **Step 3: Verify no translatable literals remain**

Run: `grep -n 'Text("' app/src/main/java/com/cruisewatch/app/ui/screens/SignInScreen.kt`
Expected: no output.

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/java/com/cruisewatch/app/ui/screens/SignInScreen.kt
git commit -m "Retrofit SignInScreen for translation"
```

---

### Task 13: Sweep remaining screens for any literal copy grep missed

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: any of `app/src/main/java/com/cruisewatch/app/ui/screens/*.kt` or `app/src/main/java/com/cruisewatch/app/ui/*.kt` with hits

Tasks 6–12 covered every `Text("literal...)` call that grep's `'Text("'` pattern (literal as the first argument) found. Some Compose calls pass copy as a named argument (`Text(text = "...")`) or wrap a `String.format`/`buildString` result — this task closes that gap before moving on.

- [ ] **Step 1: Find any remaining hardcoded English UI copy**

Run: `grep -rn 'Text(text = "\|Text(\n *"' app/src/main/java/com/cruisewatch/app/ui --include="*.kt" -P` and separately re-run `grep -rn 'Text("' app/src/main/java/com/cruisewatch/app/ui --include="*.kt"` to confirm the earlier tasks' files are still clean. Also check `app/src/main/java/com/cruisewatch/app/ui/GlassPanel.kt` and `app/src/main/java/com/cruisewatch/app/ui/PhotoHero.kt` for any `Text(` usage with literal copy (these are shared composables, not screens, so Tasks 6–12 didn't touch them).

- [ ] **Step 2: For each hit found, apply the Task 6 pattern**

Add a `strings.xml` entry named `<component>_<slug>`, replace the literal with `tr(R.string.<key>)`, import `com.cruisewatch.app.R` and `com.cruisewatch.app.i18n.tr` in that file. If Step 1 finds no hits, skip straight to Step 3 — this task's deliverable is the verification, not forced changes.

- [ ] **Step 3: Confirm the full retrofit is complete**

Run: `grep -rn 'Text("' app/src/main/java/com/cruisewatch/app/ui --include="*.kt"`
Expected: only non-translatable literals remain (currency template in `PriceHistoryScreen.kt`, emoji in `AlertsScreen.kt`) — no English sentences or words left un-keyed.

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A app/src/main/res/values/strings.xml app/src/main/java/com/cruisewatch/app/ui
git commit -m "Sweep remaining screens for hardcoded copy missed by earlier retrofit tasks"
```

---

### Task 14: Shared language picker composable

**Files:**
- Create: `app/src/main/java/com/cruisewatch/app/ui/screens/LanguagePickerScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `SupportedLanguages.ALL` (Task 2), `TranslationManager` + `TranslationState` (Task 4), `tr()` (Task 5).
- Produces: `@Composable fun LanguagePickerScreen(manager: TranslationManager, currentLanguageCode: String, onLanguageApplied: (String) -> Unit, onCancel: () -> Unit)` — used by both Onboarding (Task 15) and Settings (Task 16).

- [ ] **Step 1: Add string resources**

```xml
    <string name="language_picker_title">Choose your language</string>
    <string name="language_picker_search">Search languages</string>
    <string name="language_picker_downloading">Downloading language pack…</string>
    <string name="language_picker_translating">Translating…</string>
    <string name="language_picker_error">Something went wrong: %1$s</string>
    <string name="language_picker_retry">Retry</string>
    <string name="language_picker_continue_english">Continue in English</string>
    <string name="language_picker_wifi_required_title">Wi-Fi recommended</string>
    <string name="language_picker_wifi_required_body">This language pack is about 30 MB. Download on Wi-Fi, or continue now on your current connection.</string>
    <string name="language_picker_wifi_wait">Wait for Wi-Fi</string>
    <string name="language_picker_wifi_now">Download now</string>
```

- [ ] **Step 2: Write the implementation**

```kotlin
package com.cruisewatch.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cruisewatch.app.R
import com.cruisewatch.app.i18n.SupportedLanguage
import com.cruisewatch.app.i18n.SupportedLanguages
import com.cruisewatch.app.i18n.TranslationManager
import com.cruisewatch.app.i18n.TranslationState
import com.cruisewatch.app.i18n.tr
import kotlinx.coroutines.launch

@Composable
fun LanguagePickerScreen(
    manager: TranslationManager,
    currentLanguageCode: String,
    onLanguageApplied: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val state by manager.state.collectAsState()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var pendingCellularLanguage by remember { mutableStateOf<SupportedLanguage?>(null) }

    fun select(language: SupportedLanguage, allowCellular: Boolean = false) {
        scope.launch { manager.selectLanguage(language.code, allowCellular) }
    }

    when (val s = state) {
        is TranslationState.Downloading -> BlockingProgress(tr(R.string.language_picker_downloading))
        is TranslationState.Translating -> BlockingProgress(tr(R.string.language_picker_translating))
        is TranslationState.Ready -> if (s.language.code != currentLanguageCode) {
            onLanguageApplied(s.language.code)
        } else {
            LanguageList(query, { query = it }, ::select, onCancel)
        }
        is TranslationState.Failed -> ErrorState(
            message = s.message,
            onRetry = { select(s.language) },
            onContinueEnglish = { select(SupportedLanguages.ENGLISH) },
        )
        TranslationState.Idle -> LanguageList(query, { query = it }, ::select, onCancel)
    }

    pendingCellularLanguage?.let { language ->
        AlertDialog(
            onDismissRequest = { pendingCellularLanguage = null },
            title = { Text(tr(R.string.language_picker_wifi_required_title)) },
            text = { Text(tr(R.string.language_picker_wifi_required_body)) },
            confirmButton = {
                TextButton(onClick = {
                    select(language, allowCellular = true)
                    pendingCellularLanguage = null
                }) { Text(tr(R.string.language_picker_wifi_now)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingCellularLanguage = null }) {
                    Text(tr(R.string.language_picker_wifi_wait))
                }
            },
        )
    }
}

@Composable
private fun LanguageList(
    query: String,
    onQueryChange: (String) -> Unit,
    onSelect: (SupportedLanguage) -> Unit,
    onCancel: () -> Unit,
) {
    val filtered = remember(query) {
        SupportedLanguages.ALL.filter {
            it.nativeName.contains(query, ignoreCase = true) || it.englishName.contains(query, ignoreCase = true)
        }
    }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(tr(R.string.language_picker_title), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text(tr(R.string.language_picker_search)) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        )
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(filtered, key = { it.code }) { language ->
                ListItem(
                    headlineContent = { Text(language.nativeName) },
                    supportingContent = { Text(language.englishName) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        .let { m -> m },
                )
            }
        }
    }
}

@Composable
private fun BlockingProgress(label: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, onContinueEnglish: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr(R.string.language_picker_error, message), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRetry) { Text(tr(R.string.language_picker_retry)) }
            TextButton(onClick = onContinueEnglish) { Text(tr(R.string.language_picker_continue_english)) }
        }
    }
}
```

`ListItem` needs an `onClick` — Compose Material3's `ListItem` itself is not clickable, so wrap it: change the `items(filtered, ...)` block's `ListItem(...)` to be inside `androidx.compose.foundation.clickable { onSelect(language) }` on the `Modifier` passed to it (add `import androidx.compose.foundation.clickable` and change `modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).let { m -> m }` to `modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable { onSelect(language) }`).

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/java/com/cruisewatch/app/ui/screens/LanguagePickerScreen.kt
git commit -m "Add shared language picker screen for onboarding and settings"
```

---

### Task 15: Onboarding language page

**Files:**
- Modify: `app/src/main/java/com/cruisewatch/app/ui/screens/OnboardingScreen.kt`
- Modify: `app/src/main/java/com/cruisewatch/app/ui/CruiseWatchNavHost.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `LanguagePickerScreen` (Task 14), `TranslationManager` (Task 4), `tr()` (Task 5).

- [ ] **Step 1: Add string resources**

```xml
    <string name="onboarding_skip">Skip</string>
```

(This replaces the existing `"Skip"` literal on line 120; the other onboarding page titles/bodies (`pages` list, lines 51-70ish) are data describing each page — leave those as regular Kotlin string literals in the `pages` list for now, since Task 13's sweep already covers any the grep patterns missed. If Task 13 already converted them, skip re-adding here.)

- [ ] **Step 2: Add language selection as the first onboarding step**

In `OnboardingScreen.kt`, change the function signature from `onFinish: () -> Unit` (check the existing signature at the top of the composable) to also accept `translationManager: TranslationManager` and `currentLanguageCode: String`. Add a local `var languageChosen by remember { mutableStateOf(false) }`. At the very top of the composable body, before the existing pager UI:

```kotlin
    if (!languageChosen) {
        LanguagePickerScreen(
            manager = translationManager,
            currentLanguageCode = currentLanguageCode,
            onLanguageApplied = { languageChosen = true },
            onCancel = { languageChosen = true },
        )
        return
    }
```

Add `import com.cruisewatch.app.i18n.TranslationManager`, `import com.cruisewatch.app.i18n.ProvideTranslations`, `import com.cruisewatch.app.i18n.tr`, `import com.cruisewatch.app.R`, and `import androidx.compose.runtime.mutableStateOf` / `import androidx.compose.runtime.setValue` if not already present. Replace line 120's `Text("Skip", color = Color.White)` with `Text(tr(R.string.onboarding_skip), color = Color.White)`. Wrap everything after the language-picker early-return (the rest of the existing composable body) in `ProvideTranslations(translationManager) { ... }` — this screen renders before `CruiseWatchNavHost`'s own `ProvideTranslations` wrapper (Task 5) is reached, so it needs its own.

- [ ] **Step 3: Pass `translationManager` from the nav host**

In `CruiseWatchNavHost.kt`, the `OnboardingScreen(onFinish = { ... })` call (inside the `if (!hasOnboarded)` block) becomes:

```kotlin
        OnboardingScreen(
            translationManager = translationManager,
            currentLanguageCode = translationManager.state.value.let { (it as? com.cruisewatch.app.i18n.TranslationState.Ready)?.language?.code } ?: "en",
            onFinish = {
                prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()
                hasOnboarded = true
            },
        )
```

Note this call sits *outside* the `ProvideTranslations` wrapper from Task 5 (onboarding runs before the rest of the app's chrome) — `OnboardingScreen` must call `tr()` under its own `ProvideTranslations(translationManager) { ... }` wrapper internally, so wrap the composable's full body (after the language-picker early-return) in that.

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Manual check**

Run the app on a device/emulator with onboarding not yet completed (uninstall or clear app data first). Confirm the language picker appears first, selecting e.g. Spanish shows the blocking download/translate progress, then the rest of onboarding renders with the "Skip" button translated.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/java/com/cruisewatch/app/ui/screens/OnboardingScreen.kt app/src/main/java/com/cruisewatch/app/ui/CruiseWatchNavHost.kt
git commit -m "Add language selection as the first onboarding step"
```

---

### Task 16: Settings screen + nav entry point

**Files:**
- Create: `app/src/main/java/com/cruisewatch/app/ui/screens/SettingsScreen.kt`
- Modify: `app/src/main/java/com/cruisewatch/app/ui/CruiseWatchNavHost.kt`
- Modify: `app/src/main/java/com/cruisewatch/app/ui/screens/TrackedCruisesScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `LanguagePickerScreen` (Task 14), `SupportedLanguages.byCode` (Task 2), `tr()` (Task 5).

- [ ] **Step 1: Add string resources**

```xml
    <string name="settings_title">Settings</string>
    <string name="settings_language_row">Language</string>
    <string name="settings_back">Back</string>
```

- [ ] **Step 2: Write `SettingsScreen.kt`**

```kotlin
package com.cruisewatch.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.cruisewatch.app.R
import com.cruisewatch.app.i18n.SupportedLanguages
import com.cruisewatch.app.i18n.TranslationManager
import com.cruisewatch.app.i18n.TranslationState
import com.cruisewatch.app.i18n.tr

@Composable
fun SettingsScreen(translationManager: TranslationManager, onBack: () -> Unit) {
    var showLanguagePicker by remember { mutableStateOf(false) }
    val state by translationManager.state.collectAsState()
    val currentCode = (state as? TranslationState.Ready)?.language?.code ?: SupportedLanguages.ENGLISH.code

    if (showLanguagePicker) {
        LanguagePickerScreen(
            manager = translationManager,
            currentLanguageCode = currentCode,
            onLanguageApplied = { showLanguagePicker = false },
            onCancel = { showLanguagePicker = false },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr(R.string.settings_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxWidth().padding(padding)) {
            val currentName = SupportedLanguages.byCode(currentCode)?.nativeName ?: currentCode
            ListItem(
                headlineContent = { Text(tr(R.string.settings_language_row)) },
                supportingContent = { Text(currentName) },
                modifier = Modifier.fillMaxWidth().clickable { showLanguagePicker = true },
            )
        }
    }
}
```

- [ ] **Step 3: Add a settings entry point on `TrackedCruisesScreen.kt`**

Add an `onOpenSettings: () -> Unit` parameter to `TrackedCruisesScreen`'s signature (`app/src/main/java/com/cruisewatch/app/ui/screens/TrackedCruisesScreen.kt`, alongside the existing `onAddCruise`/`onOpenCruise` params). Inside the `PhotoHero(...) { ... }` block (lines 75-87), wrap the existing `Column` in a `Box(modifier = Modifier.fillMaxSize())` and add a settings icon button aligned top-end:

```kotlin
            PhotoHero(photoRes = R.drawable.hero_cruises, height = 210.dp) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Text(tr(R.string.cruises_title), style = MaterialTheme.typography.headlineMedium, color = Color.White)
                        Text(
                            "${cruiseList.size} sailing${if (cruiseList.size == 1) "" else "s"} being watched 24/7",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f),
                        )
                    }
                    IconButton(onClick = onOpenSettings, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                        Icon(Icons.Filled.Settings, contentDescription = tr(R.string.settings_title), tint = Color.White)
                    }
                }
            }
```

Add imports: `androidx.compose.foundation.layout.Box`, `androidx.compose.material.icons.filled.Settings`, `androidx.compose.material3.IconButton`, `androidx.compose.ui.Alignment`.

- [ ] **Step 4: Wire the route in `CruiseWatchNavHost.kt`**

Add `const val SETTINGS = "settings"` to the `Routes` object. Add `Routes.SETTINGS` to `topLevelRoutes` is NOT needed (it's a detail screen, uses the slide transition like `ADD_CRUISE`). Pass `onOpenSettings = { navController.navigate(Routes.SETTINGS) }` to the `TrackedCruisesScreen(...)` call inside `composable(Routes.CRUISES) { ... }`. Register the destination:

```kotlin
            composable(Routes.SETTINGS) {
                SettingsScreen(translationManager = translationManager, onBack = { navController.popBackStack() })
            }
```

Add `import com.cruisewatch.app.ui.screens.SettingsScreen`.

- [ ] **Step 5: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Manual check**

Run the app, sign in, tap the gear icon on the Cruises tab, tap "Language", pick a non-English language, confirm the blocking progress appears then the whole app (bottom nav labels, Cruises header, etc.) re-renders translated, and confirm navigating back from Settings keeps the new language active.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/java/com/cruisewatch/app/ui/screens/SettingsScreen.kt app/src/main/java/com/cruisewatch/app/ui/screens/TrackedCruisesScreen.kt app/src/main/java/com/cruisewatch/app/ui/CruiseWatchNavHost.kt
git commit -m "Add Settings screen with language row and nav entry point"
```

---

### Task 17: AI assistant language steering

**Files:**
- Modify: `app/src/main/java/com/cruisewatch/app/ai/RefundAssistantContext.kt`
- Modify: `app/src/main/java/com/cruisewatch/app/ai/AssistantViewModel.kt`
- Test: `app/src/test/java/com/cruisewatch/app/ai/RefundAssistantContextTest.kt`

**Interfaces:**
- Consumes: `LanguagePrefs.getSelectedLanguage()` (Task 3), `SupportedLanguages.byCode` (Task 2).
- Modifies: `RefundAssistantContext.buildSystemPrompt(...)` gains a `languageCode: String` parameter.

- [ ] **Step 1: Write the failing test**

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.cruisewatch.app.ai.RefundAssistantContextTest"`
Expected: FAIL — `buildSystemPrompt` has no `languageCode` parameter.

- [ ] **Step 3: Update `RefundAssistantContext.buildSystemPrompt`**

In `app/src/main/java/com/cruisewatch/app/ai/RefundAssistantContext.kt`, add `languageCode: String = com.google.mlkit.nl.translate.TranslateLanguage.ENGLISH` as a new parameter to `buildSystemPrompt` (after `focusCruiseId`), and change the `sb.append(...)` block (lines 20-27) to prepend the language instruction before the existing opening paragraph:

```kotlin
        val sb = StringBuilder()
        val language = com.cruisewatch.app.i18n.SupportedLanguages.byCode(languageCode)
        if (language != null && language.code != com.cruisewatch.app.i18n.SupportedLanguages.ENGLISH.code) {
            sb.append("Respond ONLY in ${language.nativeName}. Never respond in English unless the user writes in English.\n\n")
        }
        sb.append(
            "You are the CruiseWatch Refund Assistant, built into a cruise fare price-drop tracking app. " +
                "Your ONLY job is helping this specific user get money back (refund, onboard credit, or upgrade) " +
                "when their cruise fare drops below what they paid, using their cruise line's real price-protection policy. " +
                "Do not answer questions unrelated to their cruises, refunds, or claim process — politely redirect back to " +
                "that topic if asked something else. Be concise, encouraging, and give concrete next steps (what to say, " +
                "who to call, what deadline applies). Use the real data below; never invent a policy detail not given here.\n\n",
        )
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.cruisewatch.app.ai.RefundAssistantContextTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Pass the selected language from `AssistantViewModel.kt`**

In `app/src/main/java/com/cruisewatch/app/ai/AssistantViewModel.kt`, add `private val languagePrefs by lazy { com.cruisewatch.app.i18n.LanguagePrefs(getApplication()) }` near the existing `private val prefs by lazy { ... }` (line 31). At both call sites building `systemPrompt` (lines 105 and 128), add `languageCode = languagePrefs.getSelectedLanguage(),` as an argument to `RefundAssistantContext.buildSystemPrompt(...)`.

- [ ] **Step 6: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/cruisewatch/app/ai/RefundAssistantContext.kt app/src/main/java/com/cruisewatch/app/ai/AssistantViewModel.kt app/src/test/java/com/cruisewatch/app/ai/RefundAssistantContextTest.kt
git commit -m "Steer AI refund assistant to respond in the selected language"
```

---

### Task 18: Full end-to-end manual verification

**Files:** none (verification only, per superpowers:verification-before-completion — evidence before completion claims).

- [ ] **Step 1: Full test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests from Tasks 2, 3, 4, 17 passing.

- [ ] **Step 2: Full release-config build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Fresh-install onboarding flow, non-English**

Clear app data (or uninstall/reinstall on an emulator), launch the app, pick a non-Latin-script language (e.g. Japanese or Arabic) on the first onboarding page. Confirm: the blocking "Downloading…" then "Translating…" states appear, all remaining onboarding pages render translated, the "Skip" button is translated.

- [ ] **Step 4: Settings language switch**

Sign in, tap the gear icon on the Cruises tab, open Settings, tap Language, pick a different language (e.g. Spanish). Confirm the whole app — bottom nav labels, Cruises/Alerts/Policies headers, buttons — re-renders in the new language after the blocking progress completes.

- [ ] **Step 5: Instant reload from cache**

Force-stop and relaunch the app (same language as Step 4). Confirm the UI renders in that language immediately with no download/translate progress shown (verifies the `SharedPreferences` cache in `LanguagePrefs` is being used, per Task 4's `selectLanguage` cache-hit path).

- [ ] **Step 6: Airplane-mode cache check**

Enable airplane mode, relaunch the app. Confirm the previously-selected non-English language still renders correctly (proves no network call happens for a cached language).

- [ ] **Step 7: AI assistant language**

Open the Assistant tab with a non-English language selected, send a message. Confirm the model's reply is in that language (per Task 17's system-prompt instruction).

- [ ] **Step 8: English round-trip**

Switch back to English from Settings. Confirm it applies instantly (no download, since English needs no ML Kit model per Task 4).

Report the outcome of Steps 3-7 explicitly (pass/fail per step) before considering this plan complete — per `superpowers:verification-before-completion`, do not claim the feature works without having actually run it.
