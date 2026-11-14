package com.balsam.timetable.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 备份 JSON 解析与恢复前校验：v2 主格式、v1 旧格式兼容、
 * 版本过新拒绝、学期字段健全性、空条目与损坏文件拒绝。
 */
class BackupValidatorTest {

    private val v2Json = """
        {
          "version": 2,
          "semester": {"name": "2026-2027学年 第一学期", "startDate": "2026-09-07", "totalWeeks": 16},
          "slots": [{"start": "08:00", "end": "08:45"}],
          "entries": [
            {"name": "高等数学", "teacher": "王老师", "location": "X302",
             "day": 1, "startSlot": 1, "endSlot": 2,
             "startWeek": 1, "endWeek": 16, "pattern": "EVERY", "weeks": "1-8,10,12-16"}
          ]
        }
    """.trimIndent()

    @Test
    fun `解析v2备份并读出学期与条目`() {
        val doc = BackupValidator.parseAndValidate(v2Json)
        assertEquals("2026-2027学年 第一学期", doc.semester.name)
        assertEquals("2026-09-07", doc.semester.startDate)
        assertEquals(16, doc.semester.totalWeeks)
        assertEquals(1, doc.entries.size)
        assertEquals("1-8,10,12-16", doc.entries.single().weeks)
    }

    @Test
    fun `兼容v1旧备份（无weeks字段由区间推导）`() {
        val v1Json = """
            {
              "version": 1,
              "semester": {"name": "旧学期", "startDate": "2025-09-01", "totalWeeks": 20},
              "slots": [{"start": "08:00", "end": "08:45"}],
              "entries": [
                {"name": "大学英语", "day": 2, "startSlot": 3, "endSlot": 4,
                 "startWeek": 1, "endWeek": 8, "pattern": "ODD"}
              ]
            }
        """.trimIndent()
        val doc = BackupValidator.parseAndValidate(v1Json)
        assertEquals(1, doc.entries.size)
        // 1-8 单周 → {1,3,5,7}，由 toParsedEntry 推导（这里只验证文档层能通过校验）
        assertTrue(doc.entries.single().weeks == null)
        assertEquals(1, doc.entries.single().startWeek)
    }

    @Test
    fun `缺version字段按当前版本处理`() {
        val json = """
            {"semester": {"name": "无版本号", "startDate": "2026-09-07", "totalWeeks": 16},
             "entries": [{"name": "课", "day": 1, "startSlot": 1, "endSlot": 1}]}
        """.trimIndent()
        val doc = BackupValidator.parseAndValidate(json)
        assertEquals("无版本号", doc.semester.name)
    }

    @Test
    fun `版本过新被拒绝`() {
        val json = v2Json.replace("\"version\": 2", "\"version\": 3")
        try {
            BackupValidator.parseAndValidate(json)
            fail("版本过新应当被拒绝")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("版本过新"))
        }
    }

    @Test
    fun `学期名称为空被拒绝`() {
        val json = v2Json.replace("\"name\": \"2026-2027学年 第一学期\"", "\"name\": \"\"")
        assertRejected(json, "学期名称为空")
    }

    @Test
    fun `开学日期格式非法被拒绝`() {
        val json = v2Json.replace("2026-09-07", "2026/09/07")
        assertRejected(json, "开学日期无效")
    }

    @Test
    fun `总周数越界被拒绝`() {
        val json = v2Json.replace("\"totalWeeks\": 16", "\"totalWeeks\": 99")
        assertRejected(json, "总周数无效")
    }

    @Test
    fun `没有课程条目被拒绝`() {
        val json = v2Json.replace(
            Regex("\"entries\": \\[.*\\]", RegexOption.DOT_MATCHES_ALL),
            "\"entries\": []",
        )
        assertRejected(json, "没有课程条目")
    }

    @Test
    fun `损坏的JSON被拒绝`() {
        try {
            BackupValidator.parseAndValidate("这不是JSON{{{")
            fail("损坏文件应当被拒绝")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("不是有效的"))
        }
    }

    private fun assertRejected(json: String, expectedPart: String) {
        try {
            BackupValidator.parseAndValidate(json)
            fail("应当被拒绝：$expectedPart")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "错误信息应包含「$expectedPart」，实际为：${e.message}",
                e.message!!.contains(expectedPart),
            )
        }
    }
}
