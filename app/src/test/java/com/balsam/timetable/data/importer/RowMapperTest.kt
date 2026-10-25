package com.balsam.timetable.data.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RowMapperTest {

    @Test
    fun `表头列名识别`() {
        val rows = listOf(
            listOf("星期", "节次", "课程名称", "教师", "地点", "开始周", "结束周", "单双周"),
            listOf("周一", "1-2节", "高等数学", "王老师", "A101", "1", "16", "每周"),
        )
        val entries = RowMapper.fromRows(rows)
        assertEquals(1, entries.size)
        val e = entries.first()
        assertEquals("高等数学", e.name)
        assertEquals("王老师", e.teacher)
        assertEquals("A101", e.location)
        assertEquals(1, e.dayOfWeek)
        assertEquals(1, e.startSlot)
        assertEquals(2, e.endSlot)
        assertEquals((1..16).toSet(), e.weeks)
    }

    @Test
    fun `表头乱序列映射`() {
        // 列顺序与模板不同也能按表头名找到
        val rows = listOf(
            listOf("课程", "周几", "节数", "教室", "老师", "起始周", "截止周"),
            listOf("大学英语", "周五", "3~4", "B202", "李老师", "2", "17"),
        )
        val entries = RowMapper.fromRows(rows)
        assertEquals(1, entries.size)
        val e = entries.first()
        assertEquals("大学英语", e.name)
        assertEquals(5, e.dayOfWeek)
        assertEquals(3, e.startSlot)
        assertEquals(4, e.endSlot)
        assertEquals((2..17).toSet(), e.weeks)
    }

    @Test
    fun `无表头按模板顺序`() {
        // 模板列序：星期,节次,课程,教师,地点,开始周,结束周,单双周
        val rows = listOf(
            listOf("周一", "1-2", "大学英语", "李老师", "B202", "2", "17", "双周"),
        )
        val entries = RowMapper.fromRows(rows)
        assertEquals(1, entries.size)
        val e = entries.first()
        assertEquals("大学英语", e.name)
        assertEquals(1, e.dayOfWeek)
        assertEquals("B202", e.location)
        assertEquals(((2..17).filter { it % 2 == 0 }).toSet(), e.weeks)
    }

    @Test
    fun `周次缺省时从节次与单双周列推导`() {
        // 无开始/结束周列：rangeIn(节次 + 单双周) 兜底，取两列数字的全域
        // 「1-2」+「3-4周 单周」→ 1-4 周的单周 {1,3}
        val rows = listOf(
            listOf("周一", "1-2", "高等数学", "", "A101", "", "", "3-4周 单周"),
        )
        val entries = RowMapper.fromRows(rows)
        assertEquals(1, entries.size)
        assertEquals(setOf(1, 3), entries.first().weeks)
    }

    @Test
    fun `节次与周次都无数字时默认 1-16`() {
        // 节次列留空（不能用「节」，它会被表头检测识别成列名）
        val rows = listOf(
            listOf("周一", "", "高等数学", "", "A101", "", "", ""),
        )
        val entries = RowMapper.fromRows(rows)
        assertEquals(1, entries.size)
        assertEquals((1..16).toSet(), entries.first().weeks)
        assertEquals(1, entries.first().startSlot)
        assertEquals(1, entries.first().endSlot)
    }

    @Test
    fun `无效行被跳过`() {
        val rows = listOf(
            listOf("星期", "节次", "课程名称"),
            listOf("", "1-2", "无名课程"), // 课名为空 → 丢弃
            listOf("周八", "1-2", "星期无效"), // 星期解析失败 → 丢弃
            listOf("周一", "", "节次缺失兜底第1节"), // 节次缺失 → 默认 (1,1)
        )
        val entries = RowMapper.fromRows(rows)
        assertEquals(1, entries.size)
        assertEquals("节次缺失兜底第1节", entries.first().name)
        assertEquals(1, entries.first().startSlot)
        assertEquals(1, entries.first().endSlot)
    }

    @Test
    fun `CsvImporter GBK 字节流`() {
        val csv = "星期,节次,课程名称,地点\n周一,1-2节,高等数学,A101\n"
        val bytes = csv.toByteArray(java.nio.charset.Charset.forName("GBK"))
        val entries = CsvImporter.parse(bytes)
        assertEquals(1, entries.size)
        assertEquals("高等数学", entries.first().name)
        assertEquals("A101", entries.first().location)
    }

    @Test
    fun `空表无条目`() {
        assertTrue(RowMapper.fromRows(emptyList()).isEmpty())
    }
}
