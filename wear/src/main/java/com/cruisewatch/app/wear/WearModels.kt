package com.cruisewatch.app.wear

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

/** Mirrors the phone app's data/Models.kt — just the fields the watch actually displays. */
data class TrackedCruise(
    @DocumentId val id: String = "",
    val userId: String = "",
    val ship: String = "",
    val sailDate: String = "",
    val cabinCategory: String = "",
    @get:PropertyName("isGuarantee") val isGuarantee: Boolean = false,
    val farePaid: Double = 0.0,
    val currency: String = "USD",
    val finalPaymentDate: String = "",
    val active: Boolean = true,
)

data class Alert(
    @DocumentId val id: String = "",
    val userId: String = "",
    val cruiseId: String = "",
    val line: String = "",
    val policyId: String = "",
    val currentFare: Double = 0.0,
    val farePaid: Double = 0.0,
    val dropAmount: Double = 0.0,
    val claimed: Boolean = false,
)
