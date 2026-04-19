package com.reprahfit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF7A2E1E),
    onPrimary = Color(0xFFFFF8F5),
    background = Color(0xFFF6F0E8),
    onBackground = Color(0xFF201613),
    surface = Color(0xFFF6F0E8),
    onSurface = Color(0xFF201613),
    surfaceVariant = Color(0xFFE9D8C9),
    onSurfaceVariant = Color(0xFF594844)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE3A995),
    onPrimary = Color(0xFF4A160C),
    background = Color(0xFF17110F),
    onBackground = Color(0xFFF2E7E0),
    surface = Color(0xFF17110F),
    onSurface = Color(0xFFF2E7E0),
    surfaceVariant = Color(0xFF4B3A34),
    onSurfaceVariant = Color(0xFFD7C1B8)
)

@Composable
fun ReprahfitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}

