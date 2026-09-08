package com.cruisewatch.app.wear

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-wide holder for the latest Bluetooth-synced summary — the fallback for watches with no internet of their own. */
object DataLayerStore {
    private val _summary = MutableStateFlow<DataLayerSummary?>(null)
    val summary: StateFlow<DataLayerSummary?> = _summary.asStateFlow()

    fun update(summary: DataLayerSummary) {
        _summary.value = summary
    }
}
