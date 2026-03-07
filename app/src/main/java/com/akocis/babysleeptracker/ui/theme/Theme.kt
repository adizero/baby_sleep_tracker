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

fun isAutoNightTime(dayStartHour: Int = 7, dayEndHour: Int = 19): Boolean {
    val now = LocalTime.now()
    val dayStart = LocalTime.of(dayStartHour, 0)
    val dayEnd = LocalTime.of(dayEndHour, 0)
    return if (dayStartHour < dayEndHour) {
        now.isBefore(dayStart) || !now.isBefore(dayEnd)
    } else {
        !now.isBefore(dayEnd) && now.isBefore(dayStart)
    }
}

fun resolveThemeMode(themeMode: String, dayStartHour: Int = 7, dayEndHour: Int = 19): Boolean {
    return when (themeMode) {
        "dark" -> true
        "auto" -> isAutoNightTime(dayStartHour, dayEndHour)
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
