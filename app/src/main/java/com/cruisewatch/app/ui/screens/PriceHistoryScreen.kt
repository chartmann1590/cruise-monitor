package com.cruisewatch.app.ui.screens

import com.cruisewatch.app.R
import com.cruisewatch.app.i18n.tr
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.cruisewatch.app.data.PriceSnapshot
import com.cruisewatch.app.ui.theme.Coral
import com.cruisewatch.app.ui.theme.Teal
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Composable
fun PriceHistoryScreen(
    snapshots: Flow<List<PriceSnapshot>> = emptyFlow(),
    onBack: () -> Unit = {},
    onAskAssistant: () -> Unit = {},
) {
    val history by snapshots.collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(tr(R.string.price_history_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onAskAssistant) {
                Icon(Icons.Filled.SmartToy, contentDescription = "Ask the assistant")
            }
        }
        Text(
            tr(R.string.price_history_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 48.dp, top = 2.dp, bottom = 8.dp),
        )

        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(tr(R.string.price_history_empty))
            }
            return@Column
        }

        Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            PriceHistoryChart(
                history,
                modifier = Modifier.fillMaxWidth().height(200.dp).padding(16.dp),
            )
        }

        val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
        LazyColumn {
            items(history.reversed()) { snapshot ->
                Card(
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("$${"%.2f".format(snapshot.fare)}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            snapshot.timestamp?.let { dateFormat.format(it) } ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceHistoryChart(history: List<PriceSnapshot>, modifier: Modifier = Modifier) {
    val min = history.minOf { it.fare }
    val max = history.maxOf { it.fare }
    val range = (max - min).takeIf { it > 0 } ?: 1.0

    val progress by animateFloatAsState(targetValue = 1f, animationSpec = tween(900), label = "chart-draw")

    Canvas(modifier = modifier) {
        val stepX = size.width / (history.size - 1).coerceAtLeast(1)
        val points = history.mapIndexed { index, snapshot ->
            val x = index * stepX
            val y = size.height - ((snapshot.fare - min) / range * size.height).toFloat()
            Offset(x, y)
        }

        val visibleCount = (points.size * progress).toInt().coerceIn(1, points.size)
        val visiblePoints = points.take(visibleCount)

        if (visiblePoints.size > 1) {
            val fillPath = Path().apply {
                moveTo(visiblePoints.first().x, size.height)
                visiblePoints.forEach { lineTo(it.x, it.y) }
                lineTo(visiblePoints.last().x, size.height)
                close()
            }
            drawPath(
                fillPath,
                brush = Brush.verticalGradient(listOf(Teal.copy(alpha = 0.35f), Teal.copy(alpha = 0f))),
            )

            val linePath = Path().apply {
                moveTo(visiblePoints.first().x, visiblePoints.first().y)
                visiblePoints.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(
                linePath,
                color = Teal,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 7f, cap = StrokeCap.Round),
            )
        }

        // Highlight the lowest fare — the one that matters most. Y is inverted
        // (0 = top = highest fare), so the lowest fare is the largest y.
        points.maxByOrNull { it.y }?.let { lowest ->
            if (visiblePoints.contains(lowest)) {
                drawCircle(color = Color.White, radius = 9f, center = lowest)
                drawCircle(color = Coral, radius = 6f, center = lowest)
            }
        }
    }
}
