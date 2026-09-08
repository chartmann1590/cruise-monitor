package com.cruisewatch.app.wear

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-wide holder for the latest summary pushed from the phone — updated by the listener service, read by the UI. */
object SummaryStore {
    private val _summary = MutableStateFlow<WearCruiseSummary?>(null)
    val summary: StateFlow<WearCruiseSummary?> = _summary.asStateFlow()

    fun update(summary: WearCruiseSummary) {
        _summary.value = summary
    }
}
