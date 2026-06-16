package com.xiaoqi.companion.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4B647C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDE7F2),
    onPrimaryContainer = Color(0xFF152330),
    secondary = Color(0xFF68707D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6EAF0),
    tertiary = Color(0xFF6D6A85),
    tertiaryContainer = Color(0xFFE9E7F2),
    background = Color(0xFFF4F5F8),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE9EDF2),
    onSurface = Color(0xFF171B20),
    onSurfaceVariant = Color(0xFF66707B),
    outline = Color(0xFFD7DCE3),
    outlineVariant = Color(0xFFE6EAF0),
)

@Composable
fun CompanionTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalCompanionSpacing provides CompanionSpacing()) {
        MaterialTheme(
            colorScheme = LightColorScheme,
            typography = CompanionTypography,
            shapes = CompanionShapes,
            content = content,
        )
    }
}
