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
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.background
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.SizeMode
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.balsam.timetable.Graph
import com.balsam.timetable.data.model.WeekLogic
import com.balsam.timetable.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** 今日课程桌面小组件：时间轴节点 + 圆角课程卡片，进行中的课高亮 */
class TodayWidget : GlanceAppWidget() {

    /** 按真实尺寸渲染，LocalSize 才能拿到 launcher 实际分配的高度来决定显示几行 */
    override val sizeMode = SizeMode.Exact

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
        val emptyText: String = "今天没有课",
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // 任何异常都不能让小组件永远停在加载动画：降级为错误文案
        val data = try {
            withContext(Dispatchers.IO) { buildTodayData() }
        } catch (e: Exception) {
            WidgetData("数据加载失败", "", emptyList(), "数据加载失败，点这里重试")
        }
        val openIntent = Intent(context, MainActivity::class.java)
        provideContent { Content(data, openIntent) }
    }

    private suspend fun buildTodayData(): WidgetData {
        val semester = Graph.repository.currentSemesterOnce()
            ?: return WidgetData("未设置学期", "", emptyList(), "先在应用里设置学期")
        val today = LocalDate.now()
        // 固定中文 locale：小组件文案全中文，避免英文系统下「10月25日 Sunday」混排
        val dateLabel = today.format(DateTimeFormatter.ofPattern("M月d日 EEEE").withLocale(java.util.Locale.CHINESE))
        val holidayCal = Graph.settingsRepository.holidayCalendarOnce()
        if (holidayCal.isOff(today)) {
            val week = WeekLogic.weekOf(today, semester.startDate, semester.totalWeeks)
            return WidgetData(
                dateLabel,
                week?.let { "第${it}周" } ?: "假期中",
                emptyList(),
                "今天放假，好好休息",
            )
        }
        val week = WeekLogic.weekOf(today, semester.startDate, semester.totalWeeks)
            ?: return WidgetData(dateLabel, "假期中", emptyList())
        val numbered = Graph.repository.slotsOnce(semester.id)
        val courses = Graph.repository.coursesOnce(semester.id)
        // 调休上班日按用户设置的周几的课表显示（未设置为当天自然星期）
        val dow = holidayCal.effectiveDayOfWeek(today)
        val now = LocalTime.now()

        val rows = courses.flatMap { cwl ->
            cwl.lessons.filter {
                it.dayOfWeek == dow && WeekLogic.lessonActive(week, it.weekSet)
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
                    .padding(11.dp)
                    .background(GlanceTheme.colors.surface)
                    .cornerRadius(20.dp)
                    .clickable(onClick = actionStartActivity(openIntent)),
            ) {
                // 头部：日期 + 周次胶囊徽章
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
                        Box(
                            GlanceModifier
                                .background(GlanceTheme.colors.primaryContainer)
                                .cornerRadius(50.dp)
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                        ) {
                            Text(
                                data.weekLabel,
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GlanceTheme.colors.onPrimaryContainer,
                                ),
                                maxLines = 1,
                            )
                        }
                    }
                }
                Spacer(GlanceModifier.height(9.dp))

                val rows = data.rows
                when {
                    rows.isEmpty() -> EmptyState(data.emptyText, GlanceModifier.defaultWeight())
                    else -> {
                        // 按小组件实际高度算能完整放下几行，放不下的收进溢出行，避免末行被裁一半
                        val capacity = maxRowsFor(LocalSize.current.height.value)
                        val shown = rows.take(capacity)
                        shown.forEachIndexed { i, r ->
                            CourseRow(r)
                            if (i < shown.lastIndex || rows.size > capacity) TimelineGap()
                        }
                        if (rows.size > capacity) {
                            Text(
                                "还有 ${rows.size - capacity} 门课…",
                                modifier = GlanceModifier.padding(start = 22.dp),
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    color = GlanceTheme.colors.onSurfaceVariant,
                                ),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }

    /** 空状态：居中的圆形表情 + 一句话；modifier 由调用处传入以撑满剩余高度 */
    @Composable
    private fun EmptyState(message: String, modifier: GlanceModifier = GlanceModifier) {
        Column(
            modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                GlanceModifier
                    .size(34.dp)
                    .background(GlanceTheme.colors.primaryContainer)
                    .cornerRadius(50.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("🌿", style = TextStyle(fontSize = 15.sp))
            }
            Spacer(GlanceModifier.height(6.dp))
            Text(
                message,
                style = TextStyle(
                    fontSize = 13.sp,
                    color = GlanceTheme.colors.onSurfaceVariant,
                ),
                maxLines = 1,
            )
        }
    }

    /** 头部与内容行之间、以及内容行之间的竖线连接段 */
    @Composable
    private fun TimelineGap() {
        Row(GlanceModifier.fillMaxWidth().height(6.dp)) {
            Box(
                GlanceModifier.width(16.dp).height(6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    GlanceModifier
                        .width(2.dp)
                        .height(6.dp)
                        .background(railColor()),
                ) {}
            }
        }
    }

    @Composable
    private fun CourseRow(r: RowData) {
        val ctx = androidx.glance.LocalContext.current
        val done = r.status == STATUS_DONE
        val running = r.running
        // 进行中用容器色高亮整卡；已结束背景不变，仅淡化文字
        val cardBg = if (running) GlanceTheme.colors.primaryContainer else GlanceTheme.colors.surfaceVariant
        val nameColor = when {
            done -> ColorProvider(GlanceTheme.colors.onSurface.getColor(ctx).copy(alpha = 0.42f))
            running -> GlanceTheme.colors.onPrimaryContainer
            else -> GlanceTheme.colors.onSurface
        }
        val subColor = when {
            done -> ColorProvider(GlanceTheme.colors.onSurfaceVariant.getColor(ctx).copy(alpha = 0.45f))
            running -> ColorProvider(GlanceTheme.colors.onPrimaryContainer.getColor(ctx).copy(alpha = 0.78f))
            else -> GlanceTheme.colors.onSurfaceVariant
        }

        Row(
            GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 时间轴节点列
            Box(
                GlanceModifier.width(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    GlanceModifier
                        .width(2.dp)
                        .height(38.dp)
                        .background(railColor()),
                ) {}
                if (running) {
                    // 发光节点：外圈容器色 + 内芯主题色
                    Box(
                        GlanceModifier
                            .size(14.dp)
                            .background(GlanceTheme.colors.primaryContainer)
                            .cornerRadius(50.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            GlanceModifier
                                .size(8.dp)
                                .background(GlanceTheme.colors.primary)
                                .cornerRadius(50.dp),
                        ) {}
                    }
                } else {
                    Box(
                        GlanceModifier
                            .size(if (done) 6.dp else 8.dp)
                            .background(
                                ColorProvider(
                                    GlanceTheme.colors.onSurfaceVariant
                                        .getColor(ctx)
                                        .copy(alpha = if (done) 0.30f else 0.55f)
                                )
                            )
                            .cornerRadius(50.dp),
                    ) {}
                }
            }
            // 课程卡片
            Row(
                GlanceModifier
                    .defaultWeight()
                    .background(cardBg)
                    .cornerRadius(13.dp)
                    .padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(GlanceModifier.width(46.dp)) {
                    Text(
                        r.start,
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = nameColor,
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
                Spacer(GlanceModifier.width(10.dp))
                Text(
                    r.name,
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = if (running) FontWeight.Bold else FontWeight.Medium,
                        color = nameColor,
                    ),
                    maxLines = 2,
                )
                Spacer(GlanceModifier.width(6.dp))
                if (r.location.isNotBlank()) {
                    Text(
                        r.location,
                        style = TextStyle(fontSize = 11.sp, color = subColor),
                        maxLines = 1,
                    )
                }
            }
        }
    }

    @Composable
    private fun railColor(): ColorProvider = ColorProvider(
        GlanceTheme.colors.onSurfaceVariant
            .getColor(androidx.glance.LocalContext.current)
            .copy(alpha = 0.28f)
    )

    suspend fun updateAll(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        manager.getGlanceIds(TodayWidget::class.java).forEach { update(context, it) }
    }

    private companion object {
        const val MAX_ROWS = 6
        // 数值即排序优先级：进行中 → 未开始 → 已结束
        const val STATUS_RUNNING = 0
        const val STATUS_UPCOMING = 1
        const val STATUS_DONE = 2

        /** 估算该高度下能完整显示的课程行数：外边距22 + 头部33 + 溢出行18，每行约50（卡片44+间距6） */
        fun maxRowsFor(heightDp: Float): Int =
            (((heightDp - 73f) / 50f).toInt()).coerceIn(1, MAX_ROWS)
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
