package com.cruisewatch.app.ui.feedback

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cruisewatch.app.data.feedback.BugReport
import com.cruisewatch.app.data.feedback.BugReportRepo
import com.cruisewatch.app.data.feedback.DiagnosticsHelper
import com.cruisewatch.app.data.feedback.FeedbackApiException
import com.cruisewatch.app.data.feedback.FeedbackComment
import com.cruisewatch.app.data.feedback.FeedbackIssue
import com.cruisewatch.app.data.feedback.FeedbackWorkerApi
import com.cruisewatch.app.data.feedback.ImagePayload
import com.cruisewatch.app.data.feedback.uriToBase64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SubmitState {
    data object Idle : SubmitState
    data class UploadingImage(val step: String) : SubmitState
    data object Submitting : SubmitState
    data object Posted : SubmitState
    data class Success(val issueNumber: Int, val htmlUrl: String) : SubmitState
    data class Error(val message: String) : SubmitState
}

data class IssueDetailsState(
    val loading: Boolean = false,
    val issue: FeedbackIssue? = null,
    val comments: List<FeedbackComment> = emptyList(),
    val error: String? = null,
    val replyState: SubmitState = SubmitState.Idle,
)

/** Orchestrates report submission, issue refresh, and replies via the Worker. */
class FeedbackViewModel(application: Application) : AndroidViewModel(application) {

    private val api = FeedbackWorkerApi()
    private val repo = BugReportRepo(application.applicationContext)

    val isConfigured: Boolean = api.isConfigured

    val reports: StateFlow<List<BugReport>> =
        repo.bugReports.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _submitState = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submitState: StateFlow<SubmitState> = _submitState.asStateFlow()

    private val _details = MutableStateFlow(IssueDetailsState())
    val details: StateFlow<IssueDetailsState> = _details.asStateFlow()

    private var pendingDetailsNumber: Int? = null

    fun resetSubmitState() {
        _submitState.value = SubmitState.Idle
    }

    fun submitReport(
        title: String,
        description: String,
        name: String,
        email: String,
        includeDiagnostics: Boolean,
        imageUri: Uri?,
    ) {
        if (_submitState.value is SubmitState.Submitting || _submitState.value is SubmitState.UploadingImage) return
        val trimmedTitle = title.trim()
        val trimmedDescription = description.trim()
        if (trimmedTitle.isEmpty() || trimmedDescription.isEmpty()) {
            _submitState.value = SubmitState.Error("Please enter a title and description.")
            return
        }
        if (!isConfigured) {
            _submitState.value = SubmitState.Error("Feedback service is not configured for this build.")
            return
        }
        viewModelScope.launch {
            try {
                var attachmentMarkdown: String? = null
                if (imageUri != null) {
                    _submitState.value = SubmitState.UploadingImage("Uploading screenshot…")
                    val payload = uriToBase64(getApplication(), imageUri, prefix = "issue")
                    when (payload) {
                        is ImagePayload.Error -> {
                            _submitState.value = SubmitState.Error(payload.message)
                            return@launch
                        }
                        is ImagePayload.Ready -> {
                            val uploaded = api.uploadAsset(payload.fileName, payload.contentBase64)
                            val url = uploaded.downloadUrl
                            if (url.isNullOrBlank()) {
                                _submitState.value = SubmitState.Error("Screenshot upload failed. Please try again.")
                                return@launch
                            }
                            attachmentMarkdown = url
                        }
                    }
                }
                _submitState.value = SubmitState.Submitting
                val body = buildIssueBody(
                    description = trimmedDescription,
                    name = name.trim(),
                    email = email.trim(),
                    attachmentUrl = attachmentMarkdown,
                    diagnostics = if (includeDiagnostics) DiagnosticsHelper.collect(getApplication()) else null,
                )
                val issue = api.createIssue("[Feedback] $trimmedTitle", body)
                repo.saveBugReport(
                    BugReport(
                        number = issue.number,
                        title = issue.title,
                        status = issue.state,
                        createdAt = issue.createdAt,
                        htmlUrl = issue.htmlUrl,
                    ),
                )
                _submitState.value = SubmitState.Success(issue.number, issue.htmlUrl)
            } catch (e: Exception) {
                val message = if (e is FeedbackApiException) {
                    e.message
                } else {
                    "Submission failed. Please try again."
                }
                _submitState.value = SubmitState.Error(message ?: "Submission failed. Please try again.")
            }
        }
    }

    fun openDetails(report: BugReport) {
        pendingDetailsNumber = report.number
        _details.value = IssueDetailsState(loading = true)
        viewModelScope.launch {
            try {
                val issue = api.getIssue(report.number)
                val comments = api.getComments(report.number).sortedBy { it.createdAt }
                if (pendingDetailsNumber == report.number) {
                    _details.value = IssueDetailsState(loading = false, issue = issue, comments = comments)
                    if (!issue.state.equals(report.status, ignoreCase = true)) {
                        repo.saveBugReport(report.copy(status = issue.state))
                    }
                }
            } catch (e: Exception) {
                if (pendingDetailsNumber == report.number) {
                    val message = if (e is FeedbackApiException) {
                        e.message
                    } else {
                        "Unable to refresh this report."
                    }
                    _details.value = IssueDetailsState(
                        loading = false,
                        error = message ?: "Unable to refresh this report.",
                    )
                }
            }
        }
    }

    fun refreshDetails(number: Int) {
        pendingDetailsNumber = number
        val current = _details.value
        _details.value = current.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val issue = api.getIssue(number)
                val comments = api.getComments(number).sortedBy { it.createdAt }
                if (pendingDetailsNumber == number) {
                    _details.value = _details.value.copy(loading = false, issue = issue, comments = comments, error = null)
                    val cached = reports.value.firstOrNull { it.number == number }
                    if (cached != null && !issue.state.equals(cached.status, ignoreCase = true)) {
                        repo.saveBugReport(cached.copy(status = issue.state))
                    }
                }
            } catch (e: Exception) {
                if (pendingDetailsNumber == number) {
                    val message = if (e is FeedbackApiException) {
                        e.message
                    } else {
                        "Unable to refresh this report."
                    }
                    _details.value = _details.value.copy(
                        loading = false,
                        error = message ?: "Unable to refresh this report.",
                    )
                }
            }
        }
    }

    fun postReply(number: Int, replyText: String, imageUri: Uri?) {
        if (_details.value.replyState is SubmitState.Submitting ||
            _details.value.replyState is SubmitState.UploadingImage
        ) return
        if (replyText.trim().isEmpty()) {
            _details.value = _details.value.copy(replyState = SubmitState.Error("Please enter a reply."))
            return
        }
        viewModelScope.launch {
            try {
                var attachmentUrl: String? = null
                if (imageUri != null) {
                    _details.value = _details.value.copy(replyState = SubmitState.UploadingImage("Uploading screenshot…"))
                    when (val payload = uriToBase64(getApplication(), imageUri, prefix = "comment-$number")) {
                        is ImagePayload.Error -> {
                            _details.value = _details.value.copy(replyState = SubmitState.Error(payload.message))
                            return@launch
                        }
                        is ImagePayload.Ready -> {
                            val uploaded = api.uploadAsset(payload.fileName, payload.contentBase64)
                            if (uploaded.downloadUrl.isNullOrBlank()) {
                                _details.value = _details.value.copy(
                                    replyState = SubmitState.Error("Screenshot upload failed. Your reply was not posted."),
                                )
                                return@launch
                            }
                            attachmentUrl = uploaded.downloadUrl
                        }
                    }
                }
                _details.value = _details.value.copy(replyState = SubmitState.Submitting)
                val body = buildCommentBody(replyText.trim(), attachmentUrl)
                api.postComment(number, body)
                _details.value = _details.value.copy(replyState = SubmitState.Posted)
                refreshDetails(number)
            } catch (e: Exception) {
                val message = if (e is FeedbackApiException) {
                    e.message
                } else {
                    "Unable to post your reply."
                }
                _details.value = _details.value.copy(
                    replyState = SubmitState.Error(message ?: "Unable to post your reply."),
                )
            }
        }
    }

    fun clearReplyState() {
        _details.value = _details.value.copy(replyState = SubmitState.Idle)
    }

    fun closeDetails() {
        _details.value = IssueDetailsState()
    }

    companion object {
        fun buildIssueBody(
            description: String,
            name: String,
            email: String,
            attachmentUrl: String?,
            diagnostics: String?,
        ): String {
            val sb = StringBuilder()
            sb.appendLine("## Description")
            sb.appendLine()
            sb.appendLine(description)
            if (name.isNotBlank() || email.isNotBlank()) {
                sb.appendLine()
                sb.appendLine("## Contact Info")
                sb.appendLine()
                sb.appendLine("- Name: ${name.ifBlank { "Not provided" }}")
                sb.appendLine("- Email: ${email.ifBlank { "Not provided" }}")
            }
            if (!attachmentUrl.isNullOrBlank()) {
                sb.appendLine()
                sb.appendLine("## Attachment")
                sb.appendLine()
                sb.appendLine("![Screenshot]($attachmentUrl)")
            }
            if (!diagnostics.isNullOrBlank()) {
                sb.appendLine()
                sb.append(diagnostics)
            }
            return sb.toString().trimEnd()
        }

        fun buildCommentBody(replyText: String, attachmentUrl: String?): String {
            val sb = StringBuilder()
            sb.appendLine("## Reply")
            sb.appendLine()
            sb.appendLine(replyText)
            if (!attachmentUrl.isNullOrBlank()) {
                sb.appendLine()
                sb.appendLine("## Attachment")
                sb.appendLine()
                sb.appendLine("![Screenshot]($attachmentUrl)")
            }
            return sb.toString().trimEnd()
        }
    }
}
