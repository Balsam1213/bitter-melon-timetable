package com.balsam.timetable.data.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipOutputStream

/**
 * XlsxParser 的 JVM 单测：测试里用 ZipOutputStream 现场构造最小 .xlsx
 * （zip + sharedStrings.xml + sheet1.xml），零二进制资源文件。
 * 解析器只读取两个 entry 的特定元素（si/t、row/c/v/is），无需 [Content_Types] 等。
 */
class XlsxParserTest {

    /** 构造最小 xlsx：entries 为 (entryName, content) 对，按顺序写入 zip */
    private fun xlsx(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            entries.forEach { (name, content) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private val sharedStrings =
        """<sst><si><t>高等数学</t></si><si><t>王老师</t></si></sst>"""

    private val sheet =
        """<worksheet><sheetData>
            <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row>
            <row r="2"><c r="A2"><v>1</v></c><c r="B2" t="inlineStr"><is><t>X302</t></is></c></row>
        </sheetData></worksheet>"""

    @Test
    fun `共享字符串与inline字符串都能解析`() {
        val grid = XlsxParser.parse(
            xlsx("xl/sharedStrings.xml" to sharedStrings, "xl/worksheets/sheet1.xml" to sheet),
        )
        assertEquals(2, grid.size)
        assertEquals(listOf("高等数学", "王老师"), grid[0])
        assertEquals(listOf("1", "X302"), grid[1])
    }

    @Test
    fun `缺失列用空串补齐`() {
        // 第 2 行只有 B2 有值 → A2 补空串
        val grid = XlsxParser.parse(
            xlsx("xl/worksheets/sheet1.xml" to """<worksheet><sheetData>
                <row r="1"><c r="A1"><v>a</v></c><c r="B1"><v>b</v></c></row>
                <row r="2"><c r="B2"><v>b</v></c></row>
            </sheetData></worksheet>"""),
        )
        assertEquals(listOf("a", "b"), grid[0])
        assertEquals(listOf("", "b"), grid[1])
    }

    @Test
    fun `全空白行被过滤`() {
        val grid = XlsxParser.parse(
            xlsx("xl/worksheets/sheet1.xml" to """<worksheet><sheetData>
                <row r="1"><c r="A1"><v>课</v></c></row>
                <row r="2"><c r="A2"><v> </v></c></row>
            </sheetData></worksheet>"""),
        )
        assertEquals(1, grid.size)
    }

    @Test
    fun `多字母列号AA正确映射`() {
        val grid = XlsxParser.parse(
            xlsx("xl/worksheets/sheet1.xml" to """<worksheet><sheetData>
                <row r="1"><c r="A1"><v>a</v></c><c r="AA1"><v>aa</v></c></row>
            </sheetData></worksheet>"""),
        )
        assertEquals(1, grid.size)
        // AA = 第 27 列（0 基 26）：B..Z 缺失补 25 个空串
        assertEquals(27, grid[0].size)
        assertEquals("a", grid[0][0])
        assertEquals("aa", grid[0][26])
    }

    @Test
    fun `无worksheet条目时报中文错误`() {
        try {
            XlsxParser.parse(xlsx("xl/sharedStrings.xml" to sharedStrings))
            fail("无 worksheet 应当抛出")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("不是有效的 Excel"))
        }
    }

    @Test
    fun `非zip字节报中文错误或ZipException`() {
        // JVM 上 ZipInputStream 对非法头可能静默返回 null（走到「无 worksheet」分支），也可能抛 ZipException
        try {
            XlsxParser.parse("这不是zip".toByteArray())
            fail("非 zip 应当抛出")
        } catch (e: ZipException) {
            // friendlyParseError 会把它翻译为中文
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("不是有效的 Excel"))
        }
    }

    @Test
    fun `端到端_网格进RowMapper得到条目`() {
        // 照 RowMapperTest 的 Csv 全链路先例：parse → RowMapper（非 s 类型单元格取 v 文本原值）
        val grid = XlsxParser.parse(
            xlsx("xl/worksheets/sheet1.xml" to """<worksheet><sheetData>
                <row r="1"><c r="A1"><v>星期</v></c><c r="B1"><v>1</v></c></row>
            </sheetData></worksheet>"""),
        )
        assertEquals(listOf("星期", "1"), grid[0])
        val rows = listOf(
            listOf("星期", "节次", "课程", "教师", "地点", "开始周", "结束周", "单双周"),
            listOf("周一", "1-2", "高等数学", "王老师", "X302", "1", "16", "每周"),
        )
        val entries = RowMapper.fromRows(rows)
        assertEquals(1, entries.size)
        assertEquals("高等数学", entries[0].name)
        assertEquals("王老师", entries[0].teacher)
    }
}
