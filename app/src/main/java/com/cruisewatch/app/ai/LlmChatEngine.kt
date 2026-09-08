package com.cruisewatch.app.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wraps MediaPipe's on-device LLM Inference API. One session per conversation — everything
 * runs locally on the model file downloaded by ModelDownloadManager, no network calls once loaded.
 */
class LlmChatEngine(private val context: Context, private val modelFile: java.io.File) {
    private var llmInference: LlmInference? = null
    private var session: LlmInferenceSession? = null

    fun load() {
        val inferenceOptions = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(2048)
            .build()
        val inference = LlmInference.createFromOptions(context, inferenceOptions)
        llmInference = inference

        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(40)
            .setTemperature(0.6f)
            .build()
        session = LlmInferenceSession.createFromOptions(inference, sessionOptions)
    }

    suspend fun send(systemContext: String, userMessage: String): String = withContext(Dispatchers.Default) {
        val active = session ?: error("Model not loaded")
        active.addQueryChunk("$systemContext\n\nUser: $userMessage\nAssistant:")
        active.generateResponse()
    }

    fun close() {
        session?.close()
        llmInference?.close()
        session = null
        llmInference = null
    }
}
