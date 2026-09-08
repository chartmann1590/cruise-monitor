package com.cruisewatch.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Sailing
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cruisewatch.app.ads.BannerAd
import com.cruisewatch.app.data.TrackedCruise
import com.cruisewatch.app.ui.SparkleOverlay
import com.cruisewatch.app.ui.WaveHero
import com.cruisewatch.app.ui.theme.OceanGradient
import com.cruisewatch.app.ui.theme.brandFor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TrackedCruisesScreen(
    cruises: Flow<List<TrackedCruise>> = emptyFlow(),
    onAddCruise: () -> Unit,
    onOpenCruise: (String) -> Unit,
) {
    val cruiseList by cruises.collectAsState(initial = emptyList())

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddCruise,
                containerColor = MaterialTheme.colorScheme.secondary,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add cruise") },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            WaveHero(gradient = OceanGradient, height = 150.dp) {
                SparkleOverlay(modifier = Modifier.fillMaxSize())
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.Center) {
                    Text("Your Cruises", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Text(
                        "${cruiseList.size} sailing${if (cruiseList.size == 1) "" else "s"} being watched 24/7",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                }
            }

            if (cruiseList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Sailing,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        )
                        Text(
                            "No cruises tracked yet",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        Text(
                            "Tap \"Add cruise\" to start watching one you've already booked",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().weight(1f)) {
                    itemsIndexed(cruiseList, key = { _, c -> c.id }) { index, cruise ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(tween(300, delayMillis = index * 60)) +
                                slideInVertically(tween(300, delayMillis = index * 60), initialOffsetY = { it / 3 }),
                        ) {
                            TrackedCruiseCard(cruise, onClick = { onOpenCruise(cruise.id) })
                        }
                    }
                }
            }

            BannerAd()
        }
    }
}

@Composable
private fun TrackedCruiseCard(cruise: TrackedCruise, onClick: () -> Unit) {
    val brand = brandFor(cruise.line)
    val daysLeft = runCatching {
        LocalDate.now().until(LocalDate.parse(cruise.finalPaymentDate, DateTimeFormatter.ISO_LOCAL_DATE)).days
    }.getOrNull()

    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().background(brand.gradient).padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(brand.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(cruise.ship, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text(
                        "${cruise.cabinCategory}${if (cruise.isGuarantee) " (Guarantee)" else ""} · Sailing ${cruise.sailDate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        "You paid",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${cruise.currency} ${"%.2f".format(cruise.farePaid)}",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                if (daysLeft != null && daysLeft >= 0) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "$daysLeft",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Text(
                                "days",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 10.dp),
            ) {
                Icon(
                    Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    " Final payment ${cruise.finalPaymentDate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
