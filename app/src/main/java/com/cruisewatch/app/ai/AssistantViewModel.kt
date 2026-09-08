package com.cruisewatch.app.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cruisewatch.app.data.CruiseRepository
import com.cruisewatch.app.data.PolicyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ChatMessage(val fromUser: Boolean, val text: String)

sealed class AssistantState {
    object CheckingModel : AssistantState()
    data class NeedsSetup(val recommended: LlmModel) : AssistantState()
    data class Downloading(val model: LlmModel, val downloadedMb: Int, val totalMb: Int) : AssistantState()
    object Loading : AssistantState()
    object Ready : AssistantState()
    data class Error(val message: String) : AssistantState()
}

private const val PREFS_NAME = "cruisewatch_ai_prefs"
private const val KEY_MODEL_ID = "chosen_model_id"

class AssistantViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CruiseRepository()
    private val policyRepository by lazy { PolicyRepository(getApplication()) }
    private val prefs by lazy { getApplication<Application>().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE) }

    private val _state = MutableStateFlow<AssistantState>(AssistantState.CheckingModel)
    val state: StateFlow<AssistantState> = _state.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private var engine: LlmChatEngine? = null

    val recommendedTier = DeviceCapability.recommend(application)
    val deviceRamGb = DeviceCapability.totalRamGb(application)

    fun checkModelState() {
        val savedId = prefs.getString(KEY_MODEL_ID, null)
        val model = savedId?.let { LlmModelCatalog.byId(it) } ?: LlmModelCatalog.recommended(recommendedTier)
        val existing = ModelDownloadManager.localFile(getApplication(), model)
        _state.value = if (existing != null) AssistantState.Ready.also { loadEngine(existing) }
        else AssistantState.NeedsSetup(model)
    }

    fun startSetup(model: LlmModel) {
        viewModelScope.launch {
            prefs.edit().putString(KEY_MODEL_ID, model.id).apply()
            ModelDownloadManager.download(getApplication(), model).collect { progress ->
                _state.value = when (progress) {
                    is DownloadState.Progress -> AssistantState.Downloading(model, progress.downloadedMb, progress.totalMb)
                    is DownloadState.Done -> AssistantState.Ready.also { loadEngine(progress.file) }
                    is DownloadState.Failed -> AssistantState.Error(progress.message)
                }
            }
        }
    }

    private fun loadEngine(file: java.io.File) {
        viewModelScope.launch {
            _state.value = AssistantState.Loading
            runCatching {
                val newEngine = LlmChatEngine(getApplication(), file)
                newEngine.load()
                engine = newEngine
            }.onSuccess {
                _state.value = AssistantState.Ready
                _messages.value = listOf(
                    ChatMessage(
                        fromUser = false,
                        text = "Hi! I'm your refund assistant. I can see your tracked cruises and any price drops — " +
                            "ask me what to do to get your money back.",
                    ),
                )
            }.onFailure { e ->
                _state.value = AssistantState.Error(e.message ?: "Couldn't load the model")
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val activeEngine = engine ?: return
        _messages.value = _messages.value + ChatMessage(fromUser = true, text = text)
        _isThinking.value = true
        viewModelScope.launch {
            val cruises = runCatching { repository.trackedCruises().first() }.getOrDefault(emptyList())
            val alerts = runCatching { repository.alerts().first() }.getOrDefault(emptyList())
            val systemPrompt = RefundAssistantContext.buildSystemPrompt(cruises, alerts) { lineId -> policyRepository.forLine(lineId) }
            val response = runCatching { activeEngine.send(systemPrompt, text) }
                .getOrElse { "Sorry, something went wrong answering that: ${it.message}" }
            _messages.value = _messages.value + ChatMessage(fromUser = false, text = response)
            _isThinking.value = false
        }
    }

    override fun onCleared() {
        engine?.close()
    }
}
