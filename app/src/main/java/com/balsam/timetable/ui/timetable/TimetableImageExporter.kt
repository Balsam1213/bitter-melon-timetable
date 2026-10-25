package com.balsam.timetable.ui.timetable

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.balsam.timetable.data.db.CourseWithLessons
import com.balsam.timetable.data.db.Lesson
import com.balsam.timetable.data.db.Semester
import com.balsam.timetable.data.db.TimeSlot
import com.balsam.timetable.data.model.DAY_LABELS
import com.balsam.timetable.data.model.WeekLogic
import com.balsam.timetable.data.model.WeeksCodec
import java.io.File
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * 周课表导出为图片（Canvas 自绘，固定浅色样式）：标题（学期 + 第N周 + 日期范围）、
 * 表头（周X + 日期）、左列节次、该周实际上课的课程色块（课名/地点/周次标签），
 * 同节次重叠多门时左右分栏。供「分享课表图片」使用。
 */
object TimetableImageExporter {

    private const val W = 1080
    private const val PAD = 24f
    private const val TIME_COL = 104f
    private const val TITLE_H = 140f
    private const val HEAD_H = 100f
    private const val SLOT_H = 150f
    private const val FOOTER_H = 72f

    private const val BG = 0xFFF6F4FB
    private const val CARD_BG = 0xFFFFFFFF
    private const val TITLE_FG = 0xFF2A1155
    private const val SUB_FG = 0xFF6B6480
    private const val LINE = 0xFFE4E0EF

    fun render(
        semester: Semester,
        slots: List<TimeSlot>,
        courses: List<CourseWithLessons>,
        week: Int,
        showWeekend: Boolean,
        today: LocalDate,
    ): Bitmap {
        val days = if (showWeekend) 7 else 5
        val rows = slots.size.coerceAtLeast(1)
        val gridTop = TITLE_H + HEAD_H
        val gridH = rows * SLOT_H
        val h = (gridTop + gridH + FOOTER_H).roundToInt()
        val bmp = androidx.core.graphics.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(BG.toInt())
        val weekStart = WeekLogic.weekMonday(semester.startDate, week)

        // ---------- 标题 ----------
        val titleP = textPaint(48f, TITLE_FG.toInt(), bold = true).apply { textAlign = Paint.Align.CENTER }
        canvas.drawText(semester.name, W / 2f, 64f, titleP)
        val subP = textPaint(32f, SUB_FG.toInt()).apply { textAlign = Paint.Align.CENTER }
        val range = "${weekStart.monthValue}月${weekStart.dayOfMonth}日 - " +
            weekStart.plusDays(6).let { "${it.monthValue}月${it.dayOfMonth}日" }
        canvas.drawText("第${week}周 · $range", W / 2f, 110f, subP)

        // ---------- 表头 ----------
        val dayW = (W - PAD * 2 - TIME_COL) / days
        val headBar = RectF(PAD, TITLE_H + 8f, W - PAD, TITLE_H + HEAD_H - 4f)
        canvas.drawRoundRect(headBar, 18f, 18f, fillPaint(CARD_BG.toInt()))
        for (d in 1..days) {
            val cx = PAD + TIME_COL + (d - 1) * dayW + dayW / 2
            val date = weekStart.plusDays((d - 1).toLong())
            val isToday = date == today
            val dayP = textPaint(36f, TITLE_FG.toInt(), bold = true)
            dayP.textAlign = Paint.Align.CENTER
            val dateP = textPaint(28f, if (isToday) TITLE_FG.toInt() else SUB_FG.toInt())
            dateP.textAlign = Paint.Align.CENTER
            val y0 = TITLE_H + 46f
            canvas.drawText(DAY_LABELS[d - 1], cx, y0, dayP)
            canvas.drawText("${date.monthValue}/${date.dayOfMonth}", cx, y0 + 38f, dateP)
        }

        // ---------- 左列节次 ----------
        val numP = textPaint(32f, SUB_FG.toInt(), bold = true).apply { textAlign = Paint.Align.CENTER }
        val timeP = textPaint(24f, SUB_FG.toInt()).apply { textAlign = Paint.Align.CENTER }
        slots.forEachIndexed { i, s ->
            val cy = gridTop + i * SLOT_H
            canvas.drawText("${s.orderIndex}", PAD + TIME_COL / 2, cy + 56f, numP)
            canvas.drawText(s.startTime, PAD + TIME_COL / 2, cy + 88f, timeP)
            canvas.drawText(s.endTime, PAD + TIME_COL / 2, cy + 114f, timeP)
            // 行分隔线
            canvas.drawLine(PAD, cy, W - PAD, cy, linePaint())
        }

        // ---------- 课程块 ----------
        for (d in 1..days) {
            val entries = courses.flatMap { cwl ->
                cwl.lessons.filter {
                    it.dayOfWeek == d && WeekLogic.lessonActive(week, it.weekSet)
                }.map { it to cwl }
            }
            // 与课表页一致的重叠分组
            val groups = mutableListOf<MutableList<Pair<Lesson, CourseWithLessons>>>()
            entries.sortedBy { it.first.startSlot }.forEach { e ->
                val g = groups.firstOrNull { grp ->
                    grp.any { it.first.startSlot <= e.first.endSlot && e.first.startSlot <= it.first.endSlot }
                }
                if (g != null) g.add(e) else groups.add(mutableListOf(e))
            }
            groups.forEach { group ->
                val first = group.first().first
                val top = gridTop + (first.startSlot - 1) * SLOT_H + 5f
                val bottom = gridTop + first.endSlot * SLOT_H - 5f
                val colX0 = PAD + TIME_COL + (d - 1) * dayW + 4f
                val colX1 = PAD + TIME_COL + d * dayW - 4f
                val width = (colX1 - colX0) / group.size
                group.forEachIndexed { i, (lesson, cwl) ->
                    val x0 = colX0 + i * width + (if (i > 0) 3f else 0f)
                    val x1 = colX0 + (i + 1) * width - (if (i < group.size - 1) 3f else 0f)
                    drawCourse(canvas, cwl, lesson, semester.totalWeeks, x0, top, x1, bottom)
                }
            }
        }

        // ---------- 水印 ----------
        val footP = textPaint(26f, SUB_FG.toInt()).apply { textAlign = Paint.Align.CENTER }
        canvas.drawText("苦瓜课表", W / 2f, gridTop + gridH + 48f, footP)
        return bmp
    }

    private fun drawCourse(
        canvas: Canvas,
        cwl: CourseWithLessons,
        lesson: Lesson,
        totalWeeks: Int,
        x0: Float,
        top: Float,
        x1: Float,
        bottom: Float,
    ) {
        val color = cwl.course.colorArgb.toInt()
        canvas.drawRoundRect(RectF(x0, top, x1, bottom), 14f, 14f, fillPaint(color))
        // 依据亮度选黑/白文字
        val r = Color.red(color) / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color) / 255f
        val fg = if (0.299f * r + 0.587f * g + 0.114f * b > 0.72f) Color.BLACK else Color.WHITE

        val pad = 14f
        val maxW = x1 - x0 - pad * 2
        var y = top + 38f
        val nameP = textPaint(34f, fg, bold = true)
        wrap(nameP, cwl.course.name, maxW, 3).forEach { line ->
            canvas.drawText(line, x0 + pad, y, nameP); y += 40f
        }
        if (lesson.location.isNotBlank() && bottom - y > 68f) {
            val locP = textPaint(28f, fg)
            wrap(locP, lesson.location, maxW, 2).forEach { line ->
                canvas.drawText(line, x0 + pad, y, locP); y += 34f
            }
        }
        val label = WeeksCodec.label(lesson.weekSet, totalWeeks)
        if (label != null && bottom - y > 40f) {
            canvas.drawText(label, x0 + pad, y + 30f, textPaint(24f, fg))
        }
    }

    /** 按像素宽度折行，超出 maxLines 时最后一行省略号截断 */
    private fun wrap(p: Paint, text: String, maxW: Float, maxLines: Int): List<String> {
        val lines = mutableListOf<String>()
        var cur = StringBuilder()
        text.forEach { ch ->
            if (p.measureText(cur.toString() + ch) > maxW) {
                if (lines.size == maxLines - 1) {
                    var t = cur.toString()
                    while (t.isNotEmpty() && p.measureText("$t…") > maxW) t = t.dropLast(1)
                    lines.add("$t…")
                    return lines
                }
                lines.add(cur.toString())
                cur = StringBuilder().append(ch)
            } else {
                cur.append(ch)
            }
        }
        if (cur.isNotEmpty()) lines.add(cur.toString())
        return lines
    }

    private fun textPaint(size: Float, color: Int, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.textSize = size
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun fillPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun linePaint() = Paint().apply { color = LINE.toInt(); strokeWidth = 2f }

    /** 写入 cache/share 下的 PNG，返回文件（FileProvider 分享用） */
    fun writePng(context: Context, bmp: Bitmap): File {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "timetable_week_${System.currentTimeMillis()}.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f
    }

    /** 保存到相册 Pictures/苦瓜课表；仅支持 API 29+，低版本返回 false */
    fun saveToGallery(context: Context, bmp: Bitmap): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "课表_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/苦瓜课表")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return false
        context.contentResolver.openOutputStream(uri)?.use {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
        } ?: return false
        return true
    }
}
