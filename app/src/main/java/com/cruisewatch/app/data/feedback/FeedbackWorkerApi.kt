package com.cruisewatch.app.data.feedback

import com.cruisewatch.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException

/** Thrown for any Worker API failure. Message text is safe to show in the UI. */
class FeedbackApiException(message: String, val httpCode: Int? = null) : IOException(message)

/**
 * Talks only to the Cloudflare feedback Worker — never to the GitHub API
 * directly. No GitHub credentials live in the app.
 */
class FeedbackWorkerApi(
    baseUrl: String = BuildConfig.FEEDBACK_WORKER_URL,
) {
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val jsonMediaType = "application/json".toMediaType()

    val isConfigured: Boolean = baseUrl.isNotBlank()
    private val base = baseUrl.trimEnd('/')

    private val client: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            // Never log bodies: they may contain screenshots (base64) or
            // private report text. Headers-only in debug, silent in release.
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    private fun checkConfigured() {
        if (!isConfigured) throw FeedbackApiException("Feedback service is not configured for this build.")
    }

    private fun request(path: String, method: String, bodyJson: String? = null): Request {
        val builder = Request.Builder()
            .url("$base/$path")
            .header("Accept", "application/json")
            .header("User-Agent", "CruiseWatch-Android/${BuildConfig.VERSION_NAME}")
        if (bodyJson != null) {
            builder.header("Content-Type", "application/json")
            val body = bodyJson.toRequestBody(jsonMediaType)
            when (method) {
                "POST" -> builder.post(body)
                "PUT" -> builder.put(body)
                "PATCH" -> builder.patch(body)
                else -> builder.method(method, body)
            }
        } else {
            when (method) {
                "GET" -> builder.get()
                "DELETE" -> builder.delete()
                else -> builder.method(method, null)
            }
        }
        return builder.build()
    }

    private suspend fun <T> execute(path: String, method: String, body: String?, parse: (String) -> T): T =
        withContext(Dispatchers.IO) {
            checkConfigured()
            val response = client.newCall(request(path, method, body)).execute()
            response.use {
                val text = it.body?.string().orEmpty()
                if (!it.isSuccessful) {
                    throw FeedbackApiException(friendlyError(it.code, text), it.code)
                }
                try {
                    parse(text)
                } catch (e: Exception) {
                    throw FeedbackApiException("Unexpected response from the feedback service.")
                }
            }
        }

    private fun friendlyError(code: Int, text: String): String {
        // Prefer the Worker's safe error message when present.
        runCatching {
            val apiError = json.decodeFromString(ApiError.serializer(), text)
            if (apiError.error.isNotBlank()) return apiError.error
        }
        return when (code) {
            400 -> "Invalid request."
            404 -> "Report not found."
            405 -> "Operation not allowed."
            413 -> "Attachment or report is too large."
            429 -> "Too many requests. Please try again later."
            in 500..599 -> "Feedback service is temporarily unavailable."
            else -> "Unable to reach the feedback service."
        }
    }

    suspend fun createIssue(title: String, body: String): FeedbackIssue =
        execute("api/issues", "POST", json.encodeToString(CreateIssueRequest.serializer(), CreateIssueRequest(title, body))) { text ->
            json.decodeFromString(FeedbackIssue.serializer(), text)
        }

    suspend fun getIssue(number: Int): FeedbackIssue =
        execute("api/issues/$number", "GET", null) { text ->
            json.decodeFromString(FeedbackIssue.serializer(), text)
        }

    suspend fun getComments(number: Int): List<FeedbackComment> =
        execute("api/issues/$number/comments", "GET", null) { text ->
            json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(FeedbackComment.serializer()), text)
        }

    suspend fun postComment(number: Int, body: String): FeedbackComment =
        execute("api/issues/$number/comments", "POST", json.encodeToString(PostCommentRequest.serializer(), PostCommentRequest(body))) { text ->
            json.decodeFromString(FeedbackComment.serializer(), text)
        }

    suspend fun uploadAsset(fileName: String, contentBase64: String): UploadAssetResponse =
        execute("api/assets", "POST", json.encodeToString(UploadAssetRequest.serializer(), UploadAssetRequest(fileName, contentBase64))) { text ->
            json.decodeFromString(UploadAssetResponse.serializer(), text)
        }
}
