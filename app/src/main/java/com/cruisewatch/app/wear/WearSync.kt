package com.cruisewatch.app.wear

import android.content.Context
import android.util.Log
import com.cruisewatch.app.data.CruiseRepository
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "WearSync"
private const val PATH = "/cruisewatch/summary"

/**
 * Pushes a small JSON summary of tracked cruises + unclaimed alerts to any
 * paired Wear OS watch via the Data Layer API. The watch has no Firebase
 * config of its own — it only ever sees what the phone hands it here, kept
 * in sync at the same moments the home screen widget refreshes (sign-in, a
 * new cruise, an FCM price-drop push).
 */
object WearSync {
    suspend fun pushLatest(context: Context, repository: CruiseRepository = CruiseRepository()) {
        val cruises = repository.trackedCruisesOnce()
        val alerts = repository.alertsOnce().filter { !it.claimed }
        val shipByCruiseId = cruises.associateBy({ it.id }, { it.ship })

        val cruisesJson = JSONArray().apply {
            cruises.take(5).forEach { cruise ->
                val daysLeft = runCatching {
                    LocalDate.now().until(LocalDate.parse(cruise.finalPaymentDate, DateTimeFormatter.ISO_LOCAL_DATE)).days
                }.getOrNull()
                put(
                    JSONObject().apply {
                        put("ship", cruise.ship)
                        put("farePaid", cruise.farePaid)
                        put("currency", cruise.currency)
                        put("daysLeft", daysLeft ?: JSONObject.NULL)
                    },
                )
            }
        }

        val alertsJson = JSONArray().apply {
            alerts.take(5).forEach { alert ->
                put(
                    JSONObject().apply {
                        put("ship", shipByCruiseId[alert.cruiseId] ?: "Your cruise")
                        put("dropAmount", alert.dropAmount)
                        put("currentFare", alert.currentFare)
                    },
                )
            }
        }

        runCatching {
            val request = PutDataMapRequest.create(PATH).apply {
                dataMap.putString("cruises", cruisesJson.toString())
                dataMap.putString("alerts", alertsJson.toString())
                dataMap.putLong("updatedAt", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(request).await()
        }.onFailure { e ->
            // No paired watch, or Wearable services unavailable — not an error worth surfacing to the user.
            Log.d(TAG, "Data Layer sync skipped: ${e.message}")
        }
    }
}
