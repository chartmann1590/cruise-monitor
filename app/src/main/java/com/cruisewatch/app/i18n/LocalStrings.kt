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
    // A machine translation can smuggle in a stray `%` that survives isSafeTranslation's placeholder
    // check and makes String.format throw inside composition. Degrade this one label to English
    // rather than taking the whole screen down.
    return runCatching { String.format(template, *args) }
        .getOrElse { context.getString(id, *args) }
}

/**
 * Provides [LocalStrings] from the given [manager]; screens below this render in whatever language
 * was last successfully applied — a download that is still running, or one that failed, leaves the
 * existing language in place instead of snapping the whole app back to English.
 */
@Composable
fun ProvideTranslations(manager: TranslationManager, content: @Composable () -> Unit) {
    val strings by manager.activeStrings.collectAsState()
    CompositionLocalProvider(LocalStrings provides strings, content = content)
}
