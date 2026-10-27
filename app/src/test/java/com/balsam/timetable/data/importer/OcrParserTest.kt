package com.balsam.timetable.data.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OcrParserTest {

    // ---------------- parseCellText ----------------

    @Test
    fun `地点冒号前缀行`() {
        val e = OcrParser.parseCellText("高等数学\n地点：X102\n1-16周", day = 1, startSlot = 1, endSlot = 2)
        assertEquals("高等数学", e!!.name)
        assertEquals("X102", e.location)
        assertEquals("", e.teacher)
        assertEquals(1, e.dayOfWeek)
        assertEquals(1, e.startSlot)
        assertEquals(2, e.endSlot)
        assertEquals((1..16).toSet(), e.weeks)
    }

    @Test
    fun `教师括号提取`() {
        val e = OcrParser.parseCellText("通用学术英语(付华权)\n地点：X1525", day = 2, startSlot = 1, endSlot = 2)
        assertEquals("通用学术英语", e!!.name)
        assertEquals("付华权", e.teacher)
        assertEquals("X1525", e.location)
    }

    @Test
    fun `课程代码前缀剥离`() {
        // B1498 这类选课编号不应混进课名（含中文的行才剥离，纯编号地点行不受影响）
        val e = OcrParser.parseCellText("B1498 高等数学\n地点：A101", day = 1, startSlot = 3, endSlot = 4)
        assertEquals("高等数学", e!!.name)
    }

    @Test
    fun `场所关键字行加紧随编号`() {
        val e = OcrParser.parseCellText("机器学习\nX3楼\n305\n1-16周", day = 3, startSlot = 1, endSlot = 2)
        assertEquals("机器学习", e!!.name)
        assertEquals("X3楼305", e.location)
    }

    @Test
    fun `纯编号地点行`() {
        val e = OcrParser.parseCellText("大学英语\nX2229", day = 5, startSlot = 1, endSlot = 2)
        assertEquals("大学英语", e!!.name)
        assertEquals("X2229", e.location)
    }

    @Test
    fun `单双周识别与课名剥离`() {
        val e = OcrParser.parseCellText("体育\n1-16周 单周", day = 5, startSlot = 9, endSlot = 10)
        assertEquals("体育", e!!.name)
        assertEquals(((1..16).filter { it % 2 == 1 }).toSet(), e.weeks)
    }

    @Test
    fun `只有周次文本返回 null`() {
        assertNull(OcrParser.parseCellText("1-16周", day = 1, startSlot = 1, endSlot = 1))
    }

    // ---------------- extractListNameTeacher ----------------

    @Test
    fun `列表行噪声剥离`() {
        val names = mutableListOf<String>()
        val teachers = mutableListOf<String>()
        OcrParser.extractListNameTeacher(
            "1 MATH100233 高等数学(上) 3.0 必修 王建国 2024-2025第1学期",
            names, teachers,
        )
        assertEquals(listOf("高等数学"), names)
        assertEquals(listOf("王建国"), teachers)
    }

    @Test
    fun `学院短名不作教师`() {
        val names = mutableListOf<String>()
        val teachers = mutableListOf<String>()
        OcrParser.extractListNameTeacher("大学英语 张伟 外语", names, teachers)
        assertEquals(listOf("大学英语"), names)
        assertEquals(listOf("张伟"), teachers)
    }

    @Test
    fun `教师最多取三人`() {
        val names = mutableListOf<String>()
        val teachers = mutableListOf<String>()
        OcrParser.extractListNameTeacher("高数 张三 李四 王五 赵六", names, teachers)
        assertEquals(listOf("高数"), names)
        assertEquals(listOf("张三", "李四", "王五"), teachers)
    }

    // ---------------- mergeAdjacent ----------------

    private fun entry(
        name: String, day: Int, start: Int, end: Int,
        weeks: Set<Int> = (1..16).toSet(), location: String = "",
    ) = ParsedEntry(
        name = name, dayOfWeek = day, startSlot = start, endSlot = end,
        weeks = weeks, location = location,
    )

    @Test
    fun `同名相邻连堂合并`() {
        val merged = OcrParser.mergeAdjacent(
            listOf(
                entry("高等数学", 1, 1, 2, location = ""),
                entry("高等数学", 1, 3, 4, location = "A101"),
            ),
        )
        assertEquals(1, merged.size)
        assertEquals(1, merged[0].startSlot)
        assertEquals(4, merged[0].endSlot)
        assertEquals("A101", merged[0].location) // 前条地点空，取后条
    }

    @Test
    fun `周次不同不合并`() {
        val merged = OcrParser.mergeAdjacent(
            listOf(
                entry("高等数学", 1, 1, 2, weeks = (1..8).toSet()),
                entry("高等数学", 1, 3, 4, weeks = (9..16).toSet()),
            ),
        )
        assertEquals(2, merged.size)
    }

    @Test
    fun `OCR 错字相似名容错合并`() {
        val merged = OcrParser.mergeAdjacent(
            listOf(entry("体育", 2, 3, 3), entry("体育亚", 2, 4, 4)),
        )
        assertEquals(1, merged.size)
    }

    @Test
    fun `网格版式空结果报错而非静默通过`() {
        try {
            OcrParser.ensureGridResult(emptyList())
            org.junit.Assert.fail("应抛出 OcrParseException")
        } catch (e: OcrParser.OcrParseException) {
            org.junit.Assert.assertTrue(e.message!!.contains("没有解析出课程"))
        }
        // 非空结果原样返回
        val kept = listOf(entry("高等数学", 1, 1, 2))
        org.junit.Assert.assertEquals(kept, OcrParser.ensureGridResult(kept))
    }

    @Test
    fun `不同课不误合并`() {
        // 字符交集 0.5 < 0.65，且互非前缀
        val merged = OcrParser.mergeAdjacent(
            listOf(entry("大学英语", 1, 1, 2), entry("大学体育", 1, 3, 4)),
        )
        assertEquals(2, merged.size)
    }

    @Test
    fun `跨天不合并`() {
        val merged = OcrParser.mergeAdjacent(
            listOf(entry("高等数学", 1, 1, 2), entry("高等数学", 2, 1, 2)),
        )
        assertEquals(2, merged.size)
    }
}
