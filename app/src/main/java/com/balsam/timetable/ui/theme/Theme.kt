package com.balsam.timetable.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.balsam.timetable.data.model.ColorTheme

/** 每主题只定制品牌相关的 7 个角色，其余维持 Material3 默认 */
private data class ThemeColors(
    val light: ColorScheme,
    val dark: ColorScheme,
    val lightPrimary: Color,
)

private val Purple = ThemeColors(
    light = lightColorScheme(
        primary = Color(0xFF8A6FE8),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE9DFFF),
        onPrimaryContainer = Color(0xFF2A1155),
        secondary = Color(0xFF7A67C9),
        secondaryContainer = Color(0xFFE6DEFF),
        onSecondaryContainer = Color(0xFF231552),
    ),
    dark = darkColorScheme(
        primary = Color(0xFFC9B8FF),
        onPrimary = Color(0xFF3A1D71),
        primaryContainer = Color(0xFF5236A5),
        onPrimaryContainer = Color(0xFFE9DFFF),
        secondary = Color(0xFFC8BDF5),
        secondaryContainer = Color(0xFF4A3A85),
        onSecondaryContainer = Color(0xFFE6DEFF),
    ),
    lightPrimary = Color(0xFF8A6FE8),
)

private val Blue = ThemeColors(
    light = lightColorScheme(
        primary = Color(0xFF4668E8),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFDDE4FF),
        onPrimaryContainer = Color(0xFF0F1E5E),
        secondary = Color(0xFF5B6BC0),
        secondaryContainer = Color(0xFFDEE3FF),
        onSecondaryContainer = Color(0xFF16264F),
    ),
    dark = darkColorScheme(
        primary = Color(0xFFB7C4FF),
        onPrimary = Color(0xFF1A2C6B),
        primaryContainer = Color(0xFF3A4FA5),
        onPrimaryContainer = Color(0xFFDDE4FF),
        secondary = Color(0xFFBFC6F0),
        secondaryContainer = Color(0xFF3B457F),
        onSecondaryContainer = Color(0xFFDEE3FF),
    ),
    lightPrimary = Color(0xFF4668E8),
)

private val Green = ThemeColors(
    light = lightColorScheme(
        primary = Color(0xFF2E9688),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFCFF2EC),
        onPrimaryContainer = Color(0xFF063730),
        secondary = Color(0xFF4CA69B),
        secondaryContainer = Color(0xFFCFF1EA),
        onSecondaryContainer = Color(0xFF0A3F38),
    ),
    dark = darkColorScheme(
        primary = Color(0xFF8FD8CD),
        onPrimary = Color(0xFF063730),
        primaryContainer = Color(0xFF1F6157),
        onPrimaryContainer = Color(0xFFCFF2EC),
        secondary = Color(0xFF9CCFC6),
        secondaryContainer = Color(0xFF2C5A52),
        onSecondaryContainer = Color(0xFFCFF1EA),
    ),
    lightPrimary = Color(0xFF2E9688),
)

private val Pink = ThemeColors(
    light = lightColorScheme(
        primary = Color(0xFFD95C7B),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFD9E1),
        onPrimaryContainer = Color(0xFF5A0F26),
        secondary = Color(0xFFC05A75),
        secondaryContainer = Color(0xFFFFD9E0),
        onSecondaryContainer = Color(0xFF511F2E),
    ),
    dark = darkColorScheme(
        primary = Color(0xFFFFB1C2),
        onPrimary = Color(0xFF5A0F26),
        primaryContainer = Color(0xFF8E3A52),
        onPrimaryContainer = Color(0xFFFFD9E1),
        secondary = Color(0xFFE8B7C2),
        secondaryContainer = Color(0xFF7A3D4E),
        onSecondaryContainer = Color(0xFFFFD9E0),
    ),
    lightPrimary = Color(0xFFD95C7B),
)

private val themes = mapOf(
    ColorTheme.PURPLE to Purple,
    ColorTheme.BLUE to Blue,
    ColorTheme.GREEN to Green,
    ColorTheme.PINK to Pink,
)

/** 设置页主题选项的色点预览（固定用浅色 primary，不随当前深浅色变） */
fun primarySwatchOf(theme: ColorTheme): Color =
    themes[theme]?.lightPrimary ?: Purple.lightPrimary

/** 预设主题（默认兰紫），不用动态取色，保证品牌观感一致 */
@Composable
fun TimetableTheme(
    colorTheme: ColorTheme = ColorTheme.PURPLE,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = themes[colorTheme] ?: Purple
    MaterialTheme(colorScheme = if (darkTheme) colors.dark else colors.light, content = content)
}
