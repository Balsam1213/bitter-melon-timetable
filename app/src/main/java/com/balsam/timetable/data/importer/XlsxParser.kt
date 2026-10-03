package com.balsam.timetable.data.importer

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/**
 * 轻量 .xlsx 解析：xlsx 本质是 zip + XML，不引入 Apache POI（太重）。
 * 解析 sharedStrings.xml 与第一个 worksheet，输出行列文本网格，交给 RowMapper。
 */
object XlsxParser {

    fun parse(bytes: ByteArray): List<List<String>> {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var shared: List<String> = emptyList()
            var firstSheet: ByteArray? = null
            var namedSheet1: ByteArray? = null
            var entry = zis.nextEntry
            while (entry != null) {
                when {
                    entry.name == "xl/sharedStrings.xml" ->
                        shared = parseSharedStrings(readEntry(zis))

                    entry.name.startsWith("xl/worksheets/") && entry.name.endsWith(".xml") -> {
                        val data = readEntry(zis)
                        if (entry.name == "xl/worksheets/sheet1.xml") namedSheet1 = data
                        else if (firstSheet == null) firstSheet = data
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
            val sheet = namedSheet1 ?: firstSheet ?: throw IllegalArgumentException("不是有效的 Excel(.xlsx) 文件")
            return parseSheet(sheet, shared)
        }
    }

    private fun readEntry(zis: ZipInputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = zis.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun parseSharedStrings(data: ByteArray): List<String> {
        val list = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(data), null)
        val current = StringBuilder()
        var inT = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> if (parser.name == "t") inT = true
                XmlPullParser.TEXT -> if (inT) current.append(parser.text)
                XmlPullParser.END_TAG -> {
                    if (parser.name == "t") inT = false
                    if (parser.name == "si") {
                        list.add(current.toString())
                        current.setLength(0)
                    }
                }
            }
            event = parser.next()
        }
        return list
    }

    private fun parseSheet(data: ByteArray, shared: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var currentRow = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(data), null)
        var inV = false
        var inIs = false
        var inIsT = false
        var cellType = ""
        var cellRef = ""
        val value = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> currentRow = mutableListOf()
                    "c" -> {
                        cellRef = parser.getAttributeValue(null, "r") ?: ""
                        cellType = parser.getAttributeValue(null, "t") ?: ""
                        value.setLength(0)
                    }
                    "v" -> inV = true
                    "is" -> inIs = true
                    "t" -> if (inIs) inIsT = true
                }
                XmlPullParser.TEXT -> if (inV || inIsT) value.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v" -> inV = false
                    "t" -> if (inIs) inIsT = false
                    "is" -> inIs = false
                    "c" -> {
                        val idx = columnIndex(cellRef)
                        while (currentRow.size < idx) currentRow.add("")
                        val text = when (cellType) {
                            "s" -> shared.getOrNull(value.toString().toIntOrNull() ?: -1) ?: ""
                            else -> value.toString()
                        }
                        currentRow.add(text.trim())
                    }
                    "row" -> rows.add(currentRow.toList())
                }
            }
            event = parser.next()
        }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }

    /** "A1"→0，"B3"→1，"AA1"→26 */
    private fun columnIndex(ref: String): Int {
        val letters = ref.takeWhile { it in 'A'..'Z' }
        if (letters.isEmpty()) return 0
        return letters.fold(0) { acc, c -> acc * 26 + (c - 'A' + 1) } - 1
    }
}
