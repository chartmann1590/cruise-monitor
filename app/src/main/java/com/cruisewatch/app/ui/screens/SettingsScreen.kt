package com.cruisewatch.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cruisewatch.app.BuildConfig
import com.cruisewatch.app.R
import com.cruisewatch.app.auth.AuthViewModel
import com.cruisewatch.app.i18n.SupportedLanguages
import com.cruisewatch.app.i18n.TranslationManager
import com.cruisewatch.app.i18n.TranslationState
import com.cruisewatch.app.i18n.tr
import com.cruisewatch.app.ui.feedback.SupportFeedbackSection
import com.cruisewatch.crosspromo.CrossPromoSection
import com.cruisewatch.crosspromo.CrosspromoViewModel
import com.cruisewatch.crosspromo.CrosspromoViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    translationManager: TranslationManager,
    authViewModel: AuthViewModel? = null,
    onBack: () -> Unit,
    sourcePackage: String = "com.cruisewatch.app",
) {
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    val state by translationManager.state.collectAsState()
    val currentCode = (state as? TranslationState.Ready)?.language?.code ?: SupportedLanguages.ENGLISH.code

    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val crosspromoViewModel: CrosspromoViewModel = viewModel(
        factory = CrosspromoViewModelFactory(
            application = context.applicationContext as android.app.Application,
            sourcePackage = sourcePackage,
            placement = "settings",
            baseUrl = BuildConfig.CROSS_PROMO_BASE_URL,
        ),
    )

    if (showLanguagePicker) {
        LanguagePickerScreen(
            manager = translationManager,
            onLanguageApplied = { showLanguagePicker = false },
            onCancel = { showLanguagePicker = false },
        )
        return
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(tr(R.string.settings_delete_account_confirm_title)) },
            text = { Text(tr(R.string.settings_delete_account_confirm_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        authViewModel?.deleteAccount { result ->
                            result.onFailure { e ->
                                deleteError = e.message ?: "Failed to delete account"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(tr(R.string.settings_delete_account_confirm_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(tr(R.string.settings_delete_account_cancel_button))
                }
            },
        )
    }

    if (deleteError != null) {
        AlertDialog(
            onDismissRequest = { deleteError = null },
            title = { Text(tr(R.string.settings_delete_account)) },
            text = { Text(tr(R.string.settings_delete_account_error, deleteError ?: "")) },
            confirmButton = {
                TextButton(onClick = { deleteError = null }) {
                    Text("OK")
                }
            },
        )
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
        Column(modifier = Modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState())) {
            val currentName = SupportedLanguages.byCode(currentCode)?.nativeName ?: currentCode
            ListItem(
                headlineContent = { Text(tr(R.string.settings_language_row)) },
                supportingContent = { Text(currentName) },
                modifier = Modifier.fillMaxWidth().clickable { showLanguagePicker = true },
            )

            if (authViewModel != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = tr(R.string.settings_account_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                val email = authViewModel.currentUserEmail
                if (!email.isNullOrBlank()) {
                    ListItem(
                        headlineContent = { Text(tr(R.string.settings_signed_in_as, email)) },
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { authViewModel.signOut() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                        Text(tr(R.string.settings_sign_out))
                    }
                    Button(
                        onClick = { showDeleteDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                        Text(tr(R.string.settings_delete_account))
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                text = tr(R.string.settings_legal_section),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            ListItem(
                headlineContent = { Text(tr(R.string.settings_privacy_policy)) },
                supportingContent = { Text("https://cruisewatch-app.web.app/privacy.html") },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().clickable {
                    uriHandler.openUri("https://cruisewatch-app.web.app/privacy.html")
                },
            )
            ListItem(
                headlineContent = { Text(tr(R.string.settings_terms_of_service)) },
                supportingContent = { Text("https://cruisewatch-app.web.app/terms.html") },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().clickable {
                    uriHandler.openUri("https://cruisewatch-app.web.app/terms.html")
                },
            )
            ListItem(
                headlineContent = { Text(tr(R.string.settings_delete_account_web)) },
                supportingContent = { Text("https://cruisewatch-app.web.app/delete-account.html") },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().clickable {
                    uriHandler.openUri("https://cruisewatch-app.web.app/delete-account.html")
                },
            )
            ListItem(
                headlineContent = { Text(tr(R.string.settings_app_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)) },
            )

            SupportFeedbackSection()

            CrossPromoSection(
                viewModel = crosspromoViewModel,
                modifier = Modifier.padding(vertical = 8.dp),
                onAppOpened = { app ->
                    val url = app.storeUrl ?: "https://play.google.com/store/apps/details?id=${app.packageName}"
                    uriHandler.openUri(url)
                },
            )
        }
    }
}
