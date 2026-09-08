package com.cruisewatch.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A gradient hero band with a wavy bottom edge, used as a fun "beach horizon"
 * header behind screen titles instead of a flat app bar. [content] is laid
 * out inside the gradient area (above the wave).
 */
@Composable
fun WaveHero(
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
    gradient: Brush,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier = modifier.fillMaxWidth().height(height)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
            val waveHeight = size.height * 0.85f
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(0f, waveHeight)
                cubicTo(
                    size.width * 0.25f, waveHeight + 40f,
                    size.width * 0.75f, waveHeight - 40f,
                    size.width, waveHeight,
                )
                lineTo(size.width, 0f)
                close()
            }
            drawPath(path, brush = gradient)
        }
        content()
    }
}

/** A subtle repeating "sparkle" dot pattern to scatter over a hero gradient for extra fun. */
@Composable
fun SparkleOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val dots = listOf(
            Offset(size.width * 0.12f, size.height * 0.2f) to 3f,
            Offset(size.width * 0.85f, size.height * 0.15f) to 4f,
            Offset(size.width * 0.65f, size.height * 0.35f) to 2f,
            Offset(size.width * 0.3f, size.height * 0.45f) to 2.5f,
            Offset(size.width * 0.92f, size.height * 0.4f) to 3f,
        )
        dots.forEach { (offset, radius) ->
            drawCircle(color = Color.White.copy(alpha = 0.55f), radius = radius, center = offset)
        }
    }
}
