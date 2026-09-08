package com.cruisewatch.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Cruise-vacation palette: deep ocean teal as the anchor, sunset coral and
// sandy gold as the "fun" accents that show up on badges, gradients and CTAs.
val OceanDeep = Color(0xFF012A4A)
val OceanMid = Color(0xFF01497C)
val Teal = Color(0xFF00B4A6)
val TealLight = Color(0xFF6FEDDD)
val Coral = Color(0xFFFF6B57)
val CoralLight = Color(0xFFFFA98F)
val Gold = Color(0xFFFFC94D)
val Sand = Color(0xFFFFF8EC)
val Ink = Color(0xFF0B1D2A)

val OceanGradient = Brush.linearGradient(listOf(OceanDeep, OceanMid, Teal))
val SunsetGradient = Brush.linearGradient(listOf(Coral, Gold))
val CelebrationGradient = Brush.linearGradient(listOf(Color(0xFF00C9A7), Color(0xFF00B4D8)))

private val LightColors = lightColorScheme(
    primary = OceanMid,
    onPrimary = Color.White,
    primaryContainer = TealLight,
    onPrimaryContainer = OceanDeep,
    secondary = Coral,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE1D6),
    onSecondaryContainer = Color(0xFF5C1E0F),
    tertiary = Gold,
    onTertiary = OceanDeep,
    tertiaryContainer = Color(0xFFFFEEC2),
    onTertiaryContainer = Color(0xFF4A3800),
    background = Sand,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE7F3F2),
    onSurfaceVariant = Color(0xFF3F4948),
    error = Color(0xFFD64545),
)

private val DarkColors = darkColorScheme(
    primary = TealLight,
    onPrimary = OceanDeep,
    primaryContainer = OceanMid,
    onPrimaryContainer = TealLight,
    secondary = CoralLight,
    onSecondary = Color(0xFF3D0F05),
    secondaryContainer = Color(0xFF6B2A17),
    onSecondaryContainer = Color(0xFFFFE1D6),
    tertiary = Gold,
    onTertiary = Color(0xFF3D2E00),
    background = Color(0xFF061826),
    onBackground = Color(0xFFE3F2F1),
    surface = Color(0xFF0C2333),
    onSurface = Color(0xFFE3F2F1),
    surfaceVariant = Color(0xFF17323E),
    onSurfaceVariant = Color(0xFFB8CBCA),
    error = Color(0xFFFF8A80),
)

private val PlayfulShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

private val PlayfulTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp,
        letterSpacing = 0.2.sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        letterSpacing = 0.2.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
    ),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp),
)

@Composable
fun CruiseWatchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colorScheme, typography = PlayfulTypography, shapes = PlayfulShapes, content = content)
}
