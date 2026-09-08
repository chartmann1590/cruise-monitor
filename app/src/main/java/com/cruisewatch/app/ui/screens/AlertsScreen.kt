package com.cruisewatch.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cruisewatch.app.data.Alert
import com.cruisewatch.app.data.CruiseLinePolicy
import com.cruisewatch.app.ui.CallButton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Composable
fun AlertsScreen(
    alerts: Flow<List<Alert>> = emptyFlow(),
    policyFor: (String) -> CruiseLinePolicy? = { null },
    onMarkClaimed: (String) -> Unit,
) {
    val alertList by alerts.collectAsState(initial = emptyList())

    if (alertList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No price drops found yet. We're watching 24/7.")
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(alertList, key = { it.id }) { alert ->
            AlertCard(alert, policy = policyFor(alert.line), onMarkClaimed = { onMarkClaimed(alert.id) })
        }
    }
}

@Composable
private fun AlertCard(alert: Alert, policy: CruiseLinePolicy?, onMarkClaimed: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Dropped $${"%.2f".format(alert.dropAmount)}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "New fare: $${"%.2f".format(alert.currentFare)} (you paid $${"%.2f".format(alert.farePaid)})",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                "Applies under: ${alert.policyId}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )

            if (!alert.claimed && policy != null && policy.howToClaim.isNotEmpty()) {
                Text(
                    "How to get your refund",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                policy.howToClaim.forEachIndexed { index, step ->
                    Row(modifier = Modifier.padding(top = 8.dp)) {
                        Text(
                            "${index + 1}.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(step, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (policy.phone.isNotBlank()) {
                    CallButton(policy.phone, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
                }
            }

            Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                if (alert.claimed) {
                    Text("Claimed", style = MaterialTheme.typography.labelLarge)
                } else {
                    TextButton(onClick = onMarkClaimed) {
                        Text("Mark as claimed")
                    }
                }
            }
        }
    }
}
