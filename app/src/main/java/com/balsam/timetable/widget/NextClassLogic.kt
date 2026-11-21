package com.balsam.timetable.widget

import com.balsam.timetable.data.db.CourseWithLessons
import com.balsam.timetable.data.db.Semester
import com.balsam.timetable.data.db.TimeSlot
import com.balsam.timetable.data.model.HolidayCalendar
import com.balsam.timetable.data.model.WeekLogic
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 「下一节课」小组件的纯逻辑：跨天找当前上课中 / 下一节课（startAt 语义，
 * 区别于 ReminderPlanner 的 remindAt 语义——上课中的课也要能显示）。
 * 7 天窗口枚举，覆盖放假跳过、调休映射、周次过滤；与提醒/今日视图共用
 * 「节次下标 = 节次号 - 1」的取时间约定。
 */
internal enum class NextClassState { NO_SEMESTER, HOLIDAY, RUNNING, UPCOMING, NO_CLASS }

internal data class NextClassData(
    val state: NextClassState,
    /** 顶部小标签：上课中 / 下一节 / 今天放假 / 未设置学期 / 暂无课程 */
    val header: String,
    val courseName: String,
    val location: String,
    /** 时间行：「明天 08:00 开课」 / 「10:45 下课」 */
    val timeLabel: String,
    /** 粗粒度倒计时（按最近一次刷新计算，30 分钟兜底误差可接受） */
    val relativeLabel: String,
)

internal data class ClassMoment(
    val date: LocalDate,
    val courseName: String,
    val location: String,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
)

internal object NextClassLogic {

    private val DAY_FMT = DateTimeFormatter.ofPattern("M月d日", Locale.CHINESE)

    fun nextClassData(
        now: LocalDateTime,
        semester: Semester?,
        slots: List<TimeSlot>,
        courses: List<CourseWithLessons>,
        holidayCal: HolidayCalendar,
    ): NextClassData {
        if (semester == null) {
            return NextClassData(NextClassState.NO_SEMESTER, "未设置学期", "先在应用里设置学期", "", "", "")
        }
        val today = now.toLocalDate()
        if (holidayCal.isOff(today)) {
            return NextClassData(NextClassState.HOLIDAY, "今天放假", "好好休息，调休上班日会正常提醒", "", "", "")
        }
        val running = classMoments(now, semester, slots, courses, holidayCal, today).running
        val upcoming = classMoments(now, semester, slots, courses, holidayCal, today).upcoming
        return when {
            running != null -> NextClassData(
                NextClassState.RUNNING,
                "上课中",
                running.courseName,
                running.location,
                "${running.endAt.format(TIME_FMT)} 下课",
                "还有 ${relativeText(now, running.endAt)}",
            )
            upcoming != null -> NextClassData(
                NextClassState.UPCOMING,
                "下一节",
                upcoming.courseName,
                upcoming.location,
                "${dayLabel(now.toLocalDate(), upcoming.date)} ${upcoming.startAt.format(TIME_FMT)} 开课",
                "还有 ${relativeText(now, upcoming.startAt)}",
            )
            else -> NextClassData(NextClassState.NO_CLASS, "暂无课程", "7 天内没有要上的课", "", "", "")
        }
    }

    /** 小组件下一次需要翻面的时刻：上课中 → 本节下课；等待中 → 下一节开课；其余不排 */
    fun nextFlipInstant(
        now: LocalDateTime,
        semester: Semester?,
        slots: List<TimeSlot>,
        courses: List<CourseWithLessons>,
        holidayCal: HolidayCalendar,
    ): LocalDateTime? {
        if (semester == null || holidayCal.isOff(now.toLocalDate())) return null
        val moments = classMoments(now, semester, slots, courses, holidayCal, now.toLocalDate())
        return moments.running?.endAt ?: moments.upcoming?.startAt
    }

    private class Moments(val running: ClassMoment?, val upcoming: ClassMoment?)

    /** 7 天窗口内收集「上课中最早」与「下一节最早」两堂课 */
    // 日期×课程×节次的三层过滤循环：continue 多出口与嵌套是窗口扫描的固有形态
    @Suppress("LoopWithTooManyJumpStatements", "NestedBlockDepth")
    private fun classMoments(
        now: LocalDateTime,
        semester: Semester,
        slots: List<TimeSlot>,
        courses: List<CourseWithLessons>,
        holidayCal: HolidayCalendar,
        today: LocalDate,
    ): Moments {
        var running: ClassMoment? = null
        var upcoming: ClassMoment? = null
        val end = today.plusDays(7)
        var date = today
        while (!date.isAfter(end)) {
            val week = WeekLogic.weekOf(date, semester.startDate, semester.totalWeeks)
            if (week != null) {
                val dow = holidayCal.effectiveDayOfWeek(date)
                for (cwl in courses) {
                    for (lesson in cwl.lessons) {
                        if (lesson.dayOfWeek != dow) continue
                        if (!WeekLogic.lessonActive(week, lesson.weekSet)) continue
                        val startSlot = slots.getOrNull(lesson.startSlot - 1) ?: continue
                        val endSlot = slots.getOrNull(lesson.endSlot - 1) ?: continue
                        val startT = runCatching { LocalTime.parse(startSlot.startTime) }.getOrNull() ?: continue
                        val endT = runCatching { LocalTime.parse(endSlot.endTime) }.getOrNull() ?: continue
                        val startAt = date.atTime(startT)
                        val endAt = date.atTime(endT)
                        if (!startAt.isAfter(now) && endAt.isAfter(now)) {
                            if (running == null || startAt.isBefore(running.startAt)) {
                                running = ClassMoment(date, cwl.course.name, lesson.location, startAt, endAt)
                            }
                        }
                        if (startAt.isAfter(now)) {
                            if (upcoming == null || startAt.isBefore(upcoming.startAt)) {
                                upcoming = ClassMoment(date, cwl.course.name, lesson.location, startAt, endAt)
                            }
                        }
                    }
                }
            }
            date = date.plusDays(1)
        }
        return Moments(running, upcoming)
    }

    /** 今天 → 省略日期；明天 → 「明天」；更远 → 「M月d日」 */
    private fun dayLabel(today: LocalDate, date: LocalDate): String = when (date) {
        today -> "今天"
        today.plusDays(1) -> "明天"
        else -> date.format(DAY_FMT)
    }

    private fun relativeText(now: LocalDateTime, target: LocalDateTime): String {
        val minutes = java.time.Duration.between(now, target).toMinutes()
        return when {
            minutes < 1 -> "1 分钟"
            minutes < 60 -> "$minutes 分钟"
            else -> {
                val h = minutes / 60
                val m = minutes % 60
                if (m == 0L) "$h 小时" else "$h 小时 ${m.toString().padStart(2, '0')} 分"
            }
        }
    }

    private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
}
