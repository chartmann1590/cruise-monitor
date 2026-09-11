package com.cruisewatch.app.ui.screens

import androidx.compose.foundation.clickable
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
                        .clickable { onSelect(language) },
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
