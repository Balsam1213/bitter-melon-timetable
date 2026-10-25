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
    /** 上课周次集合（任意周集合，如 1-8,11-16） */
    val weekSet: Set<Int>
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

    /** 某周是否有这节课 */
    fun lessonActive(week: Int, weekSet: Set<Int>): Boolean = week in weekSet

    /** 两个时间段是否真实重叠（同星期、节次相交、周集合有交集） */
    fun conflicts(a: LessonLike, b: LessonLike): Boolean {
        if (a.dayOfWeek != b.dayOfWeek) return false
        if (a.startSlot > b.endSlot || b.startSlot > a.endSlot) return false
        return a.weekSet.any { it in b.weekSet }
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
