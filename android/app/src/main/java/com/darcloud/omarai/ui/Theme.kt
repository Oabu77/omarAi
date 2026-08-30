package com.darcloud.omarai.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val OmarNavy = Color(0xFF08121F)
val OmarNavySoft = Color(0xFF111F31)
val OmarTeal = Color(0xFF43E6C8)
val OmarBlue = Color(0xFF5A8DFF)
val OmarIce = Color(0xFFF4F8FF)
val OmarSlate = Color(0xFF607087)
val OmarAmber = Color(0xFFFFC857)
val OmarRed = Color(0xFFFF6B78)

private val DarkColors = darkColorScheme(
    primary = OmarTeal,
    onPrimary = OmarNavy,
    primaryContainer = Color(0xFF164C49),
    onPrimaryContainer = Color(0xFFC2FFF4),
    secondary = OmarBlue,
    onSecondary = OmarNavy,
    background = OmarNavy,
    onBackground = OmarIce,
    surface = OmarNavySoft,
    onSurface = OmarIce,
    surfaceVariant = Color(0xFF1B2A3D),
    onSurfaceVariant = Color(0xFFC6D1DF),
    error = OmarRed,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B60),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA9F2E4),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF315DA8),
    onSecondary = Color.White,
    background = OmarIce,
    onBackground = OmarNavy,
    surface = Color.White,
    onSurface = OmarNavy,
    surfaceVariant = Color(0xFFE3EAF3),
    onSurfaceVariant = Color(0xFF3E4A59),
    error = Color(0xFFB3261E),
)

@Composable
fun OmarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = androidx.compose.material3.Typography(),
        content = content,
    )
}
