package com.imagenote.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.imagenote.app.R

// Fluorescent orange palette
val NeonOrange = Color(0xFFFF7A1A)
val NeonOrangeBright = Color(0xFFFFA040)
val NeonOrangeDeep = Color(0xFFFF4E00)
val NeonAmber = Color(0xFFFFC14D)

// Near-black surfaces with a faint warm tint
val Ink = Color(0xFF09090B)
val Charcoal = Color(0xFF111114)
val Graphite = Color(0xFF18181D)
val Slate = Color(0xFF212128)
val Steel = Color(0xFF2B2B33)
val Mist = Color(0xFF9A9AA6)

val OrangeGradient = Brush.linearGradient(listOf(NeonOrangeBright, NeonOrange, NeonOrangeDeep))
val OrangeGlass = Brush.verticalGradient(listOf(Color(0x33FF7A1A), Color(0x0DFF7A1A)))

/** Wordmark font for the app title. */
val BrandFont = FontFamily(Font(R.font.pacifico))

private val Scheme = darkColorScheme(
    primary = NeonOrange,
    onPrimary = Color(0xFF1A0A00),
    primaryContainer = Color(0xFF7A3508),
    onPrimaryContainer = NeonAmber,
    secondary = NeonAmber,
    onSecondary = Color(0xFF1A1000),
    secondaryContainer = Color(0xFF6A3008),
    onSecondaryContainer = Color(0xFFFFD9B0),
    tertiary = Color(0xFFFF5C8A),
    background = Ink,
    onBackground = Color(0xFFF2F2F5),
    surface = Charcoal,
    onSurface = Color(0xFFF2F2F5),
    surfaceVariant = Slate,
    onSurfaceVariant = Mist,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = Charcoal,
    surfaceContainer = Graphite,
    surfaceContainerHigh = Slate,
    surfaceContainerHighest = Steel,
    outline = Color(0xFF3C3C46),
    outlineVariant = Color(0xFF2A2A32),
    error = Color(0xFFFF5C5C),
)

private val Base = Typography()
private val AppTypography = Base.copy(
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
    labelSmall = Base.labelSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp),
)

@Composable
fun ImageNoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = AppTypography, content = content)
}

val BrandTitle = TextStyle(fontFamily = BrandFont, fontSize = 26.sp, brush = OrangeGradient)
