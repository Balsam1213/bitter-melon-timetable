package com.balsam.timetable.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 节假日预置数据的守门测试（2027 年安排公布后更新，来源 holiday-cn 2026/2027.json，
 * 2027 出处 gov.cn/zhengce/zhengceku/202611/content_7085102.htm）。
 * 纯 JVM：DEFAULT_HOLIDAYS / DEFAULT_MAKEUP_DAYS 是顶层 val。
 */
class SettingsDefaultsTest {

    @Test
    fun `2026年预置33天放假日全部在集合内`() {
        // 国办发明电〔2025〕7号：元旦3 + 春节9 + 清明3 + 劳动5 + 端午3 + 中秋3 + 国庆7 = 33
        val expected = listOf(
            "2026-01-01", "2026-01-02", "2026-01-03",
            "2026-02-15", "2026-02-16", "2026-02-17", "2026-02-18", "2026-02-19", "2026-02-20", "2026-02-21", "2026-02-22", "2026-02-23",
            "2026-04-04", "2026-04-05", "2026-04-06",
            "2026-05-01", "2026-05-02", "2026-05-03", "2026-05-04", "2026-05-05",
            "2026-06-19", "2026-06-20", "2026-06-21",
            "2026-09-25", "2026-09-26", "2026-09-27",
            "2026-10-01", "2026-10-02", "2026-10-03", "2026-10-04", "2026-10-05", "2026-10-06", "2026-10-07",
        )
        assertTrue(expected.all { it in DEFAULT_HOLIDAYS })
    }

    @Test
    fun `2027年29天放假日全部在集合内`() {
        val expected = listOf(
            "2027-01-01", "2027-01-02", "2027-01-03",
            "2027-02-05", "2027-02-06", "2027-02-07", "2027-02-08", "2027-02-09", "2027-02-10", "2027-02-11", "2027-02-12", "2027-02-13",
            "2027-04-03", "2027-04-04", "2027-04-05",
            "2027-05-01", "2027-05-02", "2027-05-03", "2027-05-04", "2027-05-05",
            "2027-06-09",
            "2027-09-15",
            "2027-10-01", "2027-10-02", "2027-10-03", "2027-10-04", "2027-10-05", "2027-10-06", "2027-10-07",
        )
        assertTrue("缺失：" + expected.filterNot { it in DEFAULT_HOLIDAYS }, expected.all { it in DEFAULT_HOLIDAYS })
        assertEquals(29, expected.size)
    }

    @Test
    fun `2026年6天调休上班日全部在集合内`() {
        val expected = listOf("2026-01-04", "2026-02-14", "2026-02-28", "2026-05-09", "2026-09-20", "2026-10-10")
        assertTrue(expected.all { it in DEFAULT_MAKEUP_DAYS })
    }

    @Test
    fun `2027年5天调休上班日全部在集合内且星期正确`() {
        val expected = listOf("2027-01-31", "2027-02-14", "2027-05-08", "2027-09-26", "2027-10-09")
        assertTrue("缺失：" + expected.filterNot { it in DEFAULT_MAKEUP_DAYS }, expected.all { it in DEFAULT_MAKEUP_DAYS })
        // 调休日都是周末（国办安排只调休周末）
        assertEquals(
            expected,
            expected.filter { LocalDate.parse(it).dayOfWeek == java.time.DayOfWeek.SATURDAY || LocalDate.parse(it).dayOfWeek == java.time.DayOfWeek.SUNDAY },
        )
    }

    @Test
    fun `放假日与调休日不重叠且日期格式合法`() {
        assertTrue(DEFAULT_HOLIDAYS.intersect(DEFAULT_MAKEUP_DAYS).isEmpty())
        (DEFAULT_HOLIDAYS + DEFAULT_MAKEUP_DAYS).forEach {
            assertEquals(it, LocalDate.parse(it).toString()) // 非 yyyy-MM-dd 会在这里抛出或不相等
        }
    }

    @Test
    fun `预置不越界到未公布的年份`() {
        // 只预置已公布的 2026 与 2027
        val years = (DEFAULT_HOLIDAYS + DEFAULT_MAKEUP_DAYS).map { it.substringBefore('-').toInt() }.toSet()
        assertEquals(setOf(2026, 2027), years)
    }
}
