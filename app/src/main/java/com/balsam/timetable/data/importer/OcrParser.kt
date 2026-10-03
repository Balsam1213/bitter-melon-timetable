package com.balsam.timetable.data.importer

import com.balsam.timetable.data.model.WeekPattern
import com.google.mlkit.vision.text.Text
import kotlin.math.abs

/**
 * 课表截图解析：按文字块坐标聚类。
 *
 * 适配两类主流课表：
 * 1. 教务系统表格——每节课重复课程全文，格式如
 *    「B1498 通用学术英语 (付华权)」+「地点：X1525」，连堂靠相邻同名合并；
 * 2. 课表 App 截图——卡片内「课名 / 编号地点 / 教师名」三行，连堂靠跨行聚类。
 *
 * 结构化规则（按优先级）：
 * - 地点：优先「地点：」前缀行 → 场所关键字行（+紧随编号）→ 纯编号行（X1525 / 馆外213）；
 * - 教师：名称内最后一个人名样式括号 → 地点区域之外的独立汉字短行；
 * - 名称：地点之前的所有行拼接（长课名不拆分），剥离课程代码前缀（B1498）；
 * - 噪声：表头以上全部丢弃；日期（9/10、2026-09-07、9月10日）、周次、时间、
 *   「非本周」「午休/晚休」等界面文字一律过滤。
 * 识别结果一律先进入导入预览页人工确认。
 */
object OcrParser {

    class OcrParseException(message: String) : Exception(message)

    private val dayPrefixRegex = Regex("^(星期|周)[一二三四五六日天]")
    private val pureNumber = Regex("^\\d{1,2}$")
    private val timeText = Regex("^\\d{1,2}[:：]\\d{2}([-–~—]\\d{1,2}[:：]\\d{2})?$")
    private val dateText = Regex("^(\\d{1,2}/\\d{1,2}|\\d{4}-\\d{1,2}-\\d{1,2}|\\d{1,2}月\\d{1,2}日?)$")
    private val weekLabel = Regex("^第?\\d{1,2}周(（?(今天|本周)）?)?$")
    private val noiseText = Regex(
        "^(上午|下午|中午|晚上|午休|晚休|休息|节|非本周|第?[一二三四五六七八九十]+节|\\d{1,2}[-–~]\\d{1,2}节?)$"
    )
    internal val locationKeyword = Regex("(楼|室|馆|场|区|栋|层|机房|教室)", RegexOption.IGNORE_CASE)
    private val pureCodeText = Regex("^[0-9A-Za-z-]{2,8}$")
    private val weekRange = Regex("(\\d{1,2})\\s*[-~—–至]\\s*(\\d{1,2})\\s*周")
    private val courseCodePrefix = Regex("^[A-Za-z]{1,3}\\d{3,6}\\s*")
    private val bracketText = Regex("[(（]([^()（）]+)[)）]")
    private val teacherName = Regex("^[\\u4e00-\\u9fa5·]{2,4}$")

    /** 课程列表版式的时间块：「1-17周 星期二 9-10节」（周次+星期+节次同段文本） */
    private val listTimeRegex = Regex(
        "(\\d{1,2})\\s*[-–~]\\s*(\\d{1,2})\\s*周\\s*(?:星期|周)\\s*([一二三四五六日天])\\s*(?:第)?\\s*(\\d{1,2})\\s*(?:[-–~]\\s*(\\d{1,2}))?\\s*节"
    )

    private data class Piece(val day: Int, val row: Int, val top: Int, val bottom: Int, val text: String)

    fun parse(text: Text): List<ParsedEntry> {
        val allLines = text.textBlocks
            .flatMap { it.lines }
            .filter { it.boundingBox != null && it.text.isNotBlank() }

        // 两种版式自动分流：
        // A. 网格课表——存在「周一~周日」表头行；
        // B. 课程列表——每行一门课，含「1-17周 星期二 9-10节」式的时间地点文本
        val strictHeaders = allLines.filter {
            val t = it.text.replace(" ", "")
            t.length <= 16 && dayPrefixRegex.containsMatchIn(t)
        }
        val listHits = allLines.count { listTimeRegex.containsMatchIn(it.text) }
        if (strictHeaders.size >= 2) return parseGrid(allLines)
        if (listHits >= 2) return parseListTable(allLines)
        // 都不明确时先按网格试，失败再按列表试
        return try {
            parseGrid(allLines)
        } catch (ge: OcrParseException) {
            try {
                parseListTable(allLines)
            } catch (_: OcrParseException) {
                throw ge
            }
        }
    }

    /**
     * 版式 B：课程列表表格（教务系统选课结果页）。
     * 结构：每行一门课——序号 | 学期 | 编号 | 课程名称 | 班号 | 学院 | 教师 | 学分 | 性质 | 上课时间地点。
     * 行锚点是最左侧的纯序号；时间地点列在最右，含若干个「1-17周 星期二 9-10节 + 地点」块。
     */
    private fun parseListTable(allLines: List<Text.Line>): List<ParsedEntry> {
        val leftMost = allLines.minOf { it.boundingBox!!.left }
        val anchors = allLines
            .filter { l ->
                val b = l.boundingBox!!
                b.left <= leftMost + 60 && pureNumber.matches(l.text.trim()) &&
                    (l.text.trim().toIntOrNull() ?: 0) in 1..40
            }
            .sortedBy { it.boundingBox!!.centerY() }
        if (anchors.size < 2) {
            throw OcrParseException("未能识别出课程列表的行序号，请确保截图包含完整表格")
        }

        val imageRight = allLines.maxOf { it.boundingBox!!.right }
        val columnMargin = ((imageRight - leftMost) * 0.04).toInt().coerceAtLeast(20)
        val firstTop = anchors.first().boundingBox!!.centerY()

        // 表头行推导「课程名称」「任课教师」两列的 x 范围，用于精确切分
        var nameRange: LongRange? = null
        var teacherRange: LongRange? = null
        allLines.firstOrNull { it.text.contains("课程名称") && it.text.contains("教师") }?.let { h ->
            val els = h.elements
            if (els.size > 1) {
                val boxes = els.mapNotNull { it.boundingBox }.sortedBy { it.left }
                fun tokenBounds(kw: String): Pair<Int, Int>? {
                    val idx = boxes.indexOfFirst {
                        val t = els[boxes.indexOf(it)].text
                        t.replace(" ", "").contains(kw)
                    }
                    if (idx < 0) return null
                    val start = boxes[idx].left
                    val end = boxes.getOrNull(idx + 1)?.left ?: boxes[idx].right + 80
                    return (start - 10) to (end - 10)
                }
                nameRange = tokenBounds("课程名称")?.let { (a, b) -> a.toLong()..b.toLong() }
                teacherRange = tokenBounds("任课教师")?.let { (a, b) -> a.toLong()..b.toLong() }
            }
        }

        val entries = mutableListOf<ParsedEntry>()
        for (i in anchors.indices) {
            val rowTop = anchors[i].boundingBox!!.centerY() - 8
            val rowBottom = anchors.getOrNull(i + 1)
                ?.let { it.boundingBox!!.centerY() - 8 } ?: Int.MAX_VALUE
            val rowLines = allLines.filter { l ->
                val cy = l.boundingBox!!.centerY()
                cy >= rowTop && cy < rowBottom && cy >= firstTop - 8
            }

            val blockLines = rowLines.filter { listTimeRegex.containsMatchIn(it.text) }
            if (blockLines.isEmpty()) continue
            // 时间地点列：以时间块行的左缘为界，右侧的行都属于该列
            val timeLeft = blockLines.minOf { it.boundingBox!!.left } - columnMargin
            val timeText = rowLines
                .filter { it.boundingBox!!.left >= timeLeft }
                .sortedBy { it.boundingBox!!.top }
                .joinToString("\n") { it.text.trim() }
            val others = rowLines.filter { it.boundingBox!!.left < timeLeft }

            // 课名 / 教师：列表表格一行常被 ML Kit 粘成一条文本。
            // 行级提取：左侧所有行按 x 排序合并 → 剥噪声 → 中文分段，
            // 首段为课名，其后短段过滤常见学院名后为教师
            val nameParts = mutableListOf<String>()
            val teacherParts = mutableListOf<String>()
            val leftText = rowLines
                .filter { it.boundingBox!!.left < timeLeft }
                .sortedBy { it.boundingBox!!.left }
                .joinToString(" ") { it.text.trim() }
            extractListNameTeacher(leftText, nameParts, teacherParts)
            val name = nameParts.joinToString("")
            if (name.isEmpty()) continue
            val teacher = teacherParts.filter { it != name }.distinct().take(3).joinToString("、")

            listTimeRegex.findAll(timeText).toList().forEachIndexed { idx, m ->
                val segEnd = timeText.length
                val nextStart = listTimeRegex.findAll(timeText).toList()
                    .getOrNull(idx + 1)?.range?.first ?: segEnd
                val seg = timeText.substring(m.range.last + 1, nextStart)
                    .replace(Regex("[\\s\\n]"), "")
                val location = when {
                    seg.isEmpty() -> ""
                    locationKeyword.containsMatchIn(seg) -> locationKeyword.find(seg)!!.value
                    seg.length in 2..20 -> seg
                    else -> ""
                }
                val g = m.groupValues
                val day = when (g[3]) {
                    "一" -> 1; "二" -> 2; "三" -> 3; "四" -> 4; "五" -> 5; "六" -> 6; else -> 7
                }
                val startSlot = g[4].toIntOrNull() ?: 1
                val endSlot = g[5].toIntOrNull() ?: startSlot
                entries.add(
                    ParsedEntry(
                        name = name,
                        teacher = teacher,
                        location = location,
                        dayOfWeek = day,
                        startSlot = startSlot,
                        endSlot = endSlot,
                        startWeek = g[1].toIntOrNull() ?: 1,
                        endWeek = g[2].toIntOrNull() ?: 17,
                        weekPattern = WeekPattern.EVERY,
                    )
                )
            }
        }
        if (entries.isEmpty()) {
            throw OcrParseException("列表中未解析出上课时间，请确认截图包含「周次+星期+节次」列")
        }
        return entries
    }

    private fun parseGrid(allLines: List<Text.Line>): List<ParsedEntry> {

        // 0. 定位「周一~周日」表头（允许同格带日期，如「星期一 2026-09-07」「周一 9/7」），
        //    表头以上的一切（状态栏、标题、日期）全部丢弃
        val weekdayAny = Regex("[星期周][一二三四五六日天]")
        var headerLines = allLines.filter {
            dayPrefixRegex.containsMatchIn(it.text.replace(" ", "").trim())
        }
        if (headerLines.size < 2) {
            // 回退：短行中包含星期X 也接受（如「9月10日 星期四」合并行）；
            // 排除「1-17周 星期二 9-10节」这类列表时间行，它们属于版式 B
            headerLines = allLines.filter {
                val t = it.text.replace(" ", "")
                t.length <= 14 && weekdayAny.containsMatchIn(t) &&
                    !weekRange.containsMatchIn(t)
            }
        }
        if (headerLines.size < 2) {
            val sample = allLines.take(6).joinToString(" / ") { it.text.trim() }.take(100)
            throw OcrParseException(
                if (sample.isBlank()) "图片中未识别出任何文字，请换一张更清晰的课表截图"
                else "未能定位「周一~周日」表头。识别到的文字：$sample"
            )
        }
        val headerBottom = headerLines.maxOf { it.boundingBox!!.bottom }
        val lines = allLines.filter { it.boundingBox!!.top > headerBottom - 6 }

        // 1. 表头 → 每列 x 坐标（星期字样在行内任意位置均可）
        val dayColumns = mutableMapOf<Int, MutableList<Int>>()
        headerLines.forEach { line ->
            val t = line.text.replace(" ", "").trim()
            val wd = weekdayAny.find(t) ?: return@forEach
            val d = when (wd.value.last()) {
                '一' -> 1; '二' -> 2; '三' -> 3; '四' -> 4; '五' -> 5; '六' -> 6
                '日', '天' -> 7
                else -> null
            } ?: return@forEach
            dayColumns.getOrPut(d) { mutableListOf() }.add(line.boundingBox!!.centerX())
        }
        if (dayColumns.size < 2) {
            throw OcrParseException("未能识别出「周一~周日」表头，请换一张更清晰的课表截图")
        }
        val columns = dayColumns.entries
            .map { (d, xs) -> d to xs.average().toInt() }
            .sortedBy { it.second }
        val minX = columns.minOf { it.second }
        val columnGap = if (columns.size > 1) {
            (columns.maxOf { it.second } - minX).toDouble() / (columns.size - 1)
        } else 200.0

        // 2. 节次序号 → 行 y 坐标（序号 + 等差内插，抗个别序号漏识别）
        val slotMarkers = lines
            .filter { l ->
                val b = l.boundingBox!!
                b.centerX() < minX - 20 && pureNumber.matches(l.text.trim())
            }
            .sortedBy { it.boundingBox!!.top }
        if (slotMarkers.size < 2) {
            throw OcrParseException("未能识别出左侧的节次序号(1、2、3…)，请确保截图中包含节次列")
        }
        val maxSlot = slotMarkers.maxOf { it.text.trim().toIntOrNull() ?: 1 }
        val rowHeight = medianOfDiffs(slotMarkers.map { it.boundingBox!!.centerY() }).coerceAtLeast(20)

        data class Anchor(val row: Int, val y: Int)

        val anchors = slotMarkers.mapNotNull { m ->
            m.text.trim().toIntOrNull()?.let { Anchor(it, m.boundingBox!!.centerY()) }
        }.filter { it.row in 1..maxSlot }.groupBy { it.row }
            .map { (row, list) -> Anchor(row, list.sumOf { it.y } / list.size) }
            .sortedBy { it.row }
        val rowY = IntArray(maxSlot + 1)
        for (r in 1..maxSlot) {
            val exact = anchors.firstOrNull { it.row == r }
            rowY[r] = when {
                exact != null -> exact.y
                r < anchors.first().row -> anchors.first().y - (anchors.first().row - r) * rowHeight
                r > anchors.last().row -> anchors.last().y + (r - anchors.last().row) * rowHeight
                else -> {
                    val prev = anchors.last { it.row < r }
                    val next = anchors.first { it.row > r }
                    prev.y + (next.y - prev.y) * (r - prev.row) / (next.row - prev.row)
                }
            }
        }

        // 3. 文字块 →（星期, 节次行）
        val pieces = mutableListOf<Piece>()
        lines.forEach { line ->
            val t0 = line.text.trim().replace(" ", "")
            val b = line.boundingBox!!
            if (dayPrefixRegex.containsMatchIn(t0)) return@forEach
            if (noiseText.matches(t0) || timeText.matches(t0)) return@forEach
            if (dateText.matches(t0) || weekLabel.matches(t0)) return@forEach
            if (t0.startsWith("+") || t0.startsWith("＋")) return@forEach
            if (b.centerX() < minX - 20 && pureNumber.matches(t0)) return@forEach

            val day = columns.minByOrNull { abs(it.second - b.centerX()) }?.let { (d, x) ->
                if (abs(x - b.centerX()) <= columnGap * 0.75) d else null
            } ?: return@forEach

            val nearest = (1..maxSlot).minByOrNull { abs(rowY[it] - b.centerY()) }
            val row = nearest ?: return@forEach

            // 剥离与课程代码粘连的前缀；纯编号地点行（X1525）不能剥
            val cleaned = if (t0.any { it.code in 0x4E00..0x9FFF }) {
                t0.replace(courseCodePrefix, "")
            } else {
                t0
            }.ifBlank { return@forEach }
            pieces.add(Piece(day, row, b.top, b.bottom, cleaned))
        }

        // 4. 同一天内垂直相邻的行聚类为格子；跨行 = 连堂。
        //    教务表格特征：每节以「地点：」行结尾——出现该行即当前格完整（closed），
        //    之后的文字必属下一节；与已有内容重复的行也是新节开始的信号。
        //    两类信号都没有时才用物理间距（同格行距远小于行高）判断。
        data class Cell(val day: Int, val startSlot: Int, var endSlot: Int, val parts: MutableList<String>, var closed: Boolean)

        val cells = mutableListOf<Cell>()
        pieces.groupBy { it.day }.forEach { (_, dayPieces) ->
            var current: Cell? = null
            var lastBottom = Int.MIN_VALUE
            dayPieces.sortedWith(compareBy({ it.row }, { it.top })).forEach { p ->
                val cur = current
                val dup = cur != null && cur.parts.any { similarText(it, p.text) }
                val isLocationEnd = p.text.startsWith("地点")
                val near = cur != null && !cur.closed && !dup &&
                    p.row - cur.endSlot <= 1 && p.top - lastBottom < rowHeight * 0.45
                if (cur != null && near) {
                    cur.endSlot = maxOf(cur.endSlot, p.row)
                    cur.parts.add(p.text)
                    if (isLocationEnd) cur.closed = true
                } else {
                    current = Cell(p.day, p.row, p.row, mutableListOf(p.text), isLocationEnd)
                        .also { cells.add(it) }
                }
                lastBottom = p.bottom
            }
        }

        // 5. 解析格子
        val entries = mutableListOf<ParsedEntry>()
        cells.forEach { cell ->
            val cellText = cell.parts.joinToString("\n").trim()
            if (cellText.length < 2) return@forEach
            parseCellText(cellText, cell.day, cell.startSlot, cell.endSlot)?.let { entries.add(it) }
        }

        // 6. 连堂合并：教务表格每节重复课程全文，同名（含近似名）相邻合并为一个时间段
        return absorbOrphanLocations(mergeAdjacent(entries))
    }

    private fun medianOfDiffs(sorted: List<Int>): Int {
        if (sorted.size < 2) return 100
        val diffs = sorted.zipWithNext { a, b -> b - a }.filter { it > 0 }.sorted()
        return if (diffs.isEmpty()) 100 else diffs[diffs.size / 2]
    }

    // —— 课程列表版式的字段清洗 ——

    private val listSemesterNoise = Regex("\\d{4}\\s*[-–~]\\s*\\d{4}\\s*第?\\d?\\s*[学字]期")
    private val listCodeNoise = Regex("[A-Za-z]{1,5}\\d{4,}[A-Za-z0-9]{0,6}")
    private val listNatureWords = setOf("必修", "选修", "任选", "限选", "公选", "实践", "理论")

    private fun isNoiseToken(t: String): Boolean {
        if (t.isEmpty()) return true
        if (pureNumber.matches(t)) return true
        if (listCodeNoise.matches(t)) return true
        if (t.length <= 8 && listSemesterNoise.matches(t)) return true
        if (t in listNatureWords) return true
        if (t.length in 2..4 && (t.endsWith("学院") || t.endsWith("系"))) return true
        if (t == "节" || t == "周" || dateText.matches(t)) return true
        return false
    }

    /**
     * 把一行（可能粘连了整行表格内容）拆分为 课名 / 教师：
     * 优先用 ML Kit 元素级分词（每个元素带独立文本框）；元素不可用时用
     * 「剥离噪声模式 + 中文分段」兜底。粘连段里的学期/编号/学分/性质全部剥除。
     */
    /** 常见开课学院短名（与 2-3 字教师名同形，用于排除） */
    private val collegeWords = setOf(
        "机械", "外语", "体育", "设计", "生命", "心理", "马院", "思政",
        "人文", "经管", "艺术", "材料", "化工", "信息", "土木", "电气",
        "计算机", "数理", "法学", "音乐", "美术", "建筑", "环境", "医学",
    )

    /**
     * 从一行（或一行粘连文本）中提取课名与教师。
     * 剥除：学期 / 选课编号·课程代码 / 独立数字 / 必修选修等 / 时间块；
     * 剩余中文分段按序：首段=课名，其后 2-4 字且非学院名的段=教师（最多 3 人）。
     */
    internal fun extractListNameTeacher(
        raw: String,
        nameParts: MutableList<String>,
        teacherParts: MutableList<String>,
    ) {
        var s = raw.trim()
        s = listSemesterNoise.replace(s, " ")
        s = listCodeNoise.replace(s, " ")
        s = listTimeRegex.replace(s, " ")
        s = weekRange.replace(s, " ")
        s = Regex("\\d+(\\.\\d+)?").replace(s, " ")
        s = Regex("(必修|选修|任选|限选|公选)").replace(s, " ")
        val runs = Regex("[\\u4e00-\\u9fa5]{2,}").findAll(s).map { it.value }.toList()
        runs.forEachIndexed { idx, run ->
            when {
                idx == 0 -> if (run.length >= 2) nameParts.add(run)
                run in collegeWords -> Unit
                run.length in 2..4 && teacherName.matches(run) && teacherParts.size < 3 ->
                    teacherParts.add(run)
            }
        }
    }

    /**
     * 连堂合并时的近似同名判断：对 OCR 随机错字鲁棒——
     * 完全相同 / 互为前缀 / 字符集合交集占比 ≥ 0.65（不依赖字序，
     * 「体育亚/体体育」「模理基础/模型基础」这类错字都能命中，而
     * 「大学英语/大学体育」(0.5) 不会误合并）。
     */
    private fun similarNames(a: String, b: String): Boolean {
        if (a == b || a.startsWith(b) || b.startsWith(a)) return true
        val sa = a.toSet()
        val sb = b.toSet()
        if (sa.size < 2 || sb.size < 2) return false
        return sa.intersect(sb).size.toDouble() / maxOf(sa.size, sb.size) >= 0.65
    }

    /** 聚类用：两行文本高度相似说明是相邻两节重复的课程全文，不应并进同一格 */
    private fun similarText(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.startsWith(b) || b.startsWith(a)) return true
        return a.commonPrefixWith(b).length.toDouble() / maxOf(a.length, b.length) >= 0.6
    }

    internal fun parseCellText(text: String, day: Int, startSlot: Int, endSlot: Int): ParsedEntry? {
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null
        val pattern = when {
            text.contains("单周") -> WeekPattern.ODD
            text.contains("双周") -> WeekPattern.EVEN
            else -> WeekPattern.EVERY
        }
        var startWeek = 1
        var endWeek = 16
        weekRange.find(text)?.let { m ->
            m.groupValues[1].toIntOrNull()?.let { startWeek = it }
            m.groupValues[2].toIntOrNull()?.let { endWeek = it }
        }

        // 地点：①「地点：」前缀行 ② 场所关键字行 + 紧随编号 ③ 纯编号行
        var location = ""
        var nameEnd = -1
        var locEnd = -1
        val colonIdx = lines.indexOfFirst { it.contains("地点：") || it.contains("地点:") }
        if (colonIdx > 0) {
            location = lines[colonIdx].replace("地点：", "").replace("地点:", "").trim()
            nameEnd = colonIdx
            locEnd = colonIdx
        } else {
            val kwIdx = lines.indexOfFirst {
                it != lines.first() && locationKeyword.containsMatchIn(it) && it.length <= 25
            }
            if (kwIdx > 0) {
                location = lines[kwIdx]
                var i = kwIdx + 1
                while (i < lines.size && pureCodeText.matches(lines[i])) {
                    location += lines[i]
                    i++
                }
                nameEnd = kwIdx
                locEnd = i - 1
            } else if (lines.size >= 2 && pureCodeText.matches(lines[1])) {
                location = lines[1]
                nameEnd = 1
                locEnd = 1
            }
        }

        // 名称：地点区域之前的所有行拼接——长课名不拆分
        val nameBlockEnd = if (nameEnd > 0) nameEnd else lines.size
        var name = lines.take(nameBlockEnd)
            .filter { !weekRange.containsMatchIn(it) }
            .joinToString("") { it.replace("单周", "").replace("双周", "") }
            .replace(courseCodePrefix, "")
            .trim()

        // 教师：名称内最后一个人名样式括号，否则地点区域之外的独立汉字短行
        var teacher = ""
        val brackets = bracketText.findAll(name).toList()
        if (brackets.isNotEmpty() && teacherName.matches(brackets.last().groupValues[1])) {
            teacher = brackets.last().groupValues[1]
            name = name.replaceRange(brackets.last().range, "")
        }
        name = name.trim().trim('－', '-', '·', '，', ',', '、')
        if (name.isEmpty()) return null

        if (teacher.isEmpty()) {
            val ti = rawLineIndexForTeacher(lines, nameBlockEnd, locEnd, name)
            if (ti != null) teacher = lines[ti]
        }

        return ParsedEntry(
            name = name,
            teacher = teacher,
            location = location,
            dayOfWeek = day,
            startSlot = startSlot,
            endSlot = maxOf(startSlot, endSlot),
            startWeek = startWeek,
            endWeek = maxOf(startWeek, endWeek),
            weekPattern = pattern,
        )
    }

    private fun rawLineIndexForTeacher(
        lines: List<String>,
        nameBlockEnd: Int,
        locEnd: Int,
        name: String,
    ): Int? = lines.indices.firstOrNull { i ->
        i >= nameBlockEnd && (locEnd < 0 || i > locEnd) &&
            teacherName.matches(lines[i]) && lines[i] != name
    }

    /** 同名同天且节次相邻（或重叠）的条目合并为一个时间段 */
    internal fun mergeAdjacent(entries: List<ParsedEntry>): List<ParsedEntry> {
        val result = mutableListOf<ParsedEntry>()
        entries.sortedWith(compareBy({ it.dayOfWeek }, { it.startSlot })).forEach { e ->
            val prev = result.lastOrNull {
                it.dayOfWeek == e.dayOfWeek &&
                    similarNames(it.name, e.name) &&
                    e.startSlot <= it.endSlot + 1 &&
                    it.startWeek == e.startWeek && it.endWeek == e.endWeek &&
                    it.weekPattern == e.weekPattern
            }
            if (prev != null) {
                result[result.indexOf(prev)] = prev.copy(
                    endSlot = maxOf(prev.endSlot, e.endSlot),
                    location = prev.location.ifBlank { e.location },
                    teacher = prev.teacher.ifBlank { e.teacher },
                )
            } else {
                result.add(e)
            }
        }
        return result
    }

    /**
     * 被误识别成独立课程的地点行（如「教学楼A101」单独成格）：
     * 名称像地点/编号、无教师、且与同天某条目节次相邻时，并回该条目的地点。
     */
    private fun absorbOrphanLocations(entries: List<ParsedEntry>): List<ParsedEntry> {
        val orphans = entries.filter {
            it.teacher.isBlank() && it.name.length <= 12 &&
                (locationKeyword.containsMatchIn(it.name) || pureCodeText.matches(it.name))
        }
        if (orphans.isEmpty()) return entries
        val result = entries.toMutableList()
        orphans.forEach { orphan ->
            val host = result.lastOrNull {
                it !== orphan && it.dayOfWeek == orphan.dayOfWeek &&
                    orphan.startSlot <= it.endSlot + 2 && orphan.startSlot >= it.startSlot &&
                    !locationKeyword.containsMatchIn(it.name) && !pureCodeText.matches(it.name)
            } ?: return@forEach
            result[result.indexOf(host)] = host.copy(
                endSlot = maxOf(host.endSlot, orphan.endSlot),
                location = host.location.ifBlank { orphan.name },
            )
            result.remove(orphan)
        }
        return result
    }
}
