package com.cruisewatch.app.wear

import org.json.JSONArray
import org.json.JSONObject

data class WearCruise(
    val ship: String,
    val farePaid: Double,
    val currency: String,
    val daysLeft: Int?,
)

data class WearAlert(
    val ship: String,
    val dropAmount: Double,
    val currentFare: Double,
)

data class WearCruiseSummary(
    val cruises: List<WearCruise>,
    val alerts: List<WearAlert>,
)

/** Mirrors the JSON shape the phone app writes in WearSync.kt. */
fun parseWearSummary(cruisesJson: String?, alertsJson: String?): WearCruiseSummary {
    val cruises = runCatching {
        val arr = JSONArray(cruisesJson ?: "[]")
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            WearCruise(
                ship = obj.optString("ship"),
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
            WearAlert(
                ship = obj.optString("ship"),
                dropAmount = obj.optDouble("dropAmount"),
                currentFare = obj.optDouble("currentFare"),
            )
        }
    }.getOrDefault(emptyList())

    return WearCruiseSummary(cruises, alerts)
}
