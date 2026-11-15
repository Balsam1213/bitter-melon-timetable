package com.balsam.timetable.widget

import com.balsam.timetable.data.db.Course
import com.balsam.timetable.data.db.CourseWithLessons
import com.balsam.timetable.data.db.Lesson
import com.balsam.timetable.data.db.Semester
import com.balsam.timetable.data.db.TimeSlot
import com.balsam.timetable.data.model.HolidayCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

/**
 * 「下一节课」小组件纯逻辑：startAt 语义（上课中也要能显示）、跨天查找、
 * 放假跳过、调休映射、翻面时刻。固定 now = 2026-11-16（周一）10:00。
 */
class NextClassLogicTest {

    private val now = LocalDateTime.of(2026, 11, 16, 10, 0)
    private val semester = Semester(id = 1, name = "测", startDate = "2026-11-09", totalWeeks = 16)

    private val slots = listOf(
        TimeSlot(semesterId = 1, orderIndex = 1, startTime = "08:00", endTime = "08:45"),
        TimeSlot(semesterId = 1, orderIndex = 2, startTime = "10:00", endTime = "10:45"),
        TimeSlot(semesterId = 1, orderIndex = 3, startTime = "14:00", endTime = "14:45"),
    )

    private fun course(name: String, vararg lessons: Lesson) =
        CourseWithLessons(course = Course(id = 1, semesterId = 1, name = name), lessons = lessons.toList())

    private fun lesson(dow: Int, start: Int, end: Int, weeks: String = (1..16).joinToString(","), location: String = "X302") =
        Lesson(courseId = 1, dayOfWeek = dow, startSlot = start, endSlot = end, weeks = weeks, location = location)

    private fun data(
        now: LocalDateTime = this.now,
        semester: Semester? = this.semester,
        holidayCal: HolidayCalendar = HolidayCalendar(),
        courses: List<CourseWithLessons> = emptyList(),
    ) = NextClassLogic.nextClassData(now, semester, slots, courses, holidayCal)

    @Test
    fun `上课中的课标记RUNNING并给出下课时间`() {
        // 10:00-10:45 正在上
        val d = data(courses = listOf(course("高数", lesson(dow = 1, start = 2, end = 2))))
        assertEquals(NextClassState.RUNNING, d.state)
        assertEquals("高数", d.courseName)
        assertEquals("10:45 下课", d.timeLabel)
        assertEquals("还有 45 分钟", d.relativeLabel)
    }

    @Test
    fun `无上课中时取今天最早的下一节`() {
        val d = data(courses = listOf(course("高数", lesson(dow = 1, start = 3, end = 3))))
        assertEquals(NextClassState.UPCOMING, d.state)
        assertEquals("今天 14:00 开课", d.timeLabel)
    }

    @Test
    fun `今天课上完跨天取明天第一节`() {
        val d = data(now = LocalDateTime.of(2026, 11, 16, 20, 0), courses = listOf(course("高数", lesson(dow = 2, start = 1, end = 1))))
        assertEquals(NextClassState.UPCOMING, d.state)
        assertEquals("明天 08:00 开课", d.timeLabel)
        assertEquals("高数", d.courseName)
    }

    @Test
    fun `跨天取的是最早一堂而非第一条课程记录`() {
        // 两条不同课程都在明天：08:00 的英语早于 14:00 的物理
        val d = data(now = LocalDateTime.of(2026, 11, 16, 20, 0), courses = listOf(
            course("物理", lesson(dow = 2, start = 3, end = 3)),
            course("英语", lesson(dow = 2, start = 1, end = 1)),
        ))
        assertEquals("英语", d.courseName)
    }

    @Test
    fun `放假日显示放假态`() {
        val cal = HolidayCalendar(offDays = setOf("2026-11-16"))
        val d = data(holidayCal = cal, courses = listOf(course("高数", lesson(dow = 1, start = 2, end = 2))))
        assertEquals(NextClassState.HOLIDAY, d.state)
        // 放假日不排翻面闹钟
        assertNull(NextClassLogic.nextFlipInstant(now, semester, slots, listOf(course("高数", lesson(dow = 1, start = 2, end = 2))), cal))
    }

    @Test
    fun `调休日按映射周几取课`() {
        // 11/21（周六）被调休为周四的课；now=11/20 20:00，目标恰为明天
        val cal = HolidayCalendar(makeupDays = setOf("2026-11-21"), makeupDow = mapOf("2026-11-21" to 4))
        val d = data(now = LocalDateTime.of(2026, 11, 20, 20, 0), holidayCal = cal, courses = listOf(course("调休课", lesson(dow = 4, start = 1, end = 1))))
        assertEquals(NextClassState.UPCOMING, d.state)
        assertEquals("明天 08:00 开课", d.timeLabel)
    }

    @Test
    fun `7天窗口内没有课显示暂无`() {
        // 2026-11-16 起是第 2 周：weeks="1"（仅第 1 周）在 7 天窗口外
        val d = data(courses = listOf(course("高数", lesson(dow = 1, start = 2, end = 2, weeks = "1"))))
        assertEquals(NextClassState.NO_CLASS, d.state)
    }

    @Test
    fun `未设置学期显示引导`() {
        val d = data(semester = null)
        assertEquals(NextClassState.NO_SEMESTER, d.state)
    }

    @Test
    fun `翻面时刻_上课中为本节下课_等待中为开课`() {
        val flip1 = NextClassLogic.nextFlipInstant(now, semester, slots, listOf(course("高数", lesson(dow = 1, start = 2, end = 2))), HolidayCalendar())
        assertEquals(LocalDateTime.of(2026, 11, 16, 10, 45), flip1)
        val flip2 = NextClassLogic.nextFlipInstant(
            LocalDateTime.of(2026, 11, 16, 11, 0), semester, slots,
            listOf(course("高数", lesson(dow = 1, start = 3, end = 3))), HolidayCalendar(),
        )
        assertEquals(LocalDateTime.of(2026, 11, 16, 14, 0), flip2)
    }

    @Test
    fun `未设置学期与放假不排翻面闹钟`() {
        assertNull(NextClassLogic.nextFlipInstant(now, null, slots, emptyList(), HolidayCalendar()))
    }
}
