package com.balsam.timetable.data.model

import com.balsam.timetable.data.db.Course
import com.balsam.timetable.data.db.CourseWithLessons
import com.balsam.timetable.data.db.Lesson
import com.balsam.timetable.data.db.TimeSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 今日视图纯逻辑：三态判定、排序、「当前或下一节」优先级、相对时间文案。
 * 固定 now = 10:00（第 2 节 08:55-09:40 已结束、第 3 节 10:00-10:45 上课中、第 4 节未开始）。
 */
class TodayLogicTest {

    private val slots = listOf(
        TimeSlot(semesterId = 1, orderIndex = 1, startTime = "08:00", endTime = "08:45"),
        TimeSlot(semesterId = 1, orderIndex = 2, startTime = "08:55", endTime = "09:40"),
        TimeSlot(semesterId = 1, orderIndex = 3, startTime = "10:00", endTime = "10:45"),
        TimeSlot(semesterId = 1, orderIndex = 4, startTime = "14:00", endTime = "14:45"),
    )

    private fun course(name: String, vararg lessons: Lesson) =
        CourseWithLessons(course = Course(id = 1, semesterId = 1, name = name), lessons = lessons.toList())

    private fun lesson(dow: Int = 1, start: Int, end: Int, weeks: String = (1..16).joinToString(",")) =
        Lesson(courseId = 1, dayOfWeek = dow, startSlot = start, endSlot = end, weeks = weeks, location = "X302")

    private fun todayLessons(now: LocalTime, vararg cwl: CourseWithLessons, week: Int? = 2) =
        TodayLogic.todayLessons(now, dow = 1, week = week, slots = slots, courses = cwl.toList())

    @Test
    fun `三态按时间判定`() {
        // now=10:00：第2节 08:55-09:40 已结束、第3节 10:00-10:45 上课中、第4节 14:00 未开始
        val list = todayLessons(LocalTime.of(10, 0), course("高数", lesson(start = 2, end = 2), lesson(start = 3, end = 3), lesson(start = 4, end = 4)))
        assertEquals(LessonStatus.RUNNING, list[0].status)
        assertEquals(LessonStatus.UPCOMING, list[1].status)
        assertEquals(LessonStatus.DONE, list[2].status)
    }

    @Test
    fun `排序为进行中_未开始_已结束`() {
        // now=10:00：第1节已结束、第3节上课中、第4节未开始——排序后 RUNNING 在前
        val list = todayLessons(LocalTime.of(10, 0), course("高数", lesson(start = 1, end = 1), lesson(start = 3, end = 3), lesson(start = 4, end = 4)))
        assertEquals(listOf(LessonStatus.RUNNING, LessonStatus.UPCOMING, LessonStatus.DONE), list.map { it.status })
    }

    @Test
    fun `当前或下一节_上课中优先于未开始`() {
        val list = todayLessons(LocalTime.of(10, 0), course("高数", lesson(start = 3, end = 3), lesson(start = 4, end = 4)))
        assertEquals("高数", TodayLogic.currentOrNext(list)?.cwl?.course?.name)
        assertEquals(LessonStatus.RUNNING, TodayLogic.currentOrNext(list)?.status)
    }

    @Test
    fun `当前或下一节_全部结束返回null`() {
        val list = todayLessons(LocalTime.of(22, 0), course("高数", lesson(start = 1, end = 2)))
        assertNull(TodayLogic.currentOrNext(list))
    }

    @Test
    fun `非今天星期与周次外的课被过滤`() {
        // 2026-11-16 是第 2 周：weeks="1"（仅第 1 周）在本周外
        val list = todayLessons(
            LocalTime.of(10, 0),
            course("周三课", lesson(dow = 3, start = 3, end = 3)),
            course("周外课", lesson(weeks = "1", start = 3, end = 3)),
        )
        assertEquals(0, list.size)
    }

    @Test
    fun `节次时间解析失败兜底为UPCOMING且时间列为空`() {
        val broken = listOf(TimeSlot(semesterId = 1, orderIndex = 3, startTime = "垃圾", endTime = "垃圾"))
        val list = TodayLogic.todayLessons(LocalTime.of(10, 0), dow = 1, week = 2, slots = broken, courses = listOf(course("坏时间", lesson(start = 3, end = 3))))
        assertEquals(1, list.size)
        assertEquals(LessonStatus.UPCOMING, list[0].status)
        assertEquals("", list[0].timeText)
    }

    @Test
    fun `相对时间文案粒度`() {
        val now = LocalDateTime.of(2026, 11, 16, 9, 30)
        assertEquals("还有 25 分钟", TodayLogic.relativeLabel(now, LocalDateTime.of(2026, 11, 16, 9, 55)))
        assertEquals("还有 1 小时", TodayLogic.relativeLabel(now, LocalDateTime.of(2026, 11, 16, 10, 30)))
        assertEquals("还有 2 小时 05 分", TodayLogic.relativeLabel(now, LocalDateTime.of(2026, 11, 16, 11, 35)))
        assertEquals("已过 10 分钟", TodayLogic.relativeLabel(now, LocalDateTime.of(2026, 11, 16, 9, 20)))
    }

    @Test
    fun `timeText 拼接起止时间`() {
        val list = todayLessons(LocalTime.of(10, 0), course("高数", lesson(start = 3, end = 4)))
        assertEquals("10:00-14:45", list[0].timeText)
        assertEquals(LocalTime.of(10, 0), list[0].startAt)
        assertEquals(LocalTime.of(14, 45), list[0].endAt)
    }
}
