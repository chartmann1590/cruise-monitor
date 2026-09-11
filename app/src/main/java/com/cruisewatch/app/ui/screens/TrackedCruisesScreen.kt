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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Sailing
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.cruisewatch.app.R
import com.cruisewatch.app.ads.BannerAd
import com.cruisewatch.app.data.TrackedCruise
import com.cruisewatch.app.i18n.tr
import com.cruisewatch.app.ui.GlassPanel
import com.cruisewatch.app.ui.PhotoHero
import com.cruisewatch.app.ui.theme.Coral
import com.cruisewatch.app.ui.theme.Gold
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
    onOpenSettings: () -> Unit,
) {
    val cruiseList by cruises.collectAsState(initial = emptyList())

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddCruise,
                containerColor = MaterialTheme.colorScheme.secondary,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(tr(R.string.cruises_add_cruise)) },
                modifier = Modifier.padding(bottom = 64.dp),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PhotoHero(photoRes = R.drawable.hero_cruises, height = 210.dp) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Text(tr(R.string.cruises_title), style = MaterialTheme.typography.headlineMedium, color = Color.White)
                        Text(
                            tr(
                                R.string.cruises_sailing_count,
                                cruiseList.size,
                                if (cruiseList.size == 1) "" else "s",
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f),
                        )
                    }
                    IconButton(onClick = onOpenSettings, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                        Icon(Icons.Filled.Settings, contentDescription = tr(R.string.settings_title), tint = Color.White)
                    }
                }
            }

            if (cruiseList.isEmpty()) {
                GlassPanel(
                    modifier = Modifier.fillMaxSize().weight(1f).offset(y = (-20).dp),
                    tint = MaterialTheme.colorScheme.surface,
                    tintAlpha = 1f,
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.Sailing,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            )
                            Text(
                                tr(R.string.cruises_empty_title),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                            Text(
                                tr(R.string.cruises_empty_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().weight(1f).offset(y = (-20).dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp),
                ) {
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
                        tr(
                            R.string.cruises_cabin_sailing,
                            cruise.cabinCategory,
                            if (cruise.isGuarantee) " " + tr(R.string.cruises_guarantee_suffix) else "",
                            cruise.sailDate,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                }
                val context = androidx.compose.ui.platform.LocalContext.current
                val shareMessage = tr(
                    R.string.cruises_share_message,
                    cruise.ship,
                    cruise.sailDate,
                    cruise.currency,
                    "%.2f".format(cruise.farePaid),
                )
                val shareSubject = tr(R.string.cruises_share_subject)
                IconButton(onClick = {
                    com.cruisewatch.app.ui.shareText(context, shareSubject, shareMessage)
                }) {
                    Icon(Icons.Filled.Share, contentDescription = "Share this cruise", tint = Color.White)
                }
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                tr(R.string.cruises_you_paid_label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${cruise.currency} ${"%.2f".format(cruise.farePaid)}",
                style = MaterialTheme.typography.titleLarge,
            )

            if (daysLeft != null) {
                val urgent = daysLeft in 0..7
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .background(
                            (if (urgent) Coral else Gold).copy(alpha = 0.16f),
                            RoundedCornerShape(50),
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(
                        Icons.Filled.HourglassBottom,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (urgent) Coral else Color(0xFFB8860B),
                    )
                    Text(
                        if (daysLeft >= 0) {
                            " " + tr(R.string.cruises_days_left_urgent, daysLeft, if (daysLeft == 1) "" else "s")
                        } else {
                            " " + tr(R.string.cruises_payment_due_passed)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (urgent) Coral else Color(0xFFB8860B),
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Icon(
                    Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    " " + tr(R.string.cruises_final_payment_date, cruise.finalPaymentDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
