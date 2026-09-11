package com.cruisewatch.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
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

@OptIn(ExperimentalMaterial3Api::class)
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
