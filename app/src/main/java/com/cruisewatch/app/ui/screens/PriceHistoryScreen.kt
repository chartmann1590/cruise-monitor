package com.cruisewatch.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.cruisewatch.app.data.PriceSnapshot
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Composable
fun PriceHistoryScreen(
    snapshots: Flow<List<PriceSnapshot>> = emptyFlow(),
) {
    val history by snapshots.collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Price history", style = MaterialTheme.typography.titleLarge)

        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No price checks yet — the scraper runs every few hours.")
            }
            return@Column
        }

        PriceHistoryChart(history, modifier = Modifier.fillMaxWidth().height(180.dp).padding(vertical = 16.dp))

        val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
        LazyColumn {
            items(history.reversed()) { snapshot ->
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Text("$${"%.2f".format(snapshot.fare)}", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        snapshot.timestamp?.let { dateFormat.format(it) } ?: "",
                        style = MaterialTheme.typography.bodySmall,
                    )
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

    Canvas(modifier = modifier) {
        val stepX = size.width / (history.size - 1).coerceAtLeast(1)
        val points = history.mapIndexed { index, snapshot ->
            val x = index * stepX
            val y = size.height - ((snapshot.fare - min) / range * size.height).toFloat()
            androidx.compose.ui.geometry.Offset(x, y)
        }
        for (i in 0 until points.size - 1) {
            drawLine(
                color = Color(0xFF0B5FA5),
                start = points[i],
                end = points[i + 1],
                strokeWidth = 6f,
                cap = StrokeCap.Round,
            )
        }
    }
}
