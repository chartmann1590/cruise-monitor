package com.cruisewatch.crosspromo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

@Composable
fun CrossPromoSection(
    viewModel: CrosspromoViewModel = viewModel(),
    modifier: Modifier = Modifier,
    onAppOpened: (CrosspromoApp) -> Unit = {},
) {
    val state by viewModel.state.collectAsState(initial = CrosspromoState.Loading)
    val apps by viewModel.recommendations.collectAsState(initial = emptyList())

    if (apps.isEmpty() && state is CrosspromoState.Loading) {
        CrossPromoLoading(modifier = modifier)
        return
    }

    // Cross-promotion is never mission-critical: on failure or empty results, render nothing
    // and let the host app continue normally rather than showing an error message.
    val appList = apps
    if (appList.isEmpty()) {
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "More from Hartmann Studios",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        appList.forEach { app ->
            LaunchedEffect(app.packageName) {
                viewModel.recordImpression(app)
            }
            CrossPromoCard(
                app = app,
                onClick = {
                    viewModel.recordClick(app)
                    onAppOpened(app)
                },
            )
        }
    }
}

@Composable
fun CrossPromoLoading(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            "More from Hartmann Studios",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Loading recommendations…", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun CrossPromoCard(
    app: CrosspromoApp,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE0E0E0))
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center,
            ) {
                // Neutral fallback letter always sits underneath; a loaded icon draws over it,
                // and a failed/missing icon simply leaves the fallback visible (no broken-image UI).
                Text(
                    app.name?.firstOrNull()?.toString() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!app.iconUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = app.iconUrl,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    app.name ?: "App",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                app.shortDescription?.let { desc ->
                    if (desc.isNotBlank()) {
                        Text(
                            desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    app.category?.let { cat ->
                        Text(
                            cat,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    app.installText?.let { inst ->
                        Text(
                            inst,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    app.isFree?.let { free ->
                        Text(
                            if (free) "Free" else app.priceText ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    app.rating?.let { rating ->
                        Text(
                            "★ ${"%.1f".format(rating)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFFD700),
                        )
                    }
                }
                Text(
                    "VIEW APP",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
