package com.cruisewatch.crosspromo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras

class CrosspromoViewModelFactory(
    private val application: Application,
    private val sourcePackage: String,
    private val placement: String,
    private val baseUrl: String,
    private val versionName: String = "unknown",
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val client = CrosspromoClient(
            baseUrl = baseUrl,
            sourcePackage = sourcePackage,
            versionName = versionName,
        )
        return CrosspromoViewModel(application, client, sourcePackage, placement) as T
    }
}
