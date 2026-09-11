package com.cruisewatch.app.ui.screens

import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cruisewatch.app.R
import com.cruisewatch.app.i18n.tr
import com.cruisewatch.app.data.Alert
import com.cruisewatch.app.data.CruiseLinePolicy
import com.cruisewatch.app.data.TrackedCruise
import com.cruisewatch.app.ui.CallButton
import com.cruisewatch.app.ui.PhotoHero
import com.cruisewatch.app.ui.shareText
import com.cruisewatch.app.ui.theme.Gold
import com.cruisewatch.app.ui.theme.Teal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Composable
fun AlertsScreen(
    alerts: Flow<List<Alert>> = emptyFlow(),
    cruises: Flow<List<TrackedCruise>> = emptyFlow(),
    policyFor: (String) -> CruiseLinePolicy? = { null },
    onMarkClaimed: (String) -> Unit,
    onAskAssistant: (String) -> Unit = {},
) {
    val alertList by alerts.collectAsState(initial = emptyList())
    val cruiseList by cruises.collectAsState(initial = emptyList())
    val cruiseById = cruiseList.associateBy { it.id }

    if (alertList.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PhotoHero(photoRes = R.drawable.hero_celebrate, height = 190.dp) {
                Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Bottom) {
                    Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = Color.White)
                    Text(tr(R.string.alerts_title), style = MaterialTheme.typography.headlineMedium, color = Color.White)
                }
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Text("🔭", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        "No price drops found yet",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        "We're watching your fares 24/7 — you'll get a push notification the moment one drops.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            PhotoHero(photoRes = R.drawable.hero_celebrate, height = 190.dp) {
                Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Bottom) {
                    Text(tr(R.string.alerts_title), style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Text(
                        "${alertList.count { !it.claimed }} unclaimed drop${if (alertList.count { !it.claimed } == 1) "" else "s"} waiting for you",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.92f),
                    )
                }
            }
        }
        items(alertList, key = { it.id }) { alert ->
            AlertCard(
                alert,
                cruise = cruiseById[alert.cruiseId],
                policy = policyFor(alert.line),
                onMarkClaimed = { onMarkClaimed(alert.id) },
                onAskAssistant = { onAskAssistant(alert.cruiseId) },
            )
        }
    }
}

@Composable
private fun AlertCard(
    alert: Alert,
    cruise: TrackedCruise?,
    policy: CruiseLinePolicy?,
    onMarkClaimed: () -> Unit,
    onAskAssistant: () -> Unit,
) {
    val context = LocalContext.current
    val pop by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(500, easing = EaseOutBack),
        label = "alert-pop",
    )

    Card(
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = if (alert.claimed) 1.dp else 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .scale(pop)
            .alpha(pop),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (alert.claimed) MaterialTheme.colorScheme.surfaceVariant else Gold.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (alert.claimed) Icons.Filled.CheckCircle else Icons.Filled.CardGiftcard,
                        contentDescription = null,
                        tint = if (alert.claimed) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFB8860B),
                    )
                }
                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.TrendingDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            " Dropped $${"%.2f".format(alert.dropAmount)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    Text(
                        "New fare $${"%.2f".format(alert.currentFare)} · you paid $${"%.2f".format(alert.farePaid)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                IconButton(onClick = {
                    val shipLine = cruise?.let { "${it.ship}, sailing ${it.sailDate}\n" } ?: ""
                    val steps = policy?.howToClaim
                        ?.mapIndexed { i, step -> "${i + 1}. $step" }
                        ?.joinToString("\n")
                        ?.takeIf { it.isNotBlank() }
                    val phone = policy?.phone?.takeIf { it.isNotBlank() }?.let { "\nCall: $it" } ?: ""
                    val text = buildString {
                        append("I'm watching this cruise's fare with CruiseWatch — good news!\n\n")
                        append(shipLine)
                        append("Price dropped $${"%.2f".format(alert.dropAmount)} — new fare $${"%.2f".format(alert.currentFare)} (paid $${"%.2f".format(alert.farePaid)}).\n")
                        append("Applies under: ${alert.policyId}\n")
                        if (steps != null) append("\nHow to claim it:\n$steps\n")
                        append(phone)
                        append("\n\nTracked with CruiseWatch: https://cruisewatch-app.web.app")
                    }
                    shareText(context, "Your cruise fare dropped!", text)
                }) {
                    Icon(Icons.Filled.Share, contentDescription = "Share this alert")
                }
            }
            Text(
                "Applies under: ${alert.policyId}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, start = 60.dp),
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

            if (!alert.claimed) {
                Button(
                    onClick = onAskAssistant,
                    colors = ButtonDefaults.buttonColors(containerColor = Teal.copy(alpha = 0.14f), contentColor = Teal),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Icon(Icons.Filled.SmartToy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" " + tr(R.string.alerts_ask_assistant), modifier = Modifier.padding(start = 4.dp))
                }
            }

            Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                if (alert.claimed) {
                    Text(
                        "Claimed",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    TextButton(onClick = onMarkClaimed) {
                        Text(tr(R.string.alerts_mark_claimed))
                    }
                }
            }
        }
    }
}
