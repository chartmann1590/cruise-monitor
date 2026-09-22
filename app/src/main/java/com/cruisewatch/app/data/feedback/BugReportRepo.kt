package com.cruisewatch.app.data.feedback

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.feedbackDataStore by preferencesDataStore(name = "feedback_bug_reports")

/** Local history of submitted reports, persisted with DataStore Preferences. */
class BugReportRepo(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    val bugReports: Flow<List<BugReport>> =
        context.feedbackDataStore.data.map { prefs ->
            val raw = prefs[KEY_REPORTS].orEmpty()
            if (raw.isBlank()) {
                emptyList()
            } else {
                runCatching {
                    json.decodeFromString(ListSerializer(BugReport.serializer()), raw)
                }.getOrElse {
                    // Corrupt JSON must never crash the app.
                    emptyList()
                }
            }
        }

    suspend fun getBugReportsList(): List<BugReport> =
        try {
            withTimeout(5_000) { bugReports.first() }
        } catch (e: TimeoutCancellationException) {
            emptyList()
        }

    suspend fun saveBugReport(report: BugReport) {
        val current = getBugReportsList()
        val updated = (listOf(report) + current.filterNot { it.number == report.number })
            .sortedByDescending { it.createdAt }
        persist(updated)
    }

    suspend fun updateBugReports(reports: List<BugReport>) {
        persist(reports.sortedByDescending { it.createdAt })
    }

    private suspend fun persist(reports: List<BugReport>) {
        val raw = json.encodeToString(ListSerializer(BugReport.serializer()), reports)
        context.feedbackDataStore.edit { prefs ->
            prefs[KEY_REPORTS] = raw
        }
    }

    companion object {
        private val KEY_REPORTS = stringPreferencesKey("bug_reports_list")
    }
}
