package com.balsam.timetable.data.model

enum class WeekPattern(val label: String) {
    EVERY("每周"), ODD("单周"), EVEN("双周");

    companion object {
        fun from(s: String?): WeekPattern = entries.firstOrNull { it.name == s } ?: EVERY
    }
}

enum class ReminderMode(val label: String) {
    GLOBAL("跟随全局"), NOTIFICATION("通知"), OFF("关闭");

    companion object {
        fun from(s: String?): ReminderMode = entries.firstOrNull { it.name == s } ?: GLOBAL
    }
}

val DAY_LABELS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

object CourseColors {
    /** 高区分度配色：色相间隔大，无相近色，明度饱和度统一 */
    val PALETTE = listOf(
        0xFF4E6EF2, // 蓝
        0xFFE05571, // 红
        0xFF2FA8A0, // 青
        0xFFEE8C3A, // 橙
        0xFF9C6BF5, // 紫
        0xFF54A648, // 绿
        0xFFD45BA2, // 品红
        0xFFE8B93B, // 黄
        0xFF9A6B53, // 棕
        0xFF64748B, // 石板灰
    )

    fun pick(index: Int): Long = PALETTE[index.mod(PALETTE.size)]
}

data class AppSettings(
    val reminderEnabled: Boolean = true,
    val leadMinutes: Int = 15,
    val defaultMode: ReminderMode = ReminderMode.NOTIFICATION,
    val showWeekend: Boolean = true,
    val holidaySkipEnabled: Boolean = true,
)
