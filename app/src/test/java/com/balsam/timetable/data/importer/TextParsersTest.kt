package com.balsam.timetable.data.importer

import com.balsam.timetable.data.model.WeekPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextParsersTest {

    // ---------------- cnToInt ----------------

    @Test
    fun `cnToInt 阿拉伯数字直通`() {
        assertEquals(5, TextParsers.cnToInt("5"))
        assertEquals(17, TextParsers.cnToInt("17"))
    }

    @Test
    fun `cnToInt 中文数字`() {
        assertEquals(1, TextParsers.cnToInt("一"))
        assertEquals(9, TextParsers.cnToInt("九"))
        assertEquals(10, TextParsers.cnToInt("十"))
        assertEquals(12, TextParsers.cnToInt("十二"))
        assertEquals(20, TextParsers.cnToInt("二十"))
        assertEquals(23, TextParsers.cnToInt("二十三"))
    }

    @Test
    fun `cnToInt 非法输入返回 null`() {
        assertNull(TextParsers.cnToInt(""))
        assertNull(TextParsers.cnToInt("abc"))
        assertNull(TextParsers.cnToInt("百"))
    }

    // ---------------- numbersIn / rangeIn ----------------

    @Test
    fun `numbersIn 阿拉伯优先`() {
        assertEquals(listOf(3, 4), TextParsers.numbersIn("第3~4节"))
        assertEquals(listOf(3), TextParsers.numbersIn("3-四")) // 有阿拉伯则忽略中文
    }

    @Test
    fun `numbersIn 中文数字兜底`() {
        assertEquals(listOf(3, 4), TextParsers.numbersIn("三、四"))
        assertEquals(listOf(2, 10), TextParsers.numbersIn("第 二 至 十 节"))
    }

    @Test
    fun `rangeIn 取最小最大`() {
        assertEquals(1 to 2, TextParsers.rangeIn("1-2"))
        assertEquals(3 to 4, TextParsers.rangeIn("三、四"))
        assertEquals(3 to 4, TextParsers.rangeIn("第3~4节"))
        assertEquals(5 to 5, TextParsers.rangeIn("5"))
        assertNull(TextParsers.rangeIn("无数字"))
    }

    // ---------------- dayOfWeek ----------------

    @Test
    fun `dayOfWeek 常见写法`() {
        assertEquals(1, TextParsers.dayOfWeek("周一"))
        assertEquals(3, TextParsers.dayOfWeek("星期三"))
        assertEquals(6, TextParsers.dayOfWeek("礼拜六"))
        assertEquals(7, TextParsers.dayOfWeek("星期天"))
        assertEquals(7, TextParsers.dayOfWeek("日"))
    }

    @Test
    fun `dayOfWeek 数字与英文`() {
        assertEquals(3, TextParsers.dayOfWeek("3"))
        assertEquals(1, TextParsers.dayOfWeek("Mon"))
        assertEquals(7, TextParsers.dayOfWeek("sunday"))
    }

    @Test
    fun `dayOfWeek 整串兜底`() {
        // 「周一1-2节」这类整串：剥掉周前缀后落到数字兜底
        assertEquals(1, TextParsers.dayOfWeek("周一1-2节"))
    }

    @Test
    fun `dayOfWeek 无法识别返回 null`() {
        assertNull(TextParsers.dayOfWeek("高数"))
        assertNull(TextParsers.dayOfWeek("8")) // 超出 1..7
    }

    // ---------------- pattern ----------------

    @Test
    fun `pattern 单双周识别`() {
        assertEquals(WeekPattern.ODD, TextParsers.pattern("单周"))
        assertEquals(WeekPattern.EVEN, TextParsers.pattern("双周"))
        assertEquals(WeekPattern.ODD, TextParsers.pattern("单"))
        assertEquals(WeekPattern.EVEN, TextParsers.pattern("双"))
        assertEquals(WeekPattern.EVERY, TextParsers.pattern("每周"))
        assertEquals(WeekPattern.EVERY, TextParsers.pattern(""))
    }

    // ---------------- decodeBytes ----------------

    @Test
    fun `decodeBytes UTF-8 直通`() {
        assertEquals("高等数学", TextParsers.decodeBytes("高等数学".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `decodeBytes GBK 兜底`() {
        // 教务系统导出常见 GBK：按 UTF-8 解出乱码（\uFFFD）时回落 GBK
        val gbk = "高等数学".toByteArray(java.nio.charset.Charset.forName("GBK"))
        assertEquals("高等数学", TextParsers.decodeBytes(gbk))
    }

    @Test
    fun `decodeBytes BOM 处理`() {
        val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "课程".toByteArray(Charsets.UTF_8)
        assertEquals("课程", TextParsers.decodeBytes(utf8Bom))
        val utf16Le = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            "课程".toByteArray(Charsets.UTF_16LE)
        assertEquals("课程", TextParsers.decodeBytes(utf16Le))
        val utf16Be = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) +
            "课程".toByteArray(Charsets.UTF_16BE)
        assertEquals("课程", TextParsers.decodeBytes(utf16Be))
    }

    // ---------------- parseCsv ----------------

    @Test
    fun `parseCsv 基础与换行`() {
        assertEquals(
            listOf(listOf("a", "b", "c"), listOf("1", "2", "3")),
            TextParsers.parseCsv("a,b,c\r\n1,2,3"),
        )
    }

    @Test
    fun `parseCsv 引号内嵌逗号`() {
        assertEquals(
            listOf(listOf("a", "b,c", "d")),
            TextParsers.parseCsv("a,\"b,c\",d"),
        )
        // 引号内的双引号转义
        assertEquals(
            listOf(listOf("说\"好\"")),
            TextParsers.parseCsv("\"说\"\"好\"\"\""),
        )
    }

    @Test
    fun `parseCsv 制表符分隔`() {
        assertEquals(listOf(listOf("周一", "1-2")), TextParsers.parseCsv("周一\t1-2"))
    }

    @Test
    fun `parseCsv 空行过滤与末行无换行`() {
        assertEquals(
            listOf(listOf("a", "b"), listOf("1", "2")),
            TextParsers.parseCsv("a,b\n,\n1,2"), // 全空白行丢弃
        )
        assertEquals(listOf(listOf("x")), TextParsers.parseCsv("x"))
    }
}
