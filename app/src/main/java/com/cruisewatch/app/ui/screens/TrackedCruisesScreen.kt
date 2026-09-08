package com.cruisewatch.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cruisewatch.app.ads.BannerAd
import com.cruisewatch.app.data.TrackedCruise
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Composable
fun TrackedCruisesScreen(
    cruises: Flow<List<TrackedCruise>> = emptyFlow(),
    onAddCruise: () -> Unit,
    onOpenCruise: (String) -> Unit,
) {
    val cruiseList by cruises.collectAsState(initial = emptyList())

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddCruise) {
                Icon(Icons.Filled.Add, contentDescription = "Add cruise")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (cruiseList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No cruises tracked yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tap + to add one you've already booked",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().weight(1f)) {
                    items(cruiseList, key = { it.id }) { cruise ->
                        TrackedCruiseCard(cruise, onClick = { onOpenCruise(cruise.id) })
                    }
                }
            }

            BannerAd()
        }
    }
}

@Composable
private fun TrackedCruiseCard(cruise: TrackedCruise, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(cruise.ship, style = MaterialTheme.typography.titleMedium)
            Text(
                "${cruise.cabinCategory} · Sailing ${cruise.sailDate}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Paid ${cruise.currency} ${"%.2f".format(cruise.farePaid)}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                "Final payment: ${cruise.finalPaymentDate}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
