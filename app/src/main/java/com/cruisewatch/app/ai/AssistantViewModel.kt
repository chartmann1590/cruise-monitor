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
    private val languagePrefs by lazy { com.cruisewatch.app.i18n.LanguagePrefs(getApplication()) }

    private val _state = MutableStateFlow<AssistantState>(AssistantState.CheckingModel)
    val state: StateFlow<AssistantState> = _state.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private var engine: LlmChatEngine? = null
    private var loadedModelFile: java.io.File? = null
    private var focusCruiseId: String? = null
    private var systemPrimed = false
    private var primedLanguageCode: String? = null

    val recommendedTier = DeviceCapability.recommend(application)
    val deviceRamGb = DeviceCapability.totalRamGb(application)

    fun checkModelState(focusCruiseId: String? = null) {
        this.focusCruiseId = focusCruiseId
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
            loadedModelFile = file
            systemPrimed = false
            primedLanguageCode = null
            runCatching {
                val newEngine = LlmChatEngine(getApplication(), file)
                newEngine.load()
                engine = newEngine
            }.onSuccess {
                _state.value = AssistantState.Ready
                val cruiseId = focusCruiseId
                if (cruiseId != null) {
                    sendFocusedGreeting(cruiseId)
                } else {
                    _messages.value = listOf(
                        ChatMessage(
                            fromUser = false,
                            text = "Hi! I'm your refund assistant. I can see your tracked cruises and any price drops — " +
                                "ask me what to do to get your money back.",
                        ),
                    )
                }
            }.onFailure { e ->
                _state.value = AssistantState.Error(e.message ?: "Couldn't load the model")
            }
        }
    }

    private fun sendFocusedGreeting(cruiseId: String) {
        val activeEngine = engine ?: return
        _isThinking.value = true
        viewModelScope.launch {
            val languageCode = languagePrefs.getSelectedLanguage() ?: com.cruisewatch.app.i18n.SupportedLanguages.ENGLISH.code
            val cruises = runCatching { repository.trackedCruises().first() }.getOrDefault(emptyList())
            val alerts = runCatching { repository.alerts().first() }.getOrDefault(emptyList())
            val systemPrompt = RefundAssistantContext.buildSystemPrompt(
                cruises, alerts, { lineId -> policyRepository.forLine(lineId) }, cruiseId,
                languageCode = languageCode, isAutomatedKickoff = true,
            )
            val kickoff = "Greet me and immediately explain, step by step, exactly what I need to do right now " +
                "to get my refund for this cruise."
            val response = runCatching { activeEngine.send(kickoff, systemPrompt) }
                .getOrElse {
                    "Hi! I can see this cruise's price drop — let me know if you'd like the steps to claim it."
                }
            systemPrimed = true
            primedLanguageCode = languageCode
            _messages.value = listOf(ChatMessage(fromUser = false, text = response))
            _isThinking.value = false
        }
    }

    /**
     * MediaPipe's session can only take the system prompt on its FIRST turn — re-sending it later
     * floods the small context window and degrades quality (see [LlmChatEngine.send]). So if the
     * user changes language mid-conversation via Settings, the only way to honor the new language
     * is a fresh session: reload the model, which naturally resets [systemPrimed] so the next turn
     * sends a system prompt again, this time built with the new language.
     */
    private fun recreateEngineForLanguageChange() {
        val file = loadedModelFile ?: return
        engine?.close()
        val newEngine = LlmChatEngine(getApplication(), file)
        newEngine.load()
        engine = newEngine
        systemPrimed = false
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        if (engine == null) return
        _messages.value = _messages.value + ChatMessage(fromUser = true, text = text)
        _isThinking.value = true
        viewModelScope.launch {
            val languageCode = languagePrefs.getSelectedLanguage() ?: com.cruisewatch.app.i18n.SupportedLanguages.ENGLISH.code
            if (systemPrimed && primedLanguageCode != null && primedLanguageCode != languageCode) {
                recreateEngineForLanguageChange()
            }
            val activeEngine = engine ?: return@launch
            // The system prompt is only sent once, on the first turn of the (possibly just-reset)
            // session — the model's own session state carries the conversation from there.
            val systemPrompt = if (!systemPrimed) {
                val cruises = runCatching { repository.trackedCruises().first() }.getOrDefault(emptyList())
                val alerts = runCatching { repository.alerts().first() }.getOrDefault(emptyList())
                RefundAssistantContext.buildSystemPrompt(
                    cruises, alerts, { lineId -> policyRepository.forLine(lineId) }, focusCruiseId, languageCode = languageCode,
                )
            } else {
                null
            }
            val response = runCatching { activeEngine.send(text, systemPrompt) }
                .getOrElse { "Sorry, something went wrong answering that: ${it.message}" }
            systemPrimed = true
            primedLanguageCode = languageCode
            _messages.value = _messages.value + ChatMessage(fromUser = false, text = response)
            _isThinking.value = false
        }
    }

    override fun onCleared() {
        engine?.close()
    }
}
