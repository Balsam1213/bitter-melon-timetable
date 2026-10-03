package com.balsam.timetable.data.model

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** 冲突检测用的小接口：Lesson 实体与编辑表单都实现它 */
interface LessonLike {
    val dayOfWeek: Int
    val startSlot: Int
    val endSlot: Int
    val startWeek: Int
    val endWeek: Int
    val pattern: WeekPattern
}

object WeekLogic {
    private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun parseDate(s: String): LocalDate = LocalDate.parse(s, ISO)

    fun formatDate(d: LocalDate): String = d.format(ISO)

    /** 归一化到当周周一 */
    fun mondayOf(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())

    /** 学期周次；未开学或已结课返回 null */
    fun weekOf(date: LocalDate, startDate: String, totalWeeks: Int): Int? {
        val start = mondayOf(parseDate(startDate))
        val days = ChronoUnit.DAYS.between(start, date)
        if (days < 0) return null
        val week = (days / 7).toInt() + 1
        return if (week > totalWeeks) null else week
    }

    fun weekMonday(startDate: String, week: Int): LocalDate =
        mondayOf(parseDate(startDate)).plusWeeks((week - 1).toLong())

    fun lessonActive(week: Int, startWeek: Int, endWeek: Int, pattern: WeekPattern): Boolean {
        if (week < startWeek || week > endWeek) return false
        return when (pattern) {
            WeekPattern.EVERY -> true
            WeekPattern.ODD -> week % 2 == 1
            WeekPattern.EVEN -> week % 2 == 0
        }
    }

    /** 两个时间段是否在某个周次真实重叠（同星期、节次相交、周次相交且单双周兼容） */
    fun conflicts(a: LessonLike, b: LessonLike): Boolean {
        if (a.dayOfWeek != b.dayOfWeek) return false
        if (a.startSlot > b.endSlot || b.startSlot > a.endSlot) return false
        if (a.startWeek > b.endWeek || b.startWeek > a.endWeek) return false
        val lo = maxOf(a.startWeek, b.startWeek)
        val hi = minOf(a.endWeek, b.endWeek)
        for (w in lo..hi) {
            if (lessonActive(w, a.startWeek, a.endWeek, a.pattern) &&
                lessonActive(w, b.startWeek, b.endWeek, b.pattern)
            ) return true
        }
        return false
    }
}

fun periodLabel(startTime: String): String {
    val hour = runCatching { LocalTime.parse(startTime).hour }.getOrDefault(8)
    return when {
        hour < 12 -> "上午"
        hour < 18 -> "下午"
        else -> "晚上"
    }
}
