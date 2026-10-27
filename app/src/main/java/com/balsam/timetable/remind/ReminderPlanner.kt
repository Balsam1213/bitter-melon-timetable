package com.balsam.timetable.remind

import com.balsam.timetable.data.db.CourseWithLessons
import com.balsam.timetable.data.db.Semester
import com.balsam.timetable.data.db.TimeSlot
import com.balsam.timetable.data.model.HolidayCalendar
import com.balsam.timetable.data.model.ReminderMode
import com.balsam.timetable.data.model.WeekLogic
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 提醒排课决策（纯函数）：给定学期/节次/课程/节假日，算出未来 7 天内
 * 应该响的课前提醒事件，按触发时刻排序、最多 MAX_ALARMS 个。
 * 与 AlarmManager 完全解耦（单测覆盖）；ReminderScheduler 负责取数与真正排闹钟。
 */
object ReminderPlanner {

    const val MAX_ALARMS = 50
    const val HORIZON_DAYS = 7L

    data class Occurrence(
        val remindAt: LocalDateTime,
        val startAt: LocalDateTime,
        val courseName: String,
        val location: String,
        val teacher: String,
        val slotText: String,
    )

    fun plan(
        now: LocalDateTime,
        semester: Semester,
        slots: List<TimeSlot>,
        courses: List<CourseWithLessons>,
        holidayCal: HolidayCalendar,
        skipHolidays: Boolean,
        leadMinutes: Int,
    ): List<Occurrence> {
        val holidays = if (skipHolidays) holidayCal.offDays else emptySet()
        val occurrences = mutableListOf<Occurrence>()
        var date: LocalDate = now.toLocalDate()
        val end = now.toLocalDate().plusDays(HORIZON_DAYS)
        while (!date.isAfter(end)) {
            if (date.toString() in holidays) {
                date = date.plusDays(1)
                continue
            }
            // 调休日按用户设置的周几的课表走（未设置为当天自然星期）
            val dow = holidayCal.effectiveDayOfWeek(date)
            val week = WeekLogic.weekOf(date, semester.startDate, semester.totalWeeks)
            if (week != null) {
                for (cwl in courses) {
                    // 单课设置已简化：GLOBAL→跟随全局通知，OFF→不提醒
                    if (ReminderMode.from(cwl.course.reminderMode) == ReminderMode.OFF) continue
                    for (lesson in cwl.lessons) {
                        if (lesson.dayOfWeek != dow) continue
                        if (!WeekLogic.lessonActive(week, lesson.weekSet)) continue
                        val startSlot = slots.getOrNull(lesson.startSlot - 1) ?: continue
                        val startAt = date.atTime(
                            runCatching { LocalTime.parse(startSlot.startTime) }.getOrDefault(LocalTime.of(8, 0))
                        )
                        val remindAt = startAt.minusMinutes(leadMinutes.toLong())
                        if (remindAt.isBefore(now)) continue
                        val endSlot = slots.getOrNull(lesson.endSlot - 1)
                        val range = if (lesson.endSlot > lesson.startSlot) "${lesson.startSlot}-${lesson.endSlot}" else "${lesson.startSlot}"
                        val timeRange = endSlot?.let { "${startSlot.startTime}-${it.endTime}" } ?: startSlot.startTime
                        occurrences.add(
                            Occurrence(
                                remindAt = remindAt,
                                startAt = startAt,
                                courseName = cwl.course.name,
                                location = lesson.location,
                                teacher = cwl.course.teacher,
                                slotText = "第${range}节 $timeRange",
                            )
                        )
                    }
                }
            }
            date = date.plusDays(1)
        }
        occurrences.sortBy { it.remindAt }
        return occurrences.take(MAX_ALARMS)
    }
}
