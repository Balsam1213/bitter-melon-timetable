package com.balsam.timetable.widget

import com.balsam.timetable.data.db.Course
import com.balsam.timetable.data.db.CourseWithLessons
import com.balsam.timetable.data.db.Lesson
import com.balsam.timetable.data.model.WeeksCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekWidgetDataTest {

    // 存储格式是升序逗号分隔（"1,2,...,16"），不是区间写法
    private fun weeks(weeks: Iterable<Int>) = WeeksCodec.format(weeks.toSet())

    private fun course(vararg lessons: Lesson) =
        CourseWithLessons(Course(id = 1, semesterId = 1, name = "课"), lessons.toList())

    private fun lesson(
        dayOfWeek: Int,
        startSlot: Int,
        weeks: String,
        location: String = "",
    ) = Lesson(courseId = 1, dayOfWeek = dayOfWeek, startSlot = startSlot, endSlot = startSlot, weeks = weeks, location = location)

    @Test
    fun `按天过滤并按节次排序`() {
        val courses = listOf(
            course(
                lesson(3, 5, weeks(1..16)),
                lesson(3, 1, weeks(1..16), "A101"),
                lesson(2, 2, weeks(1..16)),
            ),
        )
        val entries = weekEntriesFor(courses, week = 4, dayOfWeek = 3)
        assertEquals(listOf(1, 5), entries.map { it.startSlot })
        assertEquals("A101", entries.first().location)
    }

    @Test
    fun `周集合过滤本周不上课的时段`() {
        val courses = listOf(
            course(lesson(1, 1, weeks(1..8)), lesson(1, 3, weeks(9..16))),
        )
        // 第 4 周：1-8 段上课，9-16 段不上
        assertEquals(listOf(1), weekEntriesFor(courses, 4, 1).map { it.startSlot })
        // 第 10 周：只剩 9-16 段
        assertEquals(listOf(3), weekEntriesFor(courses, 10, 1).map { it.startSlot })
    }

    @Test
    fun `多门课合并列出`() {
        val a = CourseWithLessons(
            Course(id = 1, semesterId = 1, name = "高数"),
            listOf(lesson(1, 1, weeks(1..16), "X1")),
        )
        val b = CourseWithLessons(
            Course(id = 2, semesterId = 1, name = "英语"),
            listOf(lesson(1, 3, weeks(listOf(2, 4, 6)), "X2")),
        )
        val entries = weekEntriesFor(listOf(a, b), week = 2, dayOfWeek = 1)
        assertEquals(listOf("高数" to 1, "英语" to 3), entries.map { it.name to it.startSlot })
    }

    @Test
    fun `无课返回空列表`() {
        assertTrue(weekEntriesFor(emptyList(), 1, 1).isEmpty())
        val courses = listOf(course(lesson(5, 1, weeks(1..16))))
        assertTrue(weekEntriesFor(courses, 1, 1).isEmpty())
    }
}
