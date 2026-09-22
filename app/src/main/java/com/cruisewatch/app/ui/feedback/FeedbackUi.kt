package com.cruisewatch.app.ui.feedback

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cruisewatch.app.data.feedback.BugReport
import com.cruisewatch.app.data.feedback.uriToPreviewBitmap
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

private const val PRIVACY_WARNING =
    "Your report will be submitted to this app's GitHub issue tracker. " +
        "Do not include passwords, private keys, medical information, financial information, " +
        "account credentials, or anything you do not want visible to repository maintainers. " +
        "Screenshots may contain private information. " +
        "If this repository is public, your report and attached screenshot may be publicly visible."

private fun formatTimestamp(iso: String): String {
    return runCatching {
        OffsetDateTime.parse(iso).format(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a"))
    }.getOrElse { iso }
}

/** "Support & Feedback" section: report button plus locally stored reports. */
@Composable
fun SupportFeedbackSection(viewModel: FeedbackViewModel = viewModel()) {
    val reports by viewModel.reports.collectAsState()
    var showReportDialog by remember { mutableStateOf(false) }
    var selectedReport by remember { mutableStateOf<BugReport?>(null) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            "Support & Feedback",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (!viewModel.isConfigured) {
                    Text(
                        "Feedback service is not configured for this build.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Button(
                    onClick = { showReportDialog = true },
                    enabled = viewModel.isConfigured,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Report a Problem")
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (reports.isEmpty()) {
                    Text(
                        "No reports submitted yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    reports.forEach { report ->
                        ReportRow(report = report, onClick = { selectedReport = report })
                    }
                }
            }
        }
    }

    if (showReportDialog) {
        ReportProblemDialog(
            viewModel = viewModel,
            onDismiss = {
                showReportDialog = false
                viewModel.resetSubmitState()
            },
        )
    }

    selectedReport?.let { report ->
        IssueDetailsDialog(
            viewModel = viewModel,
            report = report,
            onDismiss = {
                selectedReport = null
                viewModel.closeDetails()
            },
        )
    }
}

@Composable
private fun ReportRow(report: BugReport, onClick: () -> Unit) {
    val open = report.status.equals("open", ignoreCase = true)
    ListItem(
        headlineContent = { Text(report.title) },
        supportingContent = { Text("#${report.number} · ${formatTimestamp(report.createdAt)}") },
        trailingContent = {
            AssistChip(
                onClick = {},
                label = { Text(if (open) "Open" else "Closed") },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (open) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
            )
        },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
    HorizontalDivider()
}

@Composable
private fun ReportProblemDialog(viewModel: FeedbackViewModel, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var includeDiagnostics by remember { mutableStateOf(true) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    val submitState by viewModel.submitState.collectAsState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) imageUri = uri
    }

    val success = submitState as? SubmitState.Success
    if (success != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
            title = { Text("Report submitted") },
            text = { Text("Thank you! Your report was filed as issue #${success.issueNumber}.") },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            val busy = submitState is SubmitState.Submitting || submitState is SubmitState.UploadingImage
            TextButton(
                onClick = {
                    viewModel.submitReport(title, description, name, email, includeDiagnostics, imageUri)
                },
                enabled = !busy && title.isNotBlank() && description.isNotBlank() && viewModel.isConfigured,
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Submit")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Report a Problem") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (!viewModel.isConfigured) {
                    Text(
                        "Feedback service is not configured for this build.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Text(
                        PRIVACY_WARNING,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(200) },
                    label = { Text("Title / Subject *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description *") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeDiagnostics, onCheckedChange = { includeDiagnostics = it })
                    Text("Include phone/app diagnostics", style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                ImageAttachmentPicker(imageUri = imageUri, onPick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, onClear = { imageUri = null })
                val error = (submitState as? SubmitState.Error)?.message
                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                val uploading = submitState as? SubmitState.UploadingImage
                if (uploading != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(uploading.step, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
    )
}

@Composable
private fun ImageAttachmentPicker(imageUri: Uri?, onPick: () -> Unit, onClear: () -> Unit) {
    val context = LocalContext.current
    if (imageUri == null) {
        OutlinedButton(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
            Text("Attach screenshot / image")
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            val bitmap = remember(imageUri) { uriToPreviewBitmap(context, imageUri) }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Selected image preview",
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text("Image attached", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onClear) { Text("Remove") }
        }
    }
}

/** Large details dialog for a submitted report: live status, body, comments, reply. */
@Composable
private fun IssueDetailsDialog(viewModel: FeedbackViewModel, report: BugReport, onDismiss: () -> Unit) {
    val details by viewModel.details.collectAsState()
    val uriHandler = LocalUriHandler.current
    var replyText by remember { mutableStateOf("") }
    var replyImageUri by remember { mutableStateOf<Uri?>(null) }

    androidx.compose.runtime.LaunchedEffect(report.number) {
        viewModel.openDetails(report)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.9f),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        details.issue?.title ?: report.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val state = details.issue?.state ?: report.status
                    val open = state.equals("open", ignoreCase = true)
                    AssistChip(
                        onClick = {},
                        label = { Text(if (open) "Open" else "Closed") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (open) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("#${report.number}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = { viewModel.refreshDetails(report.number) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
                Text(
                    "Created ${formatTimestamp(details.issue?.createdAt ?: report.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = {
                    runCatching { uriHandler.openUri(details.issue?.htmlUrl ?: report.htmlUrl) }
                }) {
                    Text("View on GitHub")
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                if (details.loading && details.issue == null) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                } else if (details.error != null && details.issue == null) {
                    Text(
                        details.error ?: "Unable to refresh this report.",
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        "Showing cached information.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    details.issue?.body?.takeIf { it.isNotBlank() }?.let { body ->
                        item {
                            Text(body, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider()
                        }
                    }
                    items(details.comments) { comment ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text(
                                "${comment.user.login} · ${formatTimestamp(comment.createdAt)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(comment.body, style = MaterialTheme.typography.bodyMedium)
                            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                    if (!details.loading && details.issue != null && details.comments.isEmpty()) {
                        item {
                            Text(
                                "No comments yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                val replyPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                    if (uri != null) replyImageUri = uri
                }
                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    label = { Text("Write a reply") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                ImageAttachmentPicker(imageUri = replyImageUri, onPick = {
                    replyPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, onClear = { replyImageUri = null })
                val replyState = details.replyState
                if (replyState is SubmitState.Error) {
                    Text(replyState.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (replyState is SubmitState.UploadingImage) {
                    Text(replyState.step, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(8.dp))
                val busy = replyState is SubmitState.Submitting || replyState is SubmitState.UploadingImage
                Button(
                    onClick = {
                        viewModel.postReply(report.number, replyText, replyImageUri)
                        replyText = ""
                        replyImageUri = null
                    },
                    enabled = !busy && replyText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (busy) "Sending…" else "Submit reply")
                }
            }
        }
    }
}
