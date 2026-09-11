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
