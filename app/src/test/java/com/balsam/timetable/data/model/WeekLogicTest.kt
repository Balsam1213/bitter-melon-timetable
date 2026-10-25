package com.balsam.timetable.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeekLogicTest {

    // 2026-09-07 是周一，共 20 周：第 20 周最后一天 2027-01-24（周日）
    private val start = "2026-09-07"
    private val totalWeeks = 20

    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun `weekOf 开学前与开学当天`() {
        assertNull(WeekLogic.weekOf(d("2026-09-06"), start, totalWeeks))
        assertEquals(1, WeekLogic.weekOf(d("2026-09-07"), start, totalWeeks))
        assertEquals(1, WeekLogic.weekOf(d("2026-09-13"), start, totalWeeks)) // 第 1 周的周日
        assertEquals(2, WeekLogic.weekOf(d("2026-09-14"), start, totalWeeks))
    }

    @Test
    fun `weekOf 学期末边界`() {
        assertEquals(20, WeekLogic.weekOf(d("2027-01-24"), start, totalWeeks))
        assertNull(WeekLogic.weekOf(d("2027-01-25"), start, totalWeeks))
    }

    @Test
    fun `mondayOf 归一化到当周周一`() {
        assertEquals(d("2026-10-19"), WeekLogic.mondayOf(d("2026-10-25"))) // 周日 → 当周周一
        assertEquals(d("2026-09-07"), WeekLogic.mondayOf(d("2026-09-07")))
    }

    @Test
    fun `lessonActive 集合判断`() {
        val weeks = setOf(1, 2, 3, 5, 8, 11)
        assertTrue(WeekLogic.lessonActive(5, weeks))
        assertFalse(WeekLogic.lessonActive(4, weeks))
        assertFalse(WeekLogic.lessonActive(20, weeks))
        assertFalse(WeekLogic.lessonActive(3, emptySet()))
    }

    private data class L(
        override val dayOfWeek: Int,
        override val startSlot: Int,
        override val endSlot: Int,
        override val weekSet: Set<Int>,
    ) : LessonLike

    @Test
    fun `conflicts 同星期同节次周集合相交`() {
        val a = L(1, 1, 2, (1..16).toSet())
        val b = L(1, 2, 3, (8..16).toSet())
        assertTrue(WeekLogic.conflicts(a, b))
    }

    @Test
    fun `conflicts 异星期或相邻节次不冲突`() {
        val a = L(1, 1, 2, (1..16).toSet())
        assertFalse(WeekLogic.conflicts(a, a.copy(dayOfWeek = 2)))
        assertFalse(WeekLogic.conflicts(a, a.copy(startSlot = 3, endSlot = 4)))
    }

    @Test
    fun `conflicts 周集合不相交不冲突`() {
        val a = L(1, 1, 2, (1..8).toSet())
        val b = L(1, 1, 2, (9..16).toSet())
        assertFalse(WeekLogic.conflicts(a, b))
    }

    @Test
    fun `conflicts 单双周互补不冲突`() {
        val odd = L(1, 1, 2, (1..16).filter { it % 2 == 1 }.toSet())
        val even = L(1, 1, 2, (1..16).filter { it % 2 == 0 }.toSet())
        assertFalse(WeekLogic.conflicts(odd, even))
    }

    @Test
    fun `conflicts 跳周集合部分相交即冲突`() {
        val a = L(3, 3, 4, setOf(1, 2, 3, 5, 6, 7, 9))
        val b = L(3, 4, 5, setOf(7, 9, 11, 13)) // 第 7、9 周双方都上且节次相交
        assertTrue(WeekLogic.conflicts(a, b))
    }
}
