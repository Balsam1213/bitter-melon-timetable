package com.balsam.timetable.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HolidayLogicTest {

    private val store = HolidayStore(
        offOfficial = setOf("2026-10-01", "2026-10-02"),
        offAdded = setOf("2026-12-25"),
        offRemoved = setOf("2026-10-02"),
        makeupOfficial = setOf("2026-10-10"),
        makeupAdded = setOf("2026-12-12"),
        makeupRemoved = emptySet(),
    )

    @Test
    fun `effective 按 差集合成`() {
        val cal = HolidayLogic.effective(store)
        assertEquals(setOf("2026-10-01", "2026-12-25"), cal.offDays)
        assertEquals(setOf("2026-10-10", "2026-12-12"), cal.makeupDays)
    }

    @Test
    fun `同一日期放假优先于调休`() {
        val cal = HolidayLogic.effective(store.copy(makeupOfficial = setOf("2026-10-01", "2026-10-10")))
        assertTrue("2026-10-01" in cal.offDays)
        assertFalse("2026-10-01" in cal.makeupDays)
    }

    @Test
    fun `标记放假 普通日进added 官方已删日抵消removed`() {
        val s1 = HolidayLogic.setDayStatus(store, "2026-11-11", DayStatus.Off)
        assertTrue("2026-11-11" in s1.offAdded)

        val s2 = HolidayLogic.setDayStatus(store, "2026-10-02", DayStatus.Off)
        assertFalse("2026-10-02" in s2.offRemoved)
        assertFalse("2026-10-02" in s2.offAdded)
    }

    @Test
    fun `取消放假 官方日记入removed 手动日撤出added`() {
        val s1 = HolidayLogic.setDayStatus(store, "2026-10-01", DayStatus.Normal)
        assertTrue("2026-10-01" in s1.offRemoved)

        val s2 = HolidayLogic.setDayStatus(store, "2026-12-25", DayStatus.Normal)
        assertFalse("2026-12-25" in s2.offAdded)
        assertFalse("2026-12-25" in s2.offRemoved)
    }

    @Test
    fun `标记调休让放假让位`() {
        val s1 = HolidayLogic.setDayStatus(store, "2026-10-01", DayStatus.Makeup(3))
        assertTrue("2026-10-01" in s1.offRemoved)
        assertTrue("2026-10-01" in s1.makeupAdded)
        assertEquals(3, s1.makeupDow["2026-10-01"])
    }

    @Test
    fun `调休dow null 表示按当天并清除已有映射`() {
        val s1 = HolidayLogic.setDayStatus(store, "2026-11-14", DayStatus.Makeup(4))
        assertEquals(4, s1.makeupDow["2026-11-14"])

        val s2 = HolidayLogic.setDayStatus(s1, "2026-11-14", DayStatus.Makeup(null))
        assertNull(s2.makeupDow["2026-11-14"])
        assertTrue("2026-11-14" in s2.makeupAdded)
    }

    @Test
    fun `恢复普通日清两侧标记与周几映射`() {
        val s0 = store.copy(makeupDow = mapOf("2026-10-10" to 2))
        val s1 = HolidayLogic.setDayStatus(s0, "2026-10-10", DayStatus.Normal)
        assertTrue("2026-10-10" in s1.makeupRemoved)
        assertNull(s1.makeupDow["2026-10-10"])
        assertFalse("2026-10-10" in s1.makeupOfficial + s1.makeupAdded - s1.makeupRemoved)
    }

    @Test
    fun `setMakeupDow 单独设置与清除`() {
        val s1 = HolidayLogic.setMakeupDow(store, "2026-10-10", 5)
        assertEquals(5, s1.makeupDow["2026-10-10"])
        val s2 = HolidayLogic.setMakeupDow(s1, "2026-10-10", null)
        assertNull(s2.makeupDow["2026-10-10"])
    }

    @Test
    fun `effectiveDayOfWeek 调休按映射 普通日按自然星期`() {
        // 2026-10-10 是周六（java.time：周一=1..周日=7，周六=6）
        val cal = HolidayLogic.effective(store.copy(makeupDow = mapOf("2026-10-10" to 4)))
        assertEquals(4, cal.effectiveDayOfWeek(LocalDate.parse("2026-10-10")))

        // 未映射 → 当天自然星期
        assertEquals(6, HolidayLogic.effective(store).effectiveDayOfWeek(LocalDate.parse("2026-10-10")))

        // 非调休日即使残留映射也按自然星期（2026-10-09 周五）
        val stale = HolidayLogic.effective(store.copy(makeupDow = mapOf("2026-10-09" to 1)))
        assertEquals(5, stale.effectiveDayOfWeek(LocalDate.parse("2026-10-09")))
    }

    @Test
    fun `statusOf 三态`() {
        val cal = HolidayLogic.effective(store)
        assertEquals(DayStatus.Off, cal.statusOf(LocalDate.parse("2026-10-01")))
        assertEquals(DayStatus.Makeup(null), cal.statusOf(LocalDate.parse("2026-10-10")))
        assertEquals(DayStatus.Normal, cal.statusOf(LocalDate.parse("2026-10-09")))
        assertTrue(cal.isOff(LocalDate.parse("2026-12-25")))
        assertFalse(cal.isOff(LocalDate.parse("2026-12-24")))
    }
}

class HolidayJsonParserTest {

    @Test
    fun `holiday-cn 结构双通道解析`() {
        val body = """
            {"year":2026,"papers":[{"name":"国务院办公厅"}],
             "days":[
               {"name":"国庆节","date":"2026-10-01","isOffDay":true},
               {"name":"国庆节","date":"2026-10-07","isOffDay":true},
               {"name":"国庆节","date":"2026-10-10","isOffDay":false}
             ]}
        """.trimIndent()
        val r = HolidayJsonParser.parse(body, 2026)
        assertEquals(setOf("2026-10-01", "2026-10-07"), r.offDays)
        assertEquals(setOf("2026-10-10"), r.makeupDays)
    }

    @Test
    fun `无布尔标记的日期对象忽略`() {
        val body = """{"days":[{"date":"2026-05-01"},{"date":"2026-05-02","isOffDay":true}]}"""
        val r = HolidayJsonParser.parse(body, 2026)
        assertEquals(setOf("2026-05-02"), r.offDays)
        assertTrue(r.makeupDays.isEmpty())
    }

    @Test
    fun `其他年份过滤`() {
        val body = """{"days":[{"date":"2025-01-01","isOffDay":true}]}"""
        val r = HolidayJsonParser.parse(body, 2026)
        assertTrue(r.offDays.isEmpty())
    }

    @Test
    fun `holiday 字段名同样支持`() {
        val body = """[{"date":"2026-01-01","holiday":false}]"""
        val r = HolidayJsonParser.parse(body, 2026)
        assertEquals(emptySet<String>(), r.offDays)
        assertEquals(setOf("2026-01-01"), r.makeupDays)
    }
}
