package com.balsam.timetable.data.model

import com.balsam.timetable.data.db.CourseWithLessons
import com.balsam.timetable.data.db.Lesson
import com.balsam.timetable.data.db.TimeSlot
import java.time.LocalTime

/**
 * 今日视图的纯逻辑：给定今天的星期（调休映射后）、节次表与课程，
 * 算出今天每节课的状态（上课中 / 未开始 / 已结束）与「当前或下一节」。
 * 与 ReminderPlanner / TodayWidget 的取时间约定一致（slots 下标 = 节次号 - 1），
 * 时间解析失败兜底为空（该节课不参与状态判定，UI 显示为无时间行）。
 */
enum class LessonStatus { RUNNING, UPCOMING, DONE }

data class TodayLesson(
    val cwl: CourseWithLessons,
    val lesson: Lesson,
    val startSlot: TimeSlot?,
    val endSlot: TimeSlot?,
    val startAt: LocalTime?,
    val endAt: LocalTime?,
    val status: LessonStatus,
) {
    /** 时间列文案："08:00-09:40"；缺时间时为空串 */
    val timeText: String
        get() = if (startAt != null && endAt != null) "${startSlot?.startTime ?: startAt}-${endSlot?.endTime ?: endAt}" else ""
}

object TodayLogic {

    /** 今天的课程列表：按开始节次排序；week 为 null（未开学/已结课）时不过滤周次（调用方展示放假/假期态） */
    fun todayLessons(
        now: LocalTime,
        dow: Int,
        week: Int?,
        slots: List<TimeSlot>,
        courses: List<CourseWithLessons>,
    ): List<TodayLesson> {
        return courses.flatMap { cwl ->
            cwl.lessons.filter {
                it.dayOfWeek == dow && (week == null || WeekLogic.lessonActive(week, it.weekSet))
            }.map { lesson ->
                val startSlot = slots.getOrNull(lesson.startSlot - 1)
                val endSlot = slots.getOrNull(lesson.endSlot - 1)
                val startAt = startSlot?.let { s -> runCatching { LocalTime.parse(s.startTime) }.getOrNull() }
                val endAt = endSlot?.let { s -> runCatching { LocalTime.parse(s.endTime) }.getOrNull() }
                val status = when {
                    startAt == null || endAt == null -> LessonStatus.UPCOMING
                    endAt <= now -> LessonStatus.DONE
                    startAt <= now -> LessonStatus.RUNNING
                    else -> LessonStatus.UPCOMING
                }
                TodayLesson(cwl, lesson, startSlot, endSlot, startAt, endAt, status)
            }
        }.sortedWith(compareBy({ it.status }, { it.startAt ?: LocalTime.MAX }, { it.startSlot?.orderIndex ?: 0 }))
    }

    /** 状态卡主行：上课中的课优先，其次最早一节未开始的课；全部结束返回 null */
    fun currentOrNext(lessons: List<TodayLesson>): TodayLesson? {
        lessons.firstOrNull { it.status == LessonStatus.RUNNING }?.let { return it }
        return lessons.firstOrNull { it.status == LessonStatus.UPCOMING }
    }

    /** 相对时间文案：target 在 now 之后 → "还有 N 分钟" / "X 小时 Y 分后"；target 已过 → "已过 N 分钟" */
    fun relativeLabel(now: java.time.LocalDateTime, target: java.time.LocalDateTime): String {
        val minutes = java.time.Duration.between(now, target).toMinutes()
        val abs = kotlin.math.abs(minutes)
        val text = when {
            abs < 1 -> "1 分钟"
            abs < 60 -> "$abs 分钟"
            else -> {
                val h = abs / 60
                val m = abs % 60
                if (m == 0L) "$h 小时" else "$h 小时 ${m.toString().padStart(2, '0')} 分"
            }
        }
        return if (minutes >= 0) "还有 $text" else "已过 $text"
    }
}
