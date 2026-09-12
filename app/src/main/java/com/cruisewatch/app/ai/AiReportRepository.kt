package com.cruisewatch.app.ai

import com.cruisewatch.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class AiReportReason(val wireValue: String) {
    INACCURATE("inaccurate"),
    UNSAFE("unsafe"),
    OFF_TOPIC("offtopic"),
    OTHER("other"),
}

/**
 * Sends a user-flagged AI assistant exchange to the reports Worker. This is the one deliberate exception to
 * the assistant's otherwise fully on-device, nothing-ever-sent design — it only fires when the user explicitly
 * taps "report" on a specific message, and only sends that one exchange (see the privacy policy).
 */
class AiReportRepository {

    /** Fire-and-forget: a failed report should never disrupt the chat UI, so failures are swallowed. */
    suspend fun submitReport(
        userMessage: String,
        aiMessage: String,
        reason: AiReportReason,
        reasonDetail: String?,
    ) {
        withContext(Dispatchers.IO) {
            runCatching {
                val url = URL("$REPORTS_ENDPOINT/report")
                (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    setRequestProperty("Content-Type", "application/json")
                    val body = JSONObject().apply {
                        put("userMessage", userMessage)
                        put("aiMessage", aiMessage)
                        put("reasonCategory", reason.wireValue)
                        put("reasonDetail", reasonDetail)
                        put("appVersion", BuildConfig.VERSION_NAME)
                    }
                    outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                    responseCode
                    disconnect()
                }
            }
        }
    }

    companion object {
        private const val REPORTS_ENDPOINT = "https://cruisewatch-ai-reports.charles-h-hartmann1.workers.dev"
    }
}
