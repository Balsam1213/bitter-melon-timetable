package com.balsam.timetable.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeksCodecTest {

    // ---------------- parse / format ----------------

    @Test
    fun `parse 常规与容错`() {
        assertEquals(setOf(1, 2, 11), WeeksCodec.parse("1,2,11"))
        assertEquals(setOf(1, 2, 11), WeeksCodec.parse("11， 2，1")) // 中文逗号 + 空格 + 乱序
        assertEquals(setOf(3), WeeksCodec.parse("3,x,-1,0")) // 非法与非正数丢弃
        assertTrue(WeeksCodec.parse(null).isEmpty())
        assertTrue(WeeksCodec.parse("").isEmpty())
        assertTrue(WeeksCodec.parse("abc").isEmpty())
    }

    @Test
    fun `format 往返一致`() {
        val weeks = setOf(16, 1, 11, 2)
        assertEquals("1,2,11,16", WeeksCodec.format(weeks))
        assertEquals(weeks, WeeksCodec.parse(WeeksCodec.format(weeks)))
        assertEquals("", WeeksCodec.format(emptySet()))
    }

    // ---------------- fromRange / toRange ----------------

    @Test
    fun `fromRange 三种形态展开`() {
        assertEquals((1..16).toSet(), WeeksCodec.fromRange(1, 16, WeekPattern.EVERY))
        assertEquals((1..16).toSet(), WeeksCodec.fromRange(16, 1, WeekPattern.EVERY)) // 起止倒置容错
        assertEquals(listOf(1, 3, 5, 7, 9), WeeksCodec.fromRange(1, 9, WeekPattern.ODD).sorted())
        assertEquals(listOf(2, 4, 6, 8), WeeksCodec.fromRange(1, 9, WeekPattern.EVEN).sorted())
        assertEquals(setOf(5), WeeksCodec.fromRange(5, 5, WeekPattern.EVERY))
    }

    @Test
    fun `toRange 反推与往返`() {
        assertEquals(Triple(1, 16, WeekPattern.EVERY), WeeksCodec.toRange(WeeksCodec.fromRange(1, 16, WeekPattern.EVERY)))
        assertEquals(Triple(1, 9, WeekPattern.ODD), WeeksCodec.toRange(WeeksCodec.fromRange(1, 9, WeekPattern.ODD)))
        assertEquals(Triple(2, 10, WeekPattern.EVEN), WeeksCodec.toRange(WeeksCodec.fromRange(2, 10, WeekPattern.EVEN)))
        assertNull(WeeksCodec.toRange(setOf(1, 2, 3, 5, 6))) // 跳周集合无法反推
        assertNull(WeeksCodec.toRange(emptySet()))
    }

    // ---------------- segments ----------------

    @Test
    fun `segments 游程分段`() {
        assertEquals(listOf(1..8, 11..16), WeeksCodec.segments((1..8).toSet() + (11..16).toSet()))
        assertEquals(listOf(3..3), WeeksCodec.segments(setOf(3)))
        assertTrue(WeeksCodec.segments(emptySet()).isEmpty())
    }

    // ---------------- label ----------------

    @Test
    fun `label 全学期不显示`() {
        assertNull(WeeksCodec.label((1..17).toSet(), 17))
        assertEquals("1-16周", WeeksCodec.label((1..16).toSet(), 17))
    }

    @Test
    fun `label 单双周形态`() {
        assertEquals("3-17周 单周", WeeksCodec.label(WeeksCodec.fromRange(3, 17, WeekPattern.ODD), 20))
        assertEquals("2-16周 双周", WeeksCodec.label(WeeksCodec.fromRange(2, 16, WeekPattern.EVEN), 20))
        assertEquals("3周", WeeksCodec.label(setOf(3), 20)) // 单个奇数周不叫单周
    }

    @Test
    fun `label 任意跳周集合`() {
        assertEquals("1-8,11-16周", WeeksCodec.label((1..8).toSet() + (11..16).toSet(), 20))
        assertEquals("1-3,5周", WeeksCodec.label(setOf(1, 2, 3, 5), 20))
        assertEquals("不上课", WeeksCodec.label(emptySet(), 20))
    }
}
