package com.balsam.timetable.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.background
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.balsam.timetable.Graph
import com.balsam.timetable.data.model.WeekLogic
import com.balsam.timetable.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** 今日课程桌面小组件：行卡片排版，进行中的课程高亮 */
class TodayWidget : GlanceAppWidget() {

    data class RowData(
        val start: String,
        val end: String,
        val name: String,
        val location: String,
        val running: Boolean,
        val status: Int,
    )

    data class WidgetData(
        val dateLabel: String,
        val weekLabel: String,
        val rows: List<RowData>,
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // 任何异常都不能让小组件永远停在加载动画：降级为错误文案
        val data = try {
            withContext(Dispatchers.IO) { buildTodayData() }
        } catch (e: Exception) {
            WidgetData("数据加载失败", e.message?.take(60) ?: "", emptyList())
        }
        val openIntent = Intent(context, MainActivity::class.java)
        provideContent { Content(data, openIntent) }
    }

    private suspend fun buildTodayData(): WidgetData {
        val semester = Graph.repository.currentSemesterOnce()
            ?: return WidgetData("未设置学期", "", emptyList())
        val today = LocalDate.now()
        val dateLabel = today.format(DateTimeFormatter.ofPattern("M月d日 EEEE"))
        val week = WeekLogic.weekOf(today, semester.startDate, semester.totalWeeks)
            ?: return WidgetData(dateLabel, "假期中", emptyList())
        val numbered = Graph.repository.slotsOnce(semester.id)
        val courses = Graph.repository.coursesOnce(semester.id)
        val dow = today.dayOfWeek.value
        val now = LocalTime.now()

        val rows = courses.flatMap { cwl ->
            cwl.lessons.filter {
                it.dayOfWeek == dow && WeekLogic.lessonActive(week, it.startWeek, it.endWeek, it.pattern)
            }.map { lesson ->
                val start = numbered.getOrNull(lesson.startSlot - 1)
                val end = numbered.getOrNull(lesson.endSlot - 1)
                val startT = start?.let { runCatching { LocalTime.parse(it.startTime) }.getOrNull() }
                val endT = end?.let { runCatching { LocalTime.parse(it.endTime) }.getOrNull() }
                val status = when {
                    startT == null || endT == null -> STATUS_UPCOMING
                    endT < now -> STATUS_DONE
                    startT <= now -> STATUS_RUNNING
                    else -> STATUS_UPCOMING
                }
                RowData(
                    start = start?.startTime ?: "",
                    end = end?.endTime ?: "",
                    name = cwl.course.name,
                    location = lesson.location,
                    running = status == STATUS_RUNNING,
                    status = status,
                )
            }
        }.sortedWith(
            // 进行中 → 未开始 → 已结束；同状态按开始时间
            compareBy({ it.status }, { it.start })
        )
        return WidgetData(dateLabel, "第${week}周", rows)
    }

    @Composable
    private fun Content(data: WidgetData, openIntent: Intent) {
        GlanceTheme {
            Column(
                GlanceModifier
                    .fillMaxSize()
                    .padding(9.dp)
                    .background(GlanceTheme.colors.surface)
                    .clickable(onClick = actionStartActivity(openIntent)),
            ) {
                // 头部：日期 + 周次徽标
                Row(
                    GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        data.dateLabel,
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = GlanceTheme.colors.onSurface,
                        ),
                        maxLines = 1,
                    )
                    Spacer(GlanceModifier.defaultWeight())
                    if (data.weekLabel.isNotBlank()) {
                        Text(
                            data.weekLabel,
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = GlanceTheme.colors.primary,
                            ),
                            maxLines = 1,
                        )
                    }
                }
                Spacer(GlanceModifier.height(7.dp))

                val rows = data.rows
                when {
                    rows.isEmpty() -> Text(
                        "今天没有课 🎉",
                        style = TextStyle(fontSize = 14.sp, color = GlanceTheme.colors.onSurfaceVariant),
                    )
                    else -> {
                        val shown = rows.take(MAX_ROWS)
                        shown.forEach { r ->
                            CourseRow(r)
                            Spacer(GlanceModifier.height(5.dp))
                        }
                        if (rows.size > MAX_ROWS) {
                            Text(
                                "还有 ${rows.size - MAX_ROWS} 门课…",
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    color = GlanceTheme.colors.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun CourseRow(r: RowData) {
        val context = androidx.glance.LocalContext.current
        // 已结束整行灰化并压低不透明度；进行中/未开始正常配色
        val done = r.status == STATUS_DONE
        val base = if (r.running) GlanceTheme.colors.secondaryContainer.getColor(context)
        else GlanceTheme.colors.surfaceVariant.getColor(context)
        val rowColor = if (done) {
            val c = GlanceTheme.colors.surfaceVariant.getColor(context)
            Color(c.red, c.green, c.blue, c.alpha * 0.45f)
        } else base
        val titleColor = when {
            done -> GlanceTheme.colors.onSurfaceVariant
            r.running -> GlanceTheme.colors.onSecondaryContainer
            else -> GlanceTheme.colors.onSurface
        }
        val subColor = if (r.running && !done) GlanceTheme.colors.onSecondaryContainer
        else GlanceTheme.colors.onSurfaceVariant
        val title = buildString {
            append(r.name)
            if (r.running) insert(0, "▶ ")
            if (done) append("（已结束）")
        }
        Row(
            GlanceModifier
                .fillMaxWidth()
                .background(rowColor, rowColor)
                .padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧时间列
            Column(GlanceModifier.width(74.dp)) {
                Text(
                    r.start,
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                    ),
                    maxLines = 1,
                )
                if (r.end.isNotBlank()) {
                    Text(
                        r.end,
                        style = TextStyle(fontSize = 10.sp, color = subColor),
                        maxLines = 1,
                    )
                }
            }
            Spacer(GlanceModifier.width(8.dp))
            // 中间课名：限定宽度、最多两行、超长省略——不再挤压右侧地点
            Text(
                title,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = if (r.running) FontWeight.Bold else FontWeight.Medium,
                    color = titleColor,
                ),
                maxLines = 2,
            )
            Spacer(GlanceModifier.width(6.dp))
            if (r.location.isNotBlank()) {
                Text(
                    r.location,
                    style = TextStyle(fontSize = 12.sp, color = subColor),
                    maxLines = 1,
                )
            }
        }
    }

    suspend fun updateAll(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        manager.getGlanceIds(TodayWidget::class.java).forEach { update(context, it) }
    }

    private companion object {
        const val MAX_ROWS = 6
        const val STATUS_DONE = 0
        const val STATUS_RUNNING = 1
        const val STATUS_UPCOMING = 2
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
