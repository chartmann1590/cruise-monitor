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
