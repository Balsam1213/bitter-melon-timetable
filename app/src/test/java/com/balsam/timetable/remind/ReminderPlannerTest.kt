package com.balsam.timetable.remind

import com.balsam.timetable.data.db.Course
import com.balsam.timetable.data.db.CourseWithLessons
import com.balsam.timetable.data.db.Lesson
import com.balsam.timetable.data.db.Semester
import com.balsam.timetable.data.db.TimeSlot
import com.balsam.timetable.data.model.HolidayCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 提醒排课决策纯逻辑。固定 now = 2026-10-28（周三）12:00，horizon 覆盖
 * 10/28（周三）..11/4（周三）共 8 天，对应学期第 8-9 周。
 * 决策只用 LocalDateTime 计算，与系统时区无关；中国无夏令时，不引入时区用例。
 */
class ReminderPlannerTest {

    private val now = LocalDateTime.of(2026, 10, 28, 12, 0)
    private val semester = Semester(id = 1, name = "测", startDate = "2026-09-07", totalWeeks = 16)

    private val slots = listOf(
        "08:00" to "08:45", "08:55" to "09:40", "10:00" to "10:45", "10:55" to "11:40", "11:50" to "12:35",
        "14:00" to "14:45", "14:55" to "15:40", "16:00" to "16:45", "16:55" to "17:40", "17:50" to "18:35",
        "19:00" to "19:45", "19:55" to "20:40", "20:50" to "21:35",
    ).mapIndexed { i, (s, e) -> TimeSlot(semesterId = 1, orderIndex = i + 1, startTime = s, endTime = e) }

    private fun course(
        dow: Int,
        start: Int = 1,
        end: Int = 2,
        weeks: String = (1..16).joinToString(","),
        reminderMode: String = "GLOBAL",
        name: String = "高等数学",
    ) = CourseWithLessons(
        course = Course(id = 1, semesterId = 1, name = name, teacher = "王老师", reminderMode = reminderMode),
        lessons = listOf(
            Lesson(courseId = 1, dayOfWeek = dow, startSlot = start, endSlot = end, weeks = weeks, location = "X302"),
        ),
    )

    private fun plan(
        courses: List<CourseWithLessons>,
        calendar: HolidayCalendar = HolidayCalendar(),
        skipHolidays: Boolean = true,
        lead: Int = 30,
        slotList: List<TimeSlot> = slots,
    ) = ReminderPlanner.plan(now, semester, slotList, courses, calendar, skipHolidays, lead)

    @Test
    fun `常规课程在下周同星期排出提醒`() {
        val list = plan(listOf(course(dow = 1))) // 11/2 周一
        assertEquals(1, list.size)
        assertEquals(LocalDateTime.of(2026, 11, 2, 7, 30), list[0].remindAt)
        assertEquals(LocalDateTime.of(2026, 11, 2, 8, 0), list[0].startAt)
        assertEquals("第1-2节 08:00-09:40", list[0].slotText)
        assertEquals("高等数学", list[0].courseName)
        assertEquals("X302", list[0].location)
    }

    @Test
    fun `放假日不排且开关关闭时照排`() {
        val calendar = HolidayCalendar(offDays = setOf("2026-11-02"))
        assertEquals(0, plan(listOf(course(dow = 1)), calendar = calendar).size)
        assertEquals(1, plan(listOf(course(dow = 1)), calendar = calendar, skipHolidays = false).size)
    }

    @Test
    fun `放假优先于调休`() {
        val calendar = HolidayCalendar(
            offDays = setOf("2026-10-30"),
            makeupDays = setOf("2026-10-30"),
            makeupDow = mapOf("2026-10-30" to 1),
        )
        // weeks 限定第 8 周，候选只剩 10/30（被放假拦截），排除 11/2 正常周一的干扰
        assertEquals(0, plan(listOf(course(dow = 1, weeks = "8")), calendar = calendar).size)
    }

    @Test
    fun `调休日按映射周几取课`() {
        val calendar = HolidayCalendar(makeupDays = setOf("2026-10-30"), makeupDow = mapOf("2026-10-30" to 1))
        // 10/30 是周五，映射为周一的课；weeks 限定第 8 周排除 11/2 正常周一
        val list = plan(listOf(course(dow = 1, weeks = "8")), calendar = calendar)
        assertEquals(1, list.size)
        assertEquals(LocalDateTime.of(2026, 10, 30, 7, 30), list[0].remindAt)
    }

    @Test
    fun `未映射的调休日按当天自然星期`() {
        val calendar = HolidayCalendar(makeupDays = setOf("2026-10-30"))
        assertEquals(1, plan(listOf(course(dow = 5, weeks = "8")), calendar = calendar).size)
        assertEquals(0, plan(listOf(course(dow = 1, weeks = "8")), calendar = calendar).size)
    }

    @Test
    fun `开学前与超学期总周数不排`() {
        val notStarted = semester.copy(startDate = "2026-11-20")
        assertEquals(
            0,
            ReminderPlanner.plan(now, notStarted, slots, listOf(course(dow = 1)), HolidayCalendar(), true, 30).size,
        )
        val short = semester.copy(totalWeeks = 4)
        assertEquals(
            0,
            ReminderPlanner.plan(now, short, slots, listOf(course(dow = 1)), HolidayCalendar(), true, 30).size,
        )
    }

    @Test
    fun `提前量跨午夜归到前一天`() {
        val midnight = listOf(TimeSlot(semesterId = 1, orderIndex = 1, startTime = "00:30", endTime = "01:10"))
        val list = plan(listOf(course(dow = 4, start = 1, end = 1)), lead = 120, slotList = midnight) // 10/29 周四
        assertEquals(1, list.size)
        assertEquals(LocalDateTime.of(2026, 10, 28, 22, 30), list[0].remindAt)
        assertEquals(LocalDateTime.of(2026, 10, 29, 0, 30), list[0].startAt)
        assertEquals("第1节 00:30-01:10", list[0].slotText)
    }

    @Test
    fun `触发时刻已过的当日课不排`() {
        // 第 8 周限定 + 周三：10/28 07:30 已过 now=12:00，被过滤
        val list = plan(listOf(course(dow = 3, weeks = "8")))
        assertEquals(0, list.size)
    }

    @Test
    fun `单课关闭提醒不排`() {
        assertEquals(0, plan(listOf(course(dow = 1, reminderMode = "OFF"))).size)
    }

    @Test
    fun `周次不在集合内的课不排`() {
        assertEquals(0, plan(listOf(course(dow = 1, weeks = "1,2"))).size)
    }

    @Test
    fun `非法节次时间兜底到早上8点`() {
        val broken = listOf(TimeSlot(semesterId = 1, orderIndex = 1, startTime = "垃圾", endTime = "垃圾"))
        val list = plan(listOf(course(dow = 4, start = 1, end = 1)), slotList = broken) // 10/29
        assertEquals(1, list.size)
        assertEquals(LocalDateTime.of(2026, 10, 29, 7, 30), list[0].remindAt)
    }

    @Test
    fun `超过50个按时刻排序截断`() {
        val many = (1..60).map { i ->
            TimeSlot(
                semesterId = 1, orderIndex = i,
                startTime = LocalTime.of(7, 0).plusMinutes(i - 1L).toString(),
                endTime = LocalTime.of(7, 45).plusMinutes(i - 1L).toString(),
            )
        }
        val lessons = (1..60).map { i ->
            Lesson(courseId = 1, dayOfWeek = 3, startSlot = i, endSlot = i, weeks = (1..16).joinToString(","))
        }
        val cwl = CourseWithLessons(
            course = Course(id = 1, semesterId = 1, name = "高等数学"),
            lessons = lessons,
        )
        val list = plan(listOf(cwl), slotList = many)
        // 10/28 的 60 个（06:30-07:29）都早于 now 被过滤，11/4 的 60 个幸存，截断到 50
        assertEquals(ReminderPlanner.MAX_ALARMS, list.size)
        assertEquals(LocalDateTime.of(2026, 11, 4, 6, 30), list.first().remindAt)
        assertTrue(list.zipWithNext().all { (a, b) -> !a.remindAt.isAfter(b.remindAt) })
    }

    @Test
    fun `多门课按触发时刻排序`() {
        val list = plan(
            listOf(
                course(dow = 1, start = 6, end = 7, name = "下午课"),
                course(dow = 1, start = 1, end = 2, name = "早课"),
            ),
        )
        assertEquals(2, list.size)
        assertEquals("早课", list[0].courseName) // 07:30
        assertEquals("下午课", list[1].courseName) // 13:30
        assertTrue(list[0].remindAt.isBefore(list[1].remindAt))
    }
}
