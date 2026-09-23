package com.cruisewatch.crosspromo

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.net.URLEncoder

class CrosspromoApiException(message: String, val httpCode: Int? = null) : IOException(message)

class CrosspromoClient(
    baseUrl: String,
    private val sourcePackage: String,
    private val versionName: String = "unknown",
    private val okHttpClient: OkHttpClient = defaultClient(),
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val jsonMediaType = "application/json".toMediaType()

    val isConfigured: Boolean = baseUrl.isNotBlank()
    private val base = baseUrl.trimEnd('/')

    private fun request(path: String, method: String, bodyJson: String? = null): Request {
        val builder = Request.Builder()
            .url("$base/$path")
            .header("Accept", "application/json")
            .header("User-Agent", "CruiseWatch-Android/$versionName")
        if (bodyJson != null) {
            builder.header("Content-Type", "application/json")
            val body = bodyJson.toRequestBody(jsonMediaType)
            builder.post(body)
        } else {
            builder.get()
        }
        return builder.build()
    }

    private suspend fun <T> execute(path: String, method: String, body: String?, parse: (String) -> T): T =
        withContext(Dispatchers.IO) {
            val response = okHttpClient.newCall(request(path, method, body)).execute()
            response.use {
                val text = it.body?.string().orEmpty()
                if (!it.isSuccessful) {
                    throw CrosspromoApiException(friendlyError(it.code, text), it.code)
                }
                try {
                    parse(text)
                } catch (e: Exception) {
                    Log.e("CrosspromoClient", "Failed to parse response: $text", e)
                    throw CrosspromoApiException("Unexpected response from the cross-promo service.")
                }
            }
        }

    private fun friendlyError(code: Int, text: String): String {
        runCatching {
            val errorText = json.decodeFromString(CrosspromoError.serializer(), text)
            if (errorText.error.isNotBlank()) return errorText.error
        }
        return when (code) {
            400 -> "Invalid request."
            404 -> "Cross-promo service not found."
            413 -> "Request too large."
            429 -> "Too many requests. Please try again later."
            in 500..599 -> "Cross-promo service is temporarily unavailable."
            else -> "Unable to reach the cross-promo service."
        }
    }

    suspend fun getRecommendations(
        sourcePackage: String,
        placement: String,
        limit: Int? = null,
        sessionId: String? = null,
    ): RecommendationResponse {
        val params = buildString {
            append("?sourcePackage=${urlEncode(sourcePackage)}")
            append("&placement=${urlEncode(placement)}")
            if (limit != null) append("&limit=$limit")
            if (sessionId != null) append("&sessionId=${urlEncode(sessionId)}")
        }
        return execute("api/v1/recommendations$params", "GET", null) { text ->
            json.decodeFromString(RecommendationResponse.serializer(), text)
        }
    }

    suspend fun recordEvent(
        event: String,
        targetPackage: String,
        placement: String,
        rankPosition: Int? = null,
        selectionType: String? = null,
        sessionId: String? = null,
        recommendationRequestId: String? = null,
    ) {
        val payload = EventPayload(
            event = event,
            sourcePackage = sourcePackage,
            targetPackage = targetPackage,
            placement = placement,
            rankPosition = rankPosition,
            selectionType = selectionType,
            sessionId = sessionId,
            recommendationRequestId = recommendationRequestId,
            sdkVersion = versionName,
        )
        val body = json.encodeToString(EventPayload.serializer(), payload)
        execute("api/v1/events", "POST", body) { text ->
            json.decodeFromString(EventResponse.serializer(), text)
        }
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")

    suspend fun getHealth(): HealthResponse =
        execute("api/v1/health", "GET", null) { text ->
            json.decodeFromString(HealthResponse.serializer(), text)
        }

    companion object {
        @Volatile
        private var instance: CrosspromoClient? = null

        private fun defaultClient(): OkHttpClient {
            val logging = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BASIC
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            }
            return OkHttpClient.Builder()
                .addInterceptor(logging)
                .build()
        }
    }
}
