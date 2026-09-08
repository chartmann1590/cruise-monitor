package com.cruisewatch.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A full-bleed photographic banner with a bottom-to-transparent dark scrim
 * (so white headline text stays legible over any photo) and optional
 * [content] laid out on top — this is the "epic vacation photo" look used
 * at the top of every screen instead of a flat app bar.
 */
@Composable
fun PhotoHero(
    @DrawableRes photoRes: Int,
    modifier: Modifier = Modifier,
    height: Dp = 260.dp,
    scrimStrength: Float = 0.8f,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier = modifier.fillMaxWidth().height(height)) {
        Image(
            painter = painterResource(photoRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.15f),
                    0.55f to Color.Black.copy(alpha = scrimStrength * 0.55f),
                    1f to Color.Black.copy(alpha = scrimStrength),
                ),
            ),
        )
        content()
    }
}

/**
 * A translucent "frosted glass" panel that floats over whatever is behind it
 * (typically the bottom of a [PhotoHero]) — real gaussian blur needs API 31+
 * so we fake the frosted look with a semi-transparent tinted surface plus a
 * subtle light border, which reads as glass at normal viewing distance.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    tint: Color = Color(0xFF04141F),
    tintAlpha: Float = 0.72f,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(tint.copy(alpha = tintAlpha))
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape),
        content = content,
    )
}
