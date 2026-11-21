package com.imagenote.app.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Fallback = darkColorScheme(
    primary = Color(0xFFB7AEFF),
    onPrimary = Color(0xFF221B5C),
    primaryContainer = Color(0xFF3B3285),
    secondary = Color(0xFFFF8FA3),
    background = Color(0xFF0E0E10),
    surface = Color(0xFF141418),
    surfaceContainer = Color(0xFF1C1C22),
)

@Composable
fun ImageNoteTheme(content: @Composable () -> Unit) {
    // Photo editing reads best on dark; follow the One UI wallpaper colours when available.
    val scheme = if (Build.VERSION.SDK_INT >= 31) dynamicDarkColorScheme(LocalContext.current) else Fallback
    MaterialTheme(colorScheme = scheme.copy(background = Color(0xFF0E0E10)), content = content)
}

/** Mic accent stays red regardless of wallpaper colour so it is easy to find. */
val RecordRed = Color(0xFFFF4D6D)
