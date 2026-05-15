package com.xiaoqi.companion.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF48695D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9E8E1),
    onPrimaryContainer = Color(0xFF10251E),
    secondary = Color(0xFF7A5C61),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF3DDE1),
    tertiary = Color(0xFF6A5E8C),
    tertiaryContainer = Color(0xFFE7E0F7),
    background = Color(0xFFF9F7F4),
    surface = Color(0xFFFFFBF8),
    surfaceVariant = Color(0xFFE9E3DD),
    onSurface = Color(0xFF24211F),
    onSurfaceVariant = Color(0xFF56504A),
)

@Composable
fun CompanionTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
