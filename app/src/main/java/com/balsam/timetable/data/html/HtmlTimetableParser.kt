package com.balsam.timetable.data.html

import com.balsam.timetable.data.importer.OcrParser
import com.balsam.timetable.data.importer.ParsedEntry
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** 教务网导入的解析结果传递通道（WebImportScreen → ImportScreen 预览页） */
object WebImportBus {
    var pendingEntries: List<ParsedEntry>? = null
}

/**
 * 教务网页面课表解析，支持两种版式（自动检测）：
 * A. 选课记录列表——表头含「课程名称」「上课时间地点」，每行一门课，
 *    时间单元格含若干「1-17周 星期二 9-10节 + 地点」块；
 * B. 周课表网格——表头含「星期一~星期日」，每行=节次，单元格为
 *    「B1498 通用学术英语 (付华权) 地点：X1525」式文本。
 */
object HtmlTimetableParser {

    private val listTimeRegex = Regex(
        "(\\d{1,2})\\s*[-–~]\\s*(\\d{1,2})\\s*周\\s*(?:星期|周)\\s*([一二三四五六日天])\\s*(?:第)?\\s*(\\d{1,2})\\s*(?:[-–~]\\s*(\\d{1,2}))?\\s*节"
    )
    private val dayHeaderRegex = Regex("星期([一二三四五六日天])")
    private val weekRange = Regex("(\\d{1,2})\\s*[-–~]\\s*(\\d{1,2})\\s*周")

    fun parse(html: String): List<ParsedEntry> {
        val doc = Jsoup.parse(html)
        val rows = doc.select("tr")
        val list = parseListRows(rows)
        if (list.isNotEmpty()) return list
        val grid = parseGridRows(rows)
        if (grid.isNotEmpty()) return grid
        throw IllegalArgumentException(
            "未能在页面中找到课表数据。请确认已登录并打开「我的选课记录」或「本学期周课表」页面。"
        )
    }

    // —— 版式 A：选课记录列表 ——

    private fun parseListRows(rows: List<Element>): List<ParsedEntry> {
        var nameIdx = -1
        var teacherIdx = -1
        var timeIdx = -1
        for (tr in rows) {
            val cells = tr.select("th,td")
            if (cells.size < 6) continue
            val texts = cells.map { it.text().trim() }
            val ni = texts.indexOfFirst { it.contains("课程名称") || it == "课程" }
            val ti = texts.indexOfFirst { it.contains("教师") }
            val si = texts.indexOfFirst { it.contains("时间") }
            if (ni >= 0 && si >= 0) {
                nameIdx = ni
                teacherIdx = ti
                timeIdx = si
                break
            }
        }

        val out = mutableListOf<ParsedEntry>()
        if (nameIdx >= 0) {
            for (tr in rows) {
                val cells = tr.select("td")
                if (cells.size <= maxOf(nameIdx, timeIdx)) continue
                val name = cells[nameIdx].text().trim()
                val timeText = cells[timeIdx].text().trim()
                if (name.isEmpty() || !listTimeRegex.containsMatchIn(timeText)) continue
                val teacher = if (teacherIdx >= 0 && teacherIdx < cells.size) {
                    cells[teacherIdx].text().trim()
                } else ""
                out += timeEntries(name, teacher, timeText)
            }
            if (out.isNotEmpty()) return out
        }

        // 无标准表头时兜底：任意含时间块的行，整行文本按列表清洗提取
        for (tr in rows) {
            val text = tr.text().trim()
            if (!listTimeRegex.containsMatchIn(text)) continue
            val nameParts = mutableListOf<String>()
            val teacherParts = mutableListOf<String>()
            OcrParser.extractListNameTeacher(text, nameParts, teacherParts)
            val name = nameParts.firstOrNull() ?: continue
            val matches = listTimeRegex.findAll(text).toList()
            matches.forEachIndexed { idx, m ->
                val next = matches.getOrNull(idx + 1)?.range?.first ?: text.length
                val seg = text.substring(m.range.last + 1, next).replace(Regex("\\s"), "")
                out += entryFromMatch(m, name, teacherParts.joinToString("、"), seg)
            }
        }
        return out
    }

    // —— 版式 B：周课表网格 ——

    private fun parseGridRows(rows: List<Element>): List<ParsedEntry> {
        // 表头行：含 ≥5 个「星期X」单元格，记录 列号→星期
        var dayCols: Map<Int, Int>? = null
        for (tr in rows) {
            val cells = tr.select("th,td")
            if (cells.size < 6) continue
            val m = mutableMapOf<Int, Int>()
            cells.forEachIndexed { i, c ->
                dayHeaderRegex.find(c.text())?.let { mm ->
                    val d = when (mm.groupValues[1]) {
                        "一" -> 1; "二" -> 2; "三" -> 3; "四" -> 4
                        "五" -> 5; "六" -> 6; else -> 7
                    }
                    m[i] = d
                }
            }
            if (m.size >= 5) {
                dayCols = m
                break
            }
        }
        dayCols ?: return emptyList()

        val out = mutableListOf<ParsedEntry>()
        for (tr in rows) {
            val cells = tr.select("td")
            if (cells.isEmpty()) continue
            val slot = cells.first()!!.text().trim().toIntOrNull() ?: continue
            if (slot !in 1..20) continue
            for ((colIdx, day) in dayCols) {
                if (colIdx >= cells.size) continue
                val cellText = cells[colIdx].text().trim()
                if (cellText.isEmpty()) continue
                // 周课表单元格文本与 OCR 网格格子同构，复用其解析
                val entry = OcrParser.parseCellText(
                    cellText.replace("地点：", "\n地点：").replace("地点:", "\n地点:"),
                    day, slot, slot,
                ) ?: continue
                out += entry
            }
        }
        // 相邻同课合并为连堂
        return OcrParser.mergeAdjacent(out)
    }

    // —— 公共 ——

    /** 从一段含多个时间块和地点的文本中拆出全部时间段条目 */
    private fun timeEntries(name: String, teacher: String, timeText: String): List<ParsedEntry> {
        val matches = listTimeRegex.findAll(timeText).toList()
        return matches.mapIndexed { idx, m ->
            val next = matches.getOrNull(idx + 1)?.range?.first ?: timeText.length
            val seg = timeText.substring(m.range.last + 1, next).replace(Regex("\\s"), "")
            entryFromMatch(m, name, teacher, seg)
        }
    }

    private fun entryFromMatch(
        m: MatchResult,
        name: String,
        teacher: String,
        locationSeg: String,
    ): ParsedEntry {
        val g = m.groupValues
        val day = when (g[3]) {
            "一" -> 1; "二" -> 2; "三" -> 3; "四" -> 4; "五" -> 5; "六" -> 6; else -> 7
        }
        val startSlot = g[4].toIntOrNull() ?: 1
        val endSlot = g[5].toIntOrNull() ?: startSlot
        val location = when {
            locationSeg.isEmpty() -> ""
            OcrParser.locationKeyword.containsMatchIn(locationSeg) ->
                OcrParser.locationKeyword.find(locationSeg)!!.value + locationSeg
                    .substringAfter(OcrParser.locationKeyword.find(locationSeg)!!.value)
            else -> locationSeg
        }
        return ParsedEntry(
            name = name,
            teacher = teacher,
            location = location,
            dayOfWeek = day,
            startSlot = startSlot,
            endSlot = endSlot,
            startWeek = g[1].toIntOrNull() ?: 1,
            endWeek = g[2].toIntOrNull() ?: 17,
            weekPattern = com.balsam.timetable.data.model.WeekPattern.EVERY,
        )
    }
}
