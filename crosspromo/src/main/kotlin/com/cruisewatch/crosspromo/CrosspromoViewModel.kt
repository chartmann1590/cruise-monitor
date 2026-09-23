package com.cruisewatch.crosspromo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CrosspromoState {
    object Loading : CrosspromoState
    data class Ready(val recommendations: RecommendationResponse) : CrosspromoState
    data class Error(val message: String) : CrosspromoState
}

class CrosspromoViewModel(
    application: Application,
    private val client: CrosspromoClient,
    private val sourcePackage: String,
    private val placement: String,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<CrosspromoState>(CrosspromoState.Loading)
    val state: StateFlow<CrosspromoState> = _state
    private var isRefreshing = false

    val recommendations: StateFlow<List<CrosspromoApp>> =
        state.map { s ->
            (s as? CrosspromoState.Ready)?.recommendations?.apps ?: emptyList()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refresh()
    }

    fun refresh() {
        if (isRefreshing) return
        isRefreshing = true
        _state.value = CrosspromoState.Loading
        viewModelScope.launch {
            try {
                val response = client.getRecommendations(
                    sourcePackage = sourcePackage,
                    placement = placement,
                    limit = 3,
                )
                _state.value = CrosspromoState.Ready(response)
            } catch (e: Exception) {
                _state.value = CrosspromoState.Error(
                    e.message ?: "Unable to load recommendations."
                )
            } finally {
                isRefreshing = false
            }
        }
    }

    fun recordImpression(app: CrosspromoApp) {
        viewModelScope.launch {
            try {
                client.recordEvent(
                    event = "promo_impression",
                    targetPackage = app.packageName,
                    placement = placement,
                    rankPosition = app.rankPosition ?: 0,
                    selectionType = app.selectionType,
                )
            } catch (_: Exception) {
            }
        }
    }

    fun recordClick(app: CrosspromoApp) {
        viewModelScope.launch {
            try {
                client.recordEvent(
                    event = "promo_click",
                    targetPackage = app.packageName,
                    placement = placement,
                    rankPosition = app.rankPosition ?: 0,
                    selectionType = app.selectionType,
                )
            } catch (_: Exception) {
            }
        }
    }
}
