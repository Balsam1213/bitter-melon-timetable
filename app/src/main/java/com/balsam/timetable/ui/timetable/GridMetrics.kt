package com.balsam.timetable.ui.timetable

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.balsam.timetable.data.model.TextScale

/**
 * 周网格的尺寸与字号，由「字号档位 × 紧凑模式」推导，集中管理避免魔法数散落。
 * 字号影响文字与行高；紧凑模式只压行高与内边距，两者正交。
 */
data class GridMetrics(
    val slotHeight: Dp,
    val bandHeight: Dp,
    val timeColWidth: Dp,
    /** 课名字号（跨多节卡片） */
    val nameSp: TextUnit,
    val nameLineHeight: TextUnit,
    /** 课名字号（单节小卡） */
    val nameCompactSp: TextUnit,
    val nameCompactLineHeight: TextUnit,
    /** 地点 / 周次 / 提示文字字号 */
    val subSp: TextUnit,
    val subLineHeight: TextUnit,
    /** 卡片横向内边距 */
    val cardPaddingH: Dp,
    /** 卡片纵向内边距 */
    val cardPaddingV: Dp,
) {
    companion object {
        fun of(scale: TextScale, compact: Boolean): GridMetrics {
            val slotH: Dp
            val name: TextUnit
            val nameC: TextUnit
            val sub: TextUnit
            when (scale) {
                TextScale.SMALL -> { slotH = 50.dp; name = 10.sp; nameC = 9.sp; sub = 8.sp }
                TextScale.STANDARD -> { slotH = 56.dp; name = 12.sp; nameC = 10.sp; sub = 9.sp }
                TextScale.LARGE -> { slotH = 66.dp; name = 14.sp; nameC = 12.sp; sub = 10.sp }
            }
            return GridMetrics(
                slotHeight = if (compact) (slotH * 0.82f) else slotH,
                bandHeight = if (compact) 26.dp else 34.dp,
                timeColWidth = 44.dp,
                nameSp = name,
                nameLineHeight = (name.value + 2).sp,
                nameCompactSp = nameC,
                nameCompactLineHeight = (nameC.value + 2).sp,
                subSp = sub,
                subLineHeight = (sub.value + 1).sp,
                cardPaddingH = if (compact) 4.dp else 5.dp,
                cardPaddingV = if (compact) 2.dp else 3.dp,
            )
        }
    }
}
