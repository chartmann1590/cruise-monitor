package com.cruisewatch.app.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.gms.wearable.Wearable

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The listener service only fires on new data — if the phone synced before this
        // activity ever ran, ask the Data Layer for what's already there.
        Wearable.getDataClient(this).dataItems.addOnSuccessListener { buffer ->
            buffer.forEach { item ->
                if (item.uri.path == "/cruisewatch/summary") {
                    val dataMap = com.google.android.gms.wearable.DataMapItem.fromDataItem(item).dataMap
                    SummaryStore.update(
                        parseWearSummary(dataMap.getString("cruises"), dataMap.getString("alerts")),
                    )
                }
            }
            buffer.release()
        }

        setContent {
            MaterialTheme {
                CruiseWatchWearApp()
            }
        }
    }
}

@Composable
fun CruiseWatchWearApp() {
    val summary by SummaryStore.summary.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            summary == null -> CenteredMessage("Open CruiseWatch on your phone to sync")
            summary!!.alerts.isEmpty() && summary!!.cruises.isEmpty() -> CenteredMessage("No cruises tracked yet")
            else -> {
                ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Text(
                            "CruiseWatch",
                            style = MaterialTheme.typography.title3,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    if (summary!!.alerts.isNotEmpty()) {
                        item {
                            Text(
                                "Price drops",
                                style = MaterialTheme.typography.caption2,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                            )
                        }
                        items(summary!!.alerts) { alert ->
                            Chip(
                                onClick = {},
                                label = { Text("🎉 $${"%.0f".format(alert.dropAmount)} off") },
                                secondaryLabel = { Text(alert.ship) },
                                colors = ChipDefaults.chipColors(backgroundColor = androidx.compose.ui.graphics.Color(0xFF5C2A1E)),
                            )
                        }
                    }
                    if (summary!!.cruises.isNotEmpty()) {
                        item {
                            Text(
                                "Tracked cruises",
                                style = MaterialTheme.typography.caption2,
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                            )
                        }
                        items(summary!!.cruises) { cruise ->
                            Chip(
                                onClick = {},
                                label = { Text(cruise.ship) },
                                secondaryLabel = {
                                    Text(
                                        "${cruise.currency} ${"%.0f".format(cruise.farePaid)}" +
                                            (cruise.daysLeft?.let { " · ${it}d left" } ?: ""),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.body2)
    }
}
