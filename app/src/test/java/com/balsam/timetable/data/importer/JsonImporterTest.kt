package com.balsam.timetable.data.importer

import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonImporterTest {

    @Test
    fun `v1 旧格式按区间展开`() {
        val json = """
            {
              "version": 1,
              "semester": { "name": "2026-2027学年 第一学期", "startDate": "2026-09-07", "totalWeeks": 20 },
              "slots": [ { "start": "08:00", "end": "08:45" } ],
              "entries": [
                { "name": "高等数学(上)", "teacher": "王老师", "location": "教学楼A101",
                  "day": 1, "startSlot": 1, "endSlot": 2, "startWeek": 1, "endWeek": 16, "pattern": "EVERY" },
                { "name": "数据结构", "teacher": "张老师", "location": "机房B203",
                  "day": 3, "startSlot": 1, "endSlot": 2, "startWeek": 2, "endWeek": 17, "pattern": "ODD" }
              ]
            }
        """.trimIndent()
        val doc = JsonImporter.parse(json)
        assertEquals("2026-09-07", doc.semester.startDate)
        assertEquals(20, doc.semester.totalWeeks)
        assertEquals(2, doc.entries.size)

        val calculus = doc.entries[0].toParsedEntry()
        assertEquals((1..16).toSet(), calculus.weeks)
        assertEquals(1, calculus.dayOfWeek)

        // 2-17 周单周 = 区间内奇数周
        val ds = doc.entries[1].toParsedEntry()
        assertEquals(((2..17).filter { it % 2 == 1 }).toSet(), ds.weeks)
    }

    @Test
    fun `v2 weeks 字段优先于推导区间`() {
        val json = """
            {
              "semester": { "name": "测试学期", "startDate": "2026-09-07", "totalWeeks": 20 },
              "entries": [
                { "name": "任意周集合课", "day": 2, "startSlot": 3, "endSlot": 4,
                  "startWeek": 1, "endWeek": 16, "pattern": "EVERY", "weeks": "1,2,3,5,11" }
              ]
            }
        """.trimIndent()
        val doc = JsonImporter.parse(json)
        assertEquals(2, doc.version) // 缺省 version
        val e = doc.entries[0].toParsedEntry()
        assertEquals(setOf(1, 2, 3, 5, 11), e.weeks)
    }

    @Test
    fun `weeks 字段非法时回落旧区间字段`() {
        val json = """
            {
              "semester": { "name": "测试学期", "startDate": "2026-09-07", "totalWeeks": 20 },
              "entries": [
                { "name": "回落课", "day": 4, "startSlot": 1, "endSlot": 2,
                  "startWeek": 1, "endWeek": 8, "pattern": "EVERY", "weeks": "abc" }
              ]
            }
        """.trimIndent()
        val doc = JsonImporter.parse(json)
        assertEquals((1..8).toSet(), doc.entries[0].toParsedEntry().weeks)
    }

    @Test
    fun `条目字段缺省值`() {
        val json = """
            {
              "semester": { "name": "测试学期", "startDate": "2026-09-07", "totalWeeks": 16 },
              "entries": [ { "name": "最简条目", "day": 5, "startSlot": 1, "endSlot": 2 } ]
            }
        """.trimIndent()
        val doc = JsonImporter.parse(json)
        val e = doc.entries[0].toParsedEntry()
        assertEquals((1..16).toSet(), e.weeks) // 缺省 1-16 每周
        assertEquals("", e.teacher)
        assertEquals("", e.location)
    }

    @Test
    fun `fromWeeks 导出条目旧字段由集合推导`() {
        val entry = ExportEntry.fromWeeks(
            name = "导出课", teacher = "师", location = "L1",
            day = 1, startSlot = 1, endSlot = 2,
            weekSet = (1..8).toSet() + (11..16).toSet(),
        )
        assertEquals("1,2,3,4,5,6,7,8,11,12,13,14,15,16", entry.weeks)
        assertEquals(1, entry.startWeek)
        assertEquals(16, entry.endWeek)
        // 无法精确反推单双周 → EVERY 超集近似；round trip 后集合不丢
        assertEquals("EVERY", entry.pattern)
        assertEquals((1..8).toSet() + (11..16).toSet(), entry.toParsedEntry().weeks)
    }

    // ---------------- 异常输入 ----------------

    @Test(expected = SerializationException::class)
    fun `格式错误的 JSON 抛出解析异常`() {
        JsonImporter.parse("{ 这不是 JSON ")
    }

    @Test(expected = SerializationException::class)
    fun `空文件抛出解析异常`() {
        JsonImporter.parse("")
    }

    @Test(expected = SerializationException::class)
    fun `缺少 semester 必填字段抛出解析异常`() {
        JsonImporter.parse("""{"entries": []}""")
    }

    @Test
    fun `未知字段被忽略不报错`() {
        val doc = JsonImporter.parse(
            """
            {
              "semester": { "name": "测试学期", "startDate": "2026-09-07", "totalWeeks": 16 },
              "unknownTopLevel": 1,
              "entries": [ { "name": "兼容课", "day": 1, "startSlot": 1, "endSlot": 2, "extra": "x" } ]
            }
            """.trimIndent(),
        )
        assertEquals(1, doc.entries.size)
    }
}
