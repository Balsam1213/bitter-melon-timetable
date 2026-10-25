package com.balsam.timetable.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
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
import androidx.glance.unit.ColorProvider
import com.balsam.timetable.Graph
import com.balsam.timetable.data.db.CourseWithLessons
import com.balsam.timetable.data.model.DAY_LABELS
import com.balsam.timetable.data.model.WeekLogic
import com.balsam.timetable.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** 周视图里的一条课程（纯数据，供单测） */
internal data class WeekEntry(val name: String, val location: String, val startSlot: Int)

/** 某天（可能是调休映射后的周几）在某周要上的课，按节次排序 */
internal fun weekEntriesFor(
    courses: List<CourseWithLessons>,
    week: Int,
    dayOfWeek: Int,
): List<WeekEntry> = courses.flatMap { cwl ->
    cwl.lessons
        .filter { it.dayOfWeek == dayOfWeek && WeekLogic.lessonActive(week, it.weekSet) }
        .map { WeekEntry(cwl.course.name, it.location, it.startSlot) }
}.sortedBy { it.startSlot }

/** 本周课程概览桌面小组件：一天一列，今天高亮 */
class WeekWidget : GlanceAppWidget() {

    /** 按真实尺寸渲染，按高度决定每列显示几门课 */
    override val sizeMode = SizeMode.Exact

    internal data class DayColumn(
        val dow: Int,
        val label: String,
        val entries: List<WeekEntry>,
        val offDay: Boolean,
    )

    internal data class WeekData(
        val weekLabel: String,
        val todayDow: Int?,
        val days: List<DayColumn> = emptyList(),
        val errorText: String? = null,
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // 任何异常都不能让小组件永远停在加载动画：降级为错误文案
        val data = try {
            withContext(Dispatchers.IO) { buildWeekData() }
        } catch (e: Exception) {
            WeekData("数据加载失败", null, errorText = "数据加载失败，点这里重试")
        }
        val openIntent = Intent(context, MainActivity::class.java)
        provideContent { Content(data, openIntent) }
    }

    private suspend fun buildWeekData(): WeekData {
        val semester = Graph.repository.currentSemesterOnce()
            ?: return WeekData("未设置学期", null, errorText = "先在应用里设置学期")
        val today = LocalDate.now()
        val week = WeekLogic.weekOf(today, semester.startDate, semester.totalWeeks)
            ?: return WeekData("假期中", null, errorText = "假期中，没有要上的课")
        val settings = Graph.settingsRepository.once()
        val holidayCal = Graph.settingsRepository.holidayCalendarOnce()
        val courses = Graph.repository.coursesOnce(semester.id)
        val weekStart = WeekLogic.weekMonday(semester.startDate, week)
        val dayCount = if (settings.showWeekend) 7 else 5

        val days = (1..dayCount).map { dow ->
            val date = weekStart.plusDays((dow - 1).toLong())
            val off = holidayCal.isOff(date)
            // 调休上班日按用户设置的周几的课表（默认当天自然星期）
            val effDow = holidayCal.effectiveDayOfWeek(date)
            DayColumn(
                dow = dow,
                label = DAY_LABELS[dow - 1].removePrefix("周"),
                entries = if (off) emptyList() else weekEntriesFor(courses, week, effDow),
                offDay = off,
            )
        }
        return WeekData("第${week}周", today.dayOfWeek.value, days)
    }

    @Composable
    private fun Content(data: WeekData, openIntent: Intent) {
        GlanceTheme {
            Column(
                GlanceModifier
                    .fillMaxSize()
                    .padding(11.dp)
                    .background(GlanceTheme.colors.surface)
                    .cornerRadius(20.dp)
                    .clickable(onClick = actionStartActivity(openIntent)),
            ) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        data.weekLabel,
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = GlanceTheme.colors.onSurface,
                        ),
                        maxLines = 1,
                    )
                    Spacer(GlanceModifier.defaultWeight())
                    Text(
                        "本周课程",
                        style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant),
                        maxLines = 1,
                    )
                }
                Spacer(GlanceModifier.height(8.dp))
                if (data.errorText != null && data.days.isEmpty()) {
                    Column(
                        GlanceModifier.defaultWeight().fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            data.errorText,
                            style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant),
                            maxLines = 2,
                        )
                    }
                } else {
                    Row(GlanceModifier.defaultWeight().fillMaxWidth()) {
                        data.days.forEachIndexed { i, day ->
                            if (i > 0) Spacer(GlanceModifier.width(4.dp))
                            DayColumnContent(
                                day = day,
                                todayDow = data.todayDow,
                                widgetHeightDp = LocalSize.current.height.value,
                                // defaultWeight 只在 Row/Column 作用域内可用，列宽均分在这里求值
                                modifier = GlanceModifier.defaultWeight(),
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun DayColumnContent(
        day: DayColumn,
        todayDow: Int?,
        widgetHeightDp: Float,
        modifier: GlanceModifier,
    ) {
        val isToday = day.dow == todayDow
        val colBg = if (isToday) GlanceTheme.colors.primaryContainer else GlanceTheme.colors.surfaceVariant
        val headerColor = if (isToday) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onSurfaceVariant
        Column(
            modifier
                .background(colBg)
                .cornerRadius(10.dp)
                .padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            Text(
                day.label,
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                    color = headerColor,
                ),
                maxLines = 1,
            )
            when {
                day.offDay -> Text(
                    "休",
                    style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onSurfaceVariant),
                    maxLines = 1,
                )
                day.entries.isEmpty() -> Text(
                    "—",
                    style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onSurfaceVariant),
                    maxLines = 1,
                )
                else -> {
                    val capacity = maxEntriesFor(widgetHeightDp)
                    day.entries.take(capacity).forEach { e ->
                        Spacer(GlanceModifier.height(3.dp))
                        Text(
                            e.name,
                            style = TextStyle(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = GlanceTheme.colors.onSurface,
                            ),
                            maxLines = 2,
                        )
                        if (e.location.isNotBlank()) {
                            Text(
                                e.location,
                                style = TextStyle(
                                    fontSize = 9.sp,
                                    color = GlanceTheme.colors.onSurfaceVariant,
                                ),
                                maxLines = 1,
                            )
                        }
                    }
                    if (day.entries.size > capacity) {
                        Spacer(GlanceModifier.height(2.dp))
                        Text(
                            "+${day.entries.size - capacity}",
                            style = TextStyle(
                                fontSize = 9.sp,
                                color = ColorProvider(GlanceTheme.colors.primary.getColor(androidx.glance.LocalContext.current)),
                            ),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }

    private companion object {
        /** 估算该高度下每列能完整显示几门课：外框22 + 头部30 + 列头16，每条约 40 */
        fun maxEntriesFor(heightDp: Float): Int =
            (((heightDp - 68f) / 40f).toInt()).coerceIn(1, 5)
    }

    suspend fun updateAll(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        manager.getGlanceIds(WeekWidget::class.java).forEach { update(context, it) }
    }
}

class WeekWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekWidget()
}
