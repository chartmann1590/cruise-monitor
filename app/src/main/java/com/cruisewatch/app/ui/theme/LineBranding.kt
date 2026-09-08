package com.cruisewatch.app.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.Sailing
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** Cosmetic per-line identity — a brand-ish gradient + icon so each cruise line reads as itself at a glance. */
data class LineBrand(
    val primary: Color,
    val accent: Color,
    val icon: ImageVector,
) {
    val gradient: Brush get() = Brush.linearGradient(listOf(primary, accent))
}

private val brands = mapOf(
    "royal_caribbean" to LineBrand(Color(0xFF0B3D91), Color(0xFF2E9CCA), Icons.Filled.Sailing),
    "carnival" to LineBrand(Color(0xFFE0102B), Color(0xFFFF7A45), Icons.Filled.DirectionsBoat),
    "princess" to LineBrand(Color(0xFF4B2E83), Color(0xFF00B4A6), Icons.Filled.Anchor),
    "celebrity" to LineBrand(Color(0xFF0A2342), Color(0xFF8C9CB4), Icons.Filled.Sailing),
    "norwegian" to LineBrand(Color(0xFF002F6C), Color(0xFFFF6B57), Icons.Filled.DirectionsBoat),
)

private val fallbackBrand = LineBrand(OceanMid, Teal, Icons.Filled.DirectionsBoat)

fun brandFor(lineId: String): LineBrand = brands[lineId] ?: fallbackBrand
