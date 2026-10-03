package com.balsam.timetable.data.importer

import com.balsam.timetable.data.model.WeekPattern
import kotlinx.serialization.json.Json

object TextParsers {

    private val cnDigits = mapOf(
        "一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5,
        "六" to 6, "七" to 7, "八" to 8, "九" to 9, "十" to 10,
    )

    fun cnToInt(s: String): Int? {
        s.toIntOrNull()?.let { return it }
        if (s.isEmpty()) return null
        return when {
            s == "十" -> 10
            s.startsWith("十") -> 10 + (cnDigits[s.drop(1)] ?: return null)
            s.endsWith("十") -> (cnDigits[s.dropLast(1)] ?: return null) * 10
            s.contains("十") -> {
                val (a, b) = s.split("十")
                (cnDigits[a] ?: return null) * 10 + (cnDigits[b] ?: return null)
            }
            else -> cnDigits[s]
        }
    }

    /** 提取文本中的数字（阿拉伯优先，其次中文数字） */
    fun numbersIn(text: String): List<Int> {
        val arabic = Regex("\\d+").findAll(text).map { it.value.toInt() }.toList()
        if (arabic.isNotEmpty()) return arabic
        val result = mutableListOf<Int>()
        Regex("[一二三四五六七八九十]{1,3}").findAll(text).forEach {
            cnToInt(it.value)?.let(result::add)
        }
        return result
    }

    /** "1-2"、"三、四"、"第3~4节" → (1,2)；单个数字 → (n,n) */
    fun rangeIn(text: String): Pair<Int, Int>? {
        val nums = numbersIn(text)
        if (nums.isEmpty()) return null
        return nums.min() to nums.max()
    }

    /** 星期解析：周一/星期一/礼拜一/1/一/Mon → 1..7 */
    fun dayOfWeek(text: String): Int? {
        val t = text.trim()
            .removePrefix("星期").removePrefix("礼拜").removePrefix("周")
            .trim()
        if (t.isEmpty()) return null
        when (t) {
            "一" -> return 1
            "二" -> return 2
            "三" -> return 3
            "四" -> return 4
            "五" -> return 5
            "六" -> return 6
            "日", "天" -> return 7
        }
        t.toIntOrNull()?.let { if (it in 1..7) return it }
        val lower = t.lowercase()
        val en = mapOf("mon" to 1, "tue" to 2, "wed" to 3, "thu" to 4, "fri" to 5, "sat" to 6, "sun" to 7)
        en.forEach { (k, v) -> if (lower.startsWith(k)) return v }
        // 形如 "周一1-2节" 之类的整串
        rangeIn(t)?.let { (a, _) -> if (a in 1..7) return a }
        return null
    }

    fun pattern(text: String): WeekPattern = when {
        text.contains("单周") || (text.contains("单") && !text.contains("双")) -> WeekPattern.ODD
        text.contains("双周") || (text.contains("双") && !text.contains("单")) -> WeekPattern.EVEN
        else -> WeekPattern.EVERY
    }

    /** 字节解码：UTF-8 → GBK 兜底（教务系统导出常见编码），处理 BOM */
    fun decodeBytes(bytes: ByteArray): String {
        if (bytes.size >= 2) {
            if ((bytes[0].toInt() and 0xFF) == 0xFF && (bytes[1].toInt() and 0xFF) == 0xFE) {
                return String(bytes, Charsets.UTF_16LE).trimStart('\uFEFF')
            }
            if ((bytes[0].toInt() and 0xFF) == 0xFE && (bytes[1].toInt() and 0xFF) == 0xFF) {
                return String(bytes, Charsets.UTF_16BE).trimStart('\uFEFF')
            }
        }
        val utf8 = String(bytes, Charsets.UTF_8).trimStart('\uFEFF')
        if (!utf8.contains('\uFFFD')) return utf8
        return String(bytes, java.nio.charset.Charset.forName("GBK"))
    }

    /** CSV/TSV 解析（支持引号包裹、逗号内嵌） */
    fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        var cell = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes -> {
                    if (c == '"') {
                        if (i + 1 < text.length && text[i + 1] == '"') {
                            cell.append('"'); i++
                        } else inQuotes = false
                    } else cell.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' || c == '\t' -> {
                    row.add(cell.toString()); cell = StringBuilder()
                }
                c == '\n' -> {
                    row.add(cell.toString()); rows.add(row)
                    row = mutableListOf(); cell = StringBuilder()
                }
                c == '\r' -> Unit
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row.add(cell.toString()); rows.add(row)
        }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }
}

object JsonImporter {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): ExportDoc = json.decodeFromString(ExportDoc.serializer(), text)
}

object CsvImporter {
    fun parse(bytes: ByteArray): List<ParsedEntry> =
        RowMapper.fromRows(TextParsers.parseCsv(TextParsers.decodeBytes(bytes)))
}

/** 把「行 × 列」文本网格映射为课程条目，CSV 与 Excel 共用 */
object RowMapper {

    private data class ColMap(
        val day: Int?, val slot: Int?, val name: Int?, val teacher: Int?,
        val location: Int?, val startWeek: Int?, val endWeek: Int?, val pattern: Int?,
    )

    fun fromRows(rows: List<List<String>>): List<ParsedEntry> {
        if (rows.isEmpty()) return emptyList()
        val header = rows.first().map { it.trim() }
        val hasHeader = header.any { dayHeader(it) || slotHeader(it) || nameHeader(it) }
        val col: ColMap
        val dataRows: List<List<String>>
        if (hasHeader) {
            fun idx(pred: (String) -> Boolean) = header.indexOfFirst(pred).takeIf { it >= 0 }
            col = ColMap(
                day = idx(::dayHeader),
                slot = idx(::slotHeader),
                name = idx(::nameHeader),
                teacher = idx(::teacherHeader),
                location = idx(::locationHeader),
                startWeek = idx { it.contains("开始周") || it.contains("起始周") || it == "起周" },
                endWeek = idx { it.contains("结束周") || it.contains("截止周") || it == "止周" },
                pattern = idx { it.contains("单双") || it.contains("周类型") },
            )
            dataRows = rows.drop(1)
        } else {
            // 无表头时按模板顺序：星期,节次,课程,教师,地点,开始周,结束周,单双周
            col = ColMap(0, 1, 2, 3, 4, 5, 6, 7)
            dataRows = rows
        }
        return dataRows.mapNotNull { mapRow(it, col) }
    }

    private fun cell(r: List<String>, i: Int?): String = if (i != null && i < r.size) r[i].trim() else ""

    private fun mapRow(r: List<String>, col: ColMap): ParsedEntry? {
        val name = cell(r, col.name)
        if (name.isEmpty()) return null
        val day = TextParsers.dayOfWeek(cell(r, col.day)) ?: return null
        val slots = TextParsers.rangeIn(cell(r, col.slot)) ?: (1 to 1)
        val startW = cell(r, col.startWeek).toIntOrNull()
        val endW = cell(r, col.endWeek).toIntOrNull()
        val weeks = when {
            startW != null && endW != null -> startW to endW
            startW != null -> startW to startW
            endW != null -> endW to endW
            else -> TextParsers.rangeIn(cell(r, col.slot) + " " + cell(r, col.pattern)) ?: (1 to 16)
        }
        return ParsedEntry(
            name = name,
            teacher = cell(r, col.teacher),
            location = cell(r, col.location),
            dayOfWeek = day,
            startSlot = slots.first,
            endSlot = slots.second,
            startWeek = weeks.first,
            endWeek = weeks.second,
            weekPattern = TextParsers.pattern(cell(r, col.pattern)),
        )
    }

    private fun dayHeader(h: String) =
        h.contains("星期") || h == "周几" || h == "周" || h.contains("星期几") || h.equals("day", true)

    private fun slotHeader(h: String) = h.contains("节次") || h == "节" || h.contains("节数")

    private fun nameHeader(h: String) =
        h.contains("课程") || h.contains("名称") || h.contains("科目") || h.equals("name", true)

    private fun teacherHeader(h: String) = h.contains("教师") || h.contains("老师")

    private fun locationHeader(h: String) =
        h.contains("地点") || h.contains("教室") || h.contains("位置")
}
