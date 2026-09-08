package com.cruisewatch.app.wear

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

/** Receives the Bluetooth Data Layer summary the phone pushes (WearSync.kt) — the fallback sync path. */
class CruiseWatchDataListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            if (event.dataItem.uri.path != "/cruisewatch/summary") return@forEach

            val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
            DataLayerStore.update(
                parseDataLayerSummary(
                    cruisesJson = dataMap.getString("cruises"),
                    alertsJson = dataMap.getString("alerts"),
                ),
            )
        }
    }
}
