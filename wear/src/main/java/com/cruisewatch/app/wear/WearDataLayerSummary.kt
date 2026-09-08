package com.cruisewatch.app.wear

import org.json.JSONArray

/** One entry from the phone's Bluetooth Data Layer summary — see WearSync.kt on the phone side. */
data class SyncedCruise(
    val id: String,
    val ship: String,
    val cabinCategory: String,
    val sailDate: String,
    val farePaid: Double,
    val currency: String,
    val daysLeft: Int?,
)

data class SyncedAlert(
    val id: String,
    val cruiseId: String,
    val ship: String,
    val dropAmount: Double,
    val currentFare: Double,
    val farePaid: Double,
    val claimed: Boolean,
)

data class DataLayerSummary(
    val cruises: List<SyncedCruise>,
    val alerts: List<SyncedAlert>,
)

fun parseDataLayerSummary(cruisesJson: String?, alertsJson: String?): DataLayerSummary {
    val cruises = runCatching {
        val arr = JSONArray(cruisesJson ?: "[]")
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            SyncedCruise(
                id = obj.optString("id"),
                ship = obj.optString("ship"),
                cabinCategory = obj.optString("cabinCategory"),
                sailDate = obj.optString("sailDate"),
                farePaid = obj.optDouble("farePaid"),
                currency = obj.optString("currency", "USD"),
                daysLeft = if (obj.isNull("daysLeft")) null else obj.optInt("daysLeft"),
            )
        }
    }.getOrDefault(emptyList())

    val alerts = runCatching {
        val arr = JSONArray(alertsJson ?: "[]")
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            SyncedAlert(
                id = obj.optString("id"),
                cruiseId = obj.optString("cruiseId"),
                ship = obj.optString("ship"),
                dropAmount = obj.optDouble("dropAmount"),
                currentFare = obj.optDouble("currentFare"),
                farePaid = obj.optDouble("farePaid"),
                claimed = obj.optBoolean("claimed"),
            )
        }
    }.getOrDefault(emptyList())

    return DataLayerSummary(cruises, alerts)
}
