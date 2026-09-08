package com.cruisewatch.app.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    private val auth = FirebaseAuth.getInstance()
    private val repository = WearRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var isSignedIn by remember { mutableStateOf(auth.currentUser != null) }
                if (isSignedIn) {
                    CruiseWatchWearApp(repository)
                } else {
                    WearSignInScreen(auth) { isSignedIn = true }
                }
            }
        }
    }
}

@Composable
fun CruiseWatchWearApp(repository: WearRepository) {
    val cruises by repository.trackedCruises().collectAsState(initial = null)
    val alerts by repository.unclaimedAlerts().collectAsState(initial = emptyList())

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            cruises == null -> CenteredMessage("Loading…")
            alerts.isEmpty() && cruises!!.isEmpty() -> CenteredMessage("No cruises tracked yet")
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
                    if (alerts.isNotEmpty()) {
                        item {
                            Text(
                                "Price drops",
                                style = MaterialTheme.typography.caption2,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                            )
                        }
                        items(alerts) { alert ->
                            Chip(
                                onClick = {},
                                label = { Text("🎉 $${"%.0f".format(alert.dropAmount)} off") },
                                secondaryLabel = { Text("New fare $${"%.0f".format(alert.currentFare)}") },
                                colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF5C2A1E)),
                            )
                        }
                    }
                    if (cruises!!.isNotEmpty()) {
                        item {
                            Text(
                                "Tracked cruises",
                                style = MaterialTheme.typography.caption2,
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                            )
                        }
                        items(cruises!!) { cruise ->
                            Chip(
                                onClick = {},
                                label = { Text(cruise.ship) },
                                secondaryLabel = {
                                    Text("${cruise.currency} ${"%.0f".format(cruise.farePaid)} · sails ${cruise.sailDate}")
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
