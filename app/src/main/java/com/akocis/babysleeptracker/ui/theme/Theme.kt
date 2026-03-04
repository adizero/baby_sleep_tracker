package com.akocis.babysleeptracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import java.time.LocalTime

private val LightColorScheme = lightColorScheme(
    primary = LavenderPrimary,
    secondary = LavenderSecondary,
    tertiary = SoftPink,
    background = BackgroundLight,
    surface = SurfaceLight,
    onPrimary = SurfaceLight,
    onSecondary = OnSurfaceLight,
    onBackground = OnSurfaceLight,
    onSurface = OnSurfaceLight
)

private val DarkColorScheme = darkColorScheme(
    primary = LavenderPrimaryDark,
    secondary = LavenderSecondaryDark,
    tertiary = SoftPink,
    background = BackgroundDark,
    surface = SurfaceDark,
    onPrimary = SurfaceDark,
    onSecondary = OnSurfaceDark,
    onBackground = OnSurfaceDark,
    onSurface = OnSurfaceDark
)

fun isAutoNightTime(): Boolean {
    val now = LocalTime.now()
    // Approximate sunrise ~7:00, sunset ~19:00
    return now.isBefore(LocalTime.of(7, 0)) || now.isAfter(LocalTime.of(19, 0))
}

fun resolveThemeMode(themeMode: String): Boolean {
    return when (themeMode) {
        "dark" -> true
        "auto" -> isAutoNightTime()
        else -> false
    }
}

@Composable
fun BabySleepTrackerTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
