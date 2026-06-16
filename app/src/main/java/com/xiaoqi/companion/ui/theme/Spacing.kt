package com.xiaoqi.companion.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class CompanionSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val section: Dp = 24.dp,
)

val LocalCompanionSpacing = staticCompositionLocalOf { CompanionSpacing() }
