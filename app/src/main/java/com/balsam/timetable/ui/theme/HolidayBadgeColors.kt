package com.balsam.timetable.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

data class BadgeColors(val bg: Color, val fg: Color)

/**
 * 节假日徽章固定语义色，不随品牌紫主题生成，保证「红=放假日、暖橙=调休上班」
 * 的日历惯例在所有机型上观感一致（浅色/深色各一套）。
 */
@Composable
fun offDayBadgeColors(): BadgeColors =
    if (isSystemInDarkTheme()) BadgeColors(Color(0xFF8C1D18), Color(0xFFF9DEDC))
    else BadgeColors(Color(0xFFF9DEDC), Color(0xFF410E0B))

@Composable
fun makeupDayBadgeColors(): BadgeColors =
    if (isSystemInDarkTheme()) BadgeColors(Color(0xFF5D4200), Color(0xFFFFE08B))
    else BadgeColors(Color(0xFFFFDFB0), Color(0xFF4A2E00))
