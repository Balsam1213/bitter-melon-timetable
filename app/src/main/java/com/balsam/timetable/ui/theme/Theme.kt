package com.balsam.timetable.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF8A6FE8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE9DFFF),
    onPrimaryContainer = Color(0xFF2A1155),
    secondary = Color(0xFF7A67C9),
    secondaryContainer = Color(0xFFE6DEFF),
    onSecondaryContainer = Color(0xFF231552),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC9B8FF),
    onPrimary = Color(0xFF3A1D71),
    primaryContainer = Color(0xFF5236A5),
    onPrimaryContainer = Color(0xFFE9DFFF),
    secondary = Color(0xFFC8BDF5),
    secondaryContainer = Color(0xFF4A3A85),
    onSecondaryContainer = Color(0xFFE6DEFF),
)

/** 统一浅紫色主题（不用动态取色，保证品牌色一致） */
@Composable
fun TimetableTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
